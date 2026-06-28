package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamWaiterIndex;
import io.smithy.beam.core.BeamWaiterPaths;
import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExConsPattern;
import io.smithy.beam.ir.elixir.ExDoc;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExIf;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExIntegerPattern;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExListPattern;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMapFieldPattern;
import io.smithy.beam.ir.elixir.ExMapPattern;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExNil;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStruct;
import io.smithy.beam.ir.elixir.ExStructPattern;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirWaiterIr {
  private ElixirWaiterIr() {}

  static ExModule waitersModule(
      ElixirContext ctx,
      ServiceShape service,
      BeamWaiterIndex index,
      SymbolProvider sp,
      Model model) {
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String moduleName = ElixirSymbolProvider.toModuleName(layout.waitersModuleName());
    String clientMod = ElixirSymbolProvider.toModuleName(layout.clientModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<ExFunction> functions = new ArrayList<>();
    for (BeamWaiterIndex.WaiterBinding binding : index.bindings()) {
      functions.add(waiterFunction(index, binding, clientMod, typesMod, sp));
    }
    functions.addAll(waitUntilHelperFunctions());
    return ExModule.module(
        moduleName,
        List.of(
            ExModuledoc.moduledoc(
                "Generated waiters for " + service.getId() + " (generated).")),
        List.of(),
        functions);
  }

  static ExFunction waiterFunction(
      BeamWaiterIndex index,
      BeamWaiterIndex.WaiterBinding binding,
      String clientMod,
      String typesMod,
      SymbolProvider sp) {
    OperationShape operation = binding.operation();
    Symbol opSym = sp.toSymbol(operation);
    String fn = waitFunctionName(binding.name());
    List<ExExpr> acceptorMaps =
        index.acceptors(binding).stream().map(a -> acceptorMap(a, typesMod, sp)).toList();

    return ExFunction.functionWithDocAndSpec(
        "def",
        fn,
        ExDoc.doc("Waits using the " + binding.name() + " waiter on " + operation.getId() + "."),
        ExSpec.functionSpec(fn, "term(), map(), keyword()", "{:ok, term()} | {:error, term()}"),
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(
                    ExVarPattern.var("client"),
                    ExVarPattern.var("input"),
                    ExVarPattern.var("opts")),
                ExMatch.match(
                    ExVarPattern.var("acceptors"),
                    ExList.list(acceptorMaps.toArray(ExExpr[]::new))),
                ExMatch.match(
                    ExVarPattern.var("wait_opts"),
                    ExCall.call(
                        "Keyword",
                        "merge",
                        ExList.list(
                            ExTuple.tuple(
                                ExAtom.atom("min_delay_ms"),
                                ExInteger.integer(binding.minDelaySeconds() * 1000L)),
                            ExTuple.tuple(
                                ExAtom.atom("max_delay_ms"),
                                ExInteger.integer(binding.maxDelaySeconds() * 1000L))),
                        ExVar.var("opts"))),
                ExCallLocal.callLocal(
                    "wait_until",
                    ExAnonymousFn.compactFn(
                        ExClause.inlineClause(
                            List.of(),
                            ExCall.call(
                                clientMod,
                                opSym.getName(),
                                ExVar.var("client"),
                                ExVar.var("input")))),
                    ExVar.var("acceptors"),
                    ExVar.var("wait_opts")))));
  }

  static ExExpr acceptorMap(
      BeamWaiterIndex.AcceptorInfo acceptor, String typesMod, SymbolProvider sp) {
    List<ExMapEntry> entries = new ArrayList<>();
    entries.add(ExMapEntry.entry(ExAtom.atom("state"), ExAtom.atom(acceptor.state())));
    if (acceptor.successExpected().isPresent()) {
      boolean expected = acceptor.successExpected().get();
      entries.add(ExMapEntry.entry(ExAtom.atom("matcher"), ExAtom.atom("success")));
      entries.add(
          ExMapEntry.entry(
              ExAtom.atom("expected"),
              ExCapturedBlock.capturedBlock(expected ? "true" : "false")));
    } else if (acceptor.errorTypeName().isPresent()) {
      String errorType = acceptor.errorTypeName().get();
      entries.add(ExMapEntry.entry(ExAtom.atom("matcher"), ExAtom.atom("errorType")));
      if (acceptor.resolvedError().isPresent()) {
        String exception = sp.toSymbol(acceptor.resolvedError().get()).getName();
        entries.add(
            ExMapEntry.entry(
                ExAtom.atom("expected"),
                ExStruct.struct(typesMod + "." + exception, List.of())));
      } else {
        entries.add(
            ExMapEntry.entry(ExAtom.atom("expected"), ExString.string(errorType)));
      }
    } else if (acceptor.pathMatcher().isPresent()) {
      BeamWaiterIndex.PathMatcherInfo pathMatcher = acceptor.pathMatcher().get();
      entries.add(ExMapEntry.entry(ExAtom.atom("matcher"), ExAtom.atom(acceptor.matcherKind())));
      entries.add(
          ExMapEntry.entry(
              ExAtom.atom("path"),
              ExCapturedBlock.capturedBlock(BeamWaiterPaths.emitElixirPath(pathMatcher.path()))));
      entries.add(
          ExMapEntry.entry(ExAtom.atom("comparator"), ExAtom.atom(pathMatcher.comparator())));
      entries.add(
          ExMapEntry.entry(ExAtom.atom("expected"), ExString.string(pathMatcher.expected())));
    } else {
      entries.add(ExMapEntry.entry(ExAtom.atom("matcher"), ExAtom.atom(acceptor.matcherKind())));
    }
    return ExMap.map(entries.toArray(ExMapEntry[]::new));
  }

  static List<ExFunction> waitUntilHelperFunctions() {
    return List.of(
        waitUntilArity3(),
        waitUntilArity5(),
        classify(),
        matchesAcceptor(),
        errorTypesMatch(),
        pathStringEquals(),
        pathValueBinary(),
        pathValueEmptyList(),
        pathValueMap(),
        pathValueStruct(),
        pathValueFallback(),
        stringEqualsAtomBinary(),
        stringEqualsBinaryBinary(),
        stringEqualsFallback());
  }

  private static ExFunction waitUntilArity3() {
    return ExFunction.defpFunction(
        "wait_until",
        List.of(
            ExClause.blockClauseSingleLineHead(
                List.of(
                    ExVarPattern.var("step"),
                    ExVarPattern.var("acceptors"),
                    ExVarPattern.var("opts")),
                ExMatch.match(
                    ExVarPattern.var("max_attempts"),
                    ExCall.call(
                        "Keyword",
                        "get",
                        ExVar.var("opts"),
                        ExAtom.atom("max_attempts"),
                        ExInteger.integer(25))),
                ExMatch.match(
                    ExVarPattern.var("min_delay"),
                    ExCall.call(
                        "Keyword",
                        "get",
                        ExVar.var("opts"),
                        ExAtom.atom("min_delay_ms"),
                        ExInteger.integer(2000))),
                ExMatch.match(
                    ExVarPattern.var("max_delay"),
                    ExCall.call(
                        "Keyword",
                        "get",
                        ExVar.var("opts"),
                        ExAtom.atom("max_delay_ms"),
                        ExInteger.integer(120_000))),
                ExCallLocal.callLocal(
                    "wait_until",
                    ExVar.var("step"),
                    ExVar.var("acceptors"),
                    ExVar.var("max_attempts"),
                    ExVar.var("min_delay"),
                    ExVar.var("max_delay")))));
  }

  private static ExFunction waitUntilArity5() {
    return ExFunction.defpFunction(
        "wait_until",
        List.of(
            ExClause.inlineClause(
                List.of(
                    ExVarPattern.var("_step"),
                    ExVarPattern.var("_acceptors"),
                    ExIntegerPattern.integer(0),
                    ExVarPattern.var("_delay"),
                    ExVarPattern.var("_max_delay")),
                ExTuple.tuple(ExAtom.atom("error"), ExAtom.atom("max_attempts_exceeded"))),
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("step"),
                    ExVarPattern.var("acceptors"),
                    ExVarPattern.var("attempts"),
                    ExVarPattern.var("delay"),
                    ExVarPattern.var("max_delay")),
                ExCapturedBlock.capturedBlock(
                    """
                    result = step.()
                    case classify(acceptors, result) do
                      :success -> {:ok, result}

                      :failure -> {:error, result}

                      :retry when attempts <= 1 -> {:error, :max_attempts_exceeded}

                      :retry ->
                        Process.sleep(delay)
                        next_delay = min(delay * 2, max_delay)
                        wait_until(step, acceptors, attempts - 1, next_delay, max_delay)
                    end"""))));
  }

  private static ExFunction classify() {
    return ExFunction.defpFunction(
        "classify",
        List.of(
            ExClause.inlineClause(
                List.of(ExListPattern.list(), ExVarPattern.var("_result")),
                ExAtom.atom("retry")),
            ExClause.blockClauseSingleLineHead(
                List.of(
                    ExConsPattern.consPattern(
                        ExVarPattern.var("acceptor"), ExVarPattern.var("rest")),
                    ExVarPattern.var("result")),
                ExIf.ifBlock(
                    ExCallLocal.callLocal(
                        "matches_acceptor?", ExVar.var("acceptor"), ExVar.var("result")),
                    ExCapturedBlock.capturedBlock("acceptor.state"),
                    ExCallLocal.callLocal("classify", ExVar.var("rest"), ExVar.var("result"))))));
  }

  private static ExFunction matchesAcceptor() {
    return ExFunction.defpFunction(
        "matches_acceptor?",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("_acceptor"), ExVarPattern.var("_result")),
                ExCapturedBlock.capturedBlock(
                    """
                    case {acceptor, result} do
                      {%{matcher: :success, expected: true}, {:ok, _}} -> true
                      {%{matcher: :success, expected: false}, {:error, _}} -> true
                      {%{matcher: :errorType, expected: expected}, {:error, got}} ->
                        error_types_match?(expected, got)
                      {%{matcher: :output, path: path, comparator: :stringEquals, expected: expected}, {:ok, output}} ->
                        path_string_equals?(path, expected, output)
                      {%{matcher: :inputOutput, path: path, comparator: :stringEquals, expected: expected}, {:ok, output}} ->
                        path_string_equals?(path, expected, output)
                      _ -> false
                    end"""))));
  }

  private static ExFunction errorTypesMatch() {
    return ExFunction.defpFunction(
        "error_types_match?",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("expected"), ExVarPattern.var("_got")),
                List.of(ExGuard.guard("is_binary", ExVar.var("expected"))),
                ExCapturedBlock.capturedBlock("true")),
            ExClause.inlineClause(
                List.of(
                    ExMapPattern.map(
                        ExMapFieldPattern.field(
                            ExAtom.atom("__struct__"), ExVarPattern.var("struct"))),
                    ExMapPattern.map(
                        ExMapFieldPattern.field(
                            ExAtom.atom("__struct__"), ExVarPattern.var("struct")))),
                ExCapturedBlock.capturedBlock("true")),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("expected"), ExVarPattern.var("got")),
                ExOp.op("==", ExVar.var("expected"), ExVar.var("got")))));
  }

  private static ExFunction pathStringEquals() {
    return ExFunction.defpFunction(
        "path_string_equals?",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("path"),
                    ExVarPattern.var("expected"),
                    ExVarPattern.var("output")),
                ExCapturedBlock.capturedBlock(
                    """
                    case path_value(path, output) do
                      nil -> false
                      value -> string_equals?(value, expected)
                    end"""))));
  }

  private static ExFunction pathValueBinary() {
    return ExFunction.defpFunction(
        "path_value",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("path"), ExVarPattern.var("_value")),
                List.of(ExGuard.guard("is_binary", ExVar.var("path"))),
                ExNil.nil())));
  }

  private static ExFunction pathValueEmptyList() {
    return ExFunction.defpFunction(
        "path_value",
        List.of(
            ExClause.inlineClause(
                List.of(ExListPattern.list(), ExVarPattern.var("value")), ExVar.var("value"))));
  }

  private static ExFunction pathValueMap() {
    return ExFunction.defpFunction(
        "path_value",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExConsPattern.consPattern(
                        ExVarPattern.var("key"), ExVarPattern.var("rest")),
                    ExVarPattern.var("value")),
                List.of(ExGuard.guard("is_map", ExVar.var("value"))),
                ExCapturedBlock.capturedBlock(
                    """
                    case Map.get(value, key) do
                      nil -> nil
                      next -> path_value(rest, next)
                    end"""))));
  }

  private static ExFunction pathValueStruct() {
    return ExFunction.defpFunction(
        "path_value",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExConsPattern.consPattern(
                        ExVarPattern.var("key"), ExVarPattern.var("rest")),
                    ExVarPattern.var("value")),
                List.of(ExGuard.guard("is_struct", ExVar.var("value"))),
                ExCapturedBlock.capturedBlock(
                    """
                    case Map.get(Map.from_struct(value), key) do
                      nil -> nil
                      next -> path_value(rest, next)
                    end"""))));
  }

  private static ExFunction pathValueFallback() {
    return ExFunction.defpFunction(
        "path_value",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("_path"), ExVarPattern.var("_value")),
                ExNil.nil())));
  }

  private static ExFunction stringEqualsAtomBinary() {
    return ExFunction.defpFunction(
        "string_equals?",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("left"), ExVarPattern.var("right")),
                List.of(
                    ExGuard.guard("is_atom", ExVar.var("left")),
                    ExGuard.guard("is_binary", ExVar.var("right"))),
                ExOp.op(
                    "==",
                    ExCall.call("String", "upcase", ExCall.call("Atom", "to_string", ExVar.var("left"))),
                    ExCall.call("String", "upcase", ExVar.var("right"))))));
  }

  private static ExFunction stringEqualsBinaryBinary() {
    return ExFunction.defpFunction(
        "string_equals?",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("left"), ExVarPattern.var("right")),
                List.of(
                    ExGuard.guard("is_binary", ExVar.var("left")),
                    ExGuard.guard("is_binary", ExVar.var("right"))),
                ExOp.op(
                    "==",
                    ExCall.call("String", "upcase", ExVar.var("left")),
                    ExCall.call("String", "upcase", ExVar.var("right"))))));
  }

  private static ExFunction stringEqualsFallback() {
    return ExFunction.defpFunction(
        "string_equals?",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("left"), ExVarPattern.var("right")),
                ExOp.op("==", ExVar.var("left"), ExVar.var("right")))));
  }

  private static String waitFunctionName(String waiterName) {
    return "wait_" + BeamNameUtils.toSnakeCase(waiterName);
  }
}
