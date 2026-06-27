package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamWaiterIndex;
import io.smithy.beam.core.BeamWaiterPaths;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

/** Generates a {@code <Service>Waiters} helper for {@code @waitable} operations. */
public final class ElixirWaiterEmitter {

  private ElixirWaiterEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    BeamWaiterIndex index = BeamWaiterIndex.of(ctx.model(), service);
    if (index.isEmpty()) {
      return;
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String waitersMod = ElixirSymbolProvider.toModuleName(layout.waitersModuleName());
    String clientMod = ElixirSymbolProvider.toModuleName(layout.clientModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    SymbolProvider sp = ctx.symbolProvider();

    ctx.writerDelegator()
        .useFileWriter(
            layout.waitersModuleFile(),
            writer -> {
              writer.write("defmodule $L do", waitersMod);
              writer.indent();
              writer.write("@moduledoc \"Generated waiters for $L (generated).\"", service.getId());
              writer.write("");

              for (BeamWaiterIndex.WaiterBinding binding : index.bindings()) {
                emitWaiterFunction(writer, index, binding, clientMod, typesMod, sp);
              }

              emitWaitUntilHelper(writer);
              writer.dedent();
              writer.write("end");
            });
  }

  private static void emitWaiterFunction(
      ElixirWriter writer,
      BeamWaiterIndex index,
      BeamWaiterIndex.WaiterBinding binding,
      String clientMod,
      String typesMod,
      SymbolProvider sp) {
    OperationShape operation = binding.operation();
    Symbol opSym = sp.toSymbol(operation);
    String fn = waitFunctionName(binding.name());
    List<BeamWaiterIndex.AcceptorInfo> acceptors = index.acceptors(binding);

    writer.write("@doc \"Waits using the $L waiter on $L.\"", binding.name(), operation.getId());
    writer.write("def $L(client, input, opts \\\\ []) do", fn);
    writer.indent();
    writer.write("acceptors = [");
    writer.indent();
    for (int i = 0; i < acceptors.size(); i++) {
      emitAcceptorMap(writer, acceptors.get(i), typesMod, sp, i < acceptors.size() - 1);
    }
    writer.dedent();
    writer.write("]");
    writer.write("");
    writer.write("wait_opts =");
    writer.indent();
    writer.write("Keyword.merge(");
    writer.indent();
    writer.write("[");
    writer.indent();
    writer.write("min_delay_ms: $L,", binding.minDelaySeconds() * 1000);
    writer.write("max_delay_ms: $L", binding.maxDelaySeconds() * 1000);
    writer.dedent();
    writer.write("], opts)");
    writer.dedent();
    writer.dedent();
    writer.write("");
    writer.write("wait_until(");
    writer.indent();
    writer.write("fn -> $L.$L(client, input) end,", clientMod, opSym.getName());
    writer.write("acceptors,");
    writer.write("wait_opts");
    writer.dedent();
    writer.write(")");
    writer.dedent();
    writer.write("end");
    writer.write("");
  }

  private static void emitAcceptorMap(
      ElixirWriter writer,
      BeamWaiterIndex.AcceptorInfo acceptor,
      String typesMod,
      SymbolProvider sp,
      boolean more) {
    writer.write("%{");
    writer.indent();
    writer.write("state: :$L,", acceptor.state());
    acceptor
        .successExpected()
        .ifPresentOrElse(
            expected -> writer.write("matcher: :success, expected: $L", expected),
            () ->
                acceptor
                    .errorTypeName()
                    .ifPresentOrElse(
                        errorType -> {
                          if (acceptor.resolvedError().isPresent()) {
                            String exception =
                                sp.toSymbol(acceptor.resolvedError().get()).getName();
                            writer.write(
                                "matcher: :errorType, expected: %$L.$L{}", typesMod, exception);
                          } else {
                            writer.write(
                                "matcher: :errorType, expected: \"$L\"", escapeString(errorType));
                          }
                        },
                        () ->
                            acceptor
                                .pathMatcher()
                                .ifPresentOrElse(
                                    pathMatcher ->
                                        emitPathMatcher(
                                            writer, acceptor.matcherKind(), pathMatcher),
                                    () -> writer.write("matcher: :$L", acceptor.matcherKind()))));
    writer.dedent();
    writer.write(more ? "}," : "}");
  }

  private static void emitPathMatcher(
      ElixirWriter writer, String memberName, BeamWaiterIndex.PathMatcherInfo pathMatcher) {
    writer.write("matcher: :$L,", memberName);
    writer.write("path: $L,", BeamWaiterPaths.emitElixirPath(pathMatcher.path()));
    writer.write("comparator: :$L,", pathMatcher.comparator());
    writer.write("expected: \"$L\"", escapeString(pathMatcher.expected()));
  }

  private static void emitWaitUntilHelper(ElixirWriter writer) {
    writer.write("defp wait_until(step, acceptors, opts) do");
    writer.indent();
    writer.write("max_attempts = Keyword.get(opts, :max_attempts, 25)");
    writer.write("min_delay = Keyword.get(opts, :min_delay_ms, 2_000)");
    writer.write("max_delay = Keyword.get(opts, :max_delay_ms, 120_000)");
    writer.write("wait_until(step, acceptors, max_attempts, min_delay, max_delay)");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write(
        "defp wait_until(_step, _acceptors, 0, _delay, _max_delay), do: {:error, :max_attempts_exceeded}");
    writer.write("");
    writer.write("defp wait_until(step, acceptors, attempts, delay, max_delay) do");
    writer.indent();
    writer.write("result = step.()");
    writer.write("case classify(acceptors, result) do");
    writer.indent();
    writer.write(":success -> {:ok, result}");
    writer.write("");
    writer.write(":failure -> {:error, result}");
    writer.write("");
    writer.write(":retry when attempts <= 1 -> {:error, :max_attempts_exceeded}");
    writer.write("");
    writer.write(":retry ->");
    writer.indent();
    writer.write("Process.sleep(delay)");
    writer.write("next_delay = min(delay * 2, max_delay)");
    writer.write("wait_until(step, acceptors, attempts - 1, next_delay, max_delay)");
    writer.dedent();
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp classify([], _result), do: :retry");
    writer.write("");
    writer.write("defp classify([acceptor | rest], result) do");
    writer.indent();
    writer.write("if matches_acceptor?(acceptor, result) do");
    writer.indent();
    writer.write("acceptor.state");
    writer.dedent();
    writer.write("else");
    writer.indent();
    writer.write("classify(rest, result)");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write(
        "defp matches_acceptor?(%{matcher: :success, expected: true}, {:ok, _}), do: true");
    writer.write(
        "defp matches_acceptor?(%{matcher: :success, expected: false}, {:error, _}), do: true");
    writer.write(
        "defp matches_acceptor?(%{matcher: :errorType, expected: expected}, {:error, got}), do: error_types_match?(expected, got)");
    writer.write("");
    writer.write("defp error_types_match?(expected, _got) when is_binary(expected), do: true");
    writer.write("defp error_types_match?(%{__struct__: struct}, %{__struct__: struct}), do: true");
    writer.write("defp error_types_match?(expected, got), do: expected == got");
    writer.write("");
    ElixirFormat.breakFunctionHead(
        writer,
        "defp",
        "matches_acceptor?",
        List.of(
            "%{matcher: :output, path: path, comparator: :stringEquals, expected: expected}",
            "{:ok, output}"));
    writer.indent();
    writer.write("path_string_equals?(path, expected, output)");
    writer.dedent();
    writer.write("end");
    writer.write("");
    ElixirFormat.breakFunctionHead(
        writer,
        "defp",
        "matches_acceptor?",
        List.of(
            "%{matcher: :inputOutput, path: path, comparator: :stringEquals, expected: expected}",
            "{:ok, output}"));
    writer.indent();
    writer.write("path_string_equals?(path, expected, output)");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp matches_acceptor?(_, _), do: false");
    writer.write("");
    writer.write("defp path_string_equals?(path, expected, output) do");
    writer.indent();
    writer.write("case path_value(path, output) do");
    writer.indent();
    writer.write("nil -> false");
    writer.write("value -> string_equals?(value, expected)");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp path_value(path, _value) when is_binary(path), do: nil");
    writer.write("defp path_value([], value), do: value");
    writer.write("defp path_value([key | rest], value) when is_map(value) do");
    writer.indent();
    writer.write("case Map.get(value, key) do");
    writer.indent();
    writer.write("nil -> nil");
    writer.write("next -> path_value(rest, next)");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("defp path_value([key | rest], value) when is_struct(value) do");
    writer.indent();
    writer.write("case Map.get(Map.from_struct(value), key) do");
    writer.indent();
    writer.write("nil -> nil");
    writer.write("next -> path_value(rest, next)");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("defp path_value(_path, _value), do: nil");
    writer.write("");
    writer.write("defp string_equals?(left, right) when is_atom(left) and is_binary(right) do");
    writer.indent();
    writer.write("String.upcase(Atom.to_string(left)) == String.upcase(right)");
    writer.dedent();
    writer.write("end");
    writer.write("defp string_equals?(left, right) when is_binary(left) and is_binary(right) do");
    writer.indent();
    writer.write("String.upcase(left) == String.upcase(right)");
    writer.dedent();
    writer.write("end");
    writer.write("defp string_equals?(left, right), do: left == right");
  }

  private static String waitFunctionName(String waiterName) {
    return "wait_" + BeamNameUtils.toSnakeCase(waiterName);
  }

  private static String escapeString(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
