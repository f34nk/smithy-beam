package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExDoc;
import io.smithy.beam.ir.elixir.ExFunction;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits pagination loops inline in generated client operations for {@code @paginated} operations.
 */
public final class ElixirClientPaginationEmitter {

  private ElixirClientPaginationEmitter() {}

  public static List<ExFunction> paginatedOperationFunctions(
      ElixirContext ctx,
      ServiceShape service,
      OperationShape op,
      BeamElixirLayout layout,
      boolean wrapWithRetry,
      String retryModule,
      String successReturnType,
      ExDoc docOrNull) {
    return ElixirClientPaginationIr.paginatedOperationFunctions(
        ctx, service, op, layout, wrapWithRetry, retryModule, successReturnType, docOrNull);
  }

  public static void emitPaginatedOperation(
      ElixirContext ctx,
      ServiceShape service,
      OperationShape op,
      boolean wrapWithRetry,
      String retryModule,
      Runnable emitPageDispatch,
      ElixirWriter writer) {
    PaginationInfo pi = BeamClientPaginationSupport.requirePaginationInfo(ctx.model(), service, op);
    SymbolProvider sp = ctx.symbolProvider();
    Symbol opSym = sp.toSymbol(op);
    String inputToken = fieldName(sp, pi.getInputTokenMember());
    String outputTokenExpr = tokenAccess("output", pi.getOutputTokenMemberPath(), sp);
    List<MemberShape> itemsPath = pi.getItemsMemberPath();
    boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
    String itemsExpr = hasItems ? itemsAccess("output", itemsPath, sp) : null;

    writer.write("def $L(config, input) do", opSym.getName());
    writer.indent();
    writer.write("$L(config, input, [])", opSym.getName());
    writer.dedent();
    writer.write("end");
    writer.write("");

    writer.write("defp $L(config, input, acc) do", opSym.getName());
    writer.indent();
    if (wrapWithRetry) {
      writer.write("retry_opts = Map.get(config, :retry, [])");
      writer.write("");
      writer.write("case $L.with_retry(fn ->", retryModule);
      writer.indent();
      emitPageDispatch.run();
      writer.dedent();
      writer.write("end, retry_opts) do");
      writer.indent();
      writer.write("{:ok, output} ->");
      writer.indent();
      emitAccumulationAndRecursion(writer, opSym, hasItems, itemsExpr, outputTokenExpr, inputToken);
      writer.dedent();
      writer.write("");
      writer.write("{:error, reason} ->");
      writer.indent();
      writer.write("{:error, reason}");
      writer.dedent();
      writer.dedent();
      writer.write("end");
    } else {
      emitPageDispatch.run();
    }
    writer.dedent();
    writer.write("end");
  }

  static void emitAccumulationAndRecursion(
      ElixirWriter writer,
      Symbol opSym,
      boolean hasItems,
      String itemsExpr,
      String outputTokenExpr,
      String inputToken) {
    if (hasItems) {
      writer.write("new_acc = acc ++ $L", itemsExpr);
    } else {
      writer.write("new_acc = [output | acc]");
    }
    writer.write("");
    writer.write("case $L do", outputTokenExpr);
    writer.indent();
    if (hasItems) {
      writer.write("nil ->");
      writer.indent();
      writer.write("{:ok, new_acc}");
      writer.dedent();
    } else {
      writer.write("nil ->");
      writer.indent();
      writer.write("{:ok, Enum.reverse(new_acc)}");
      writer.dedent();
    }
    writer.write("");
    writer.write("next_token ->");
    writer.indent();
    writer.write(
        "$L(config, Map.put(input, :$L, next_token), new_acc)", opSym.getName(), inputToken);
    writer.dedent();
    writer.dedent();
    writer.write("end");
  }

  static String fieldName(SymbolProvider sp, MemberShape member) {
    return sp.toSymbol(member).getProperty("fieldName", String.class).orElseThrow();
  }

  static String tokenAccess(String rootVar, List<MemberShape> path, SymbolProvider sp) {
    if (path.size() == 1) {
      return "Map.get(" + rootVar + ", :" + fieldName(sp, path.get(0)) + ")";
    }
    String keys =
        path.stream().map(member -> ":" + fieldName(sp, member)).collect(Collectors.joining(", "));
    return "get_in(" + rootVar + ", [" + keys + "])";
  }

  static String itemsAccess(String rootVar, List<MemberShape> path, SymbolProvider sp) {
    if (path.size() == 1) {
      return "Map.get(" + rootVar + ", :" + fieldName(sp, path.get(0)) + ", [])";
    }
    String keys =
        path.stream().map(member -> ":" + fieldName(sp, member)).collect(Collectors.joining(", "));
    return "get_in(" + rootVar + ", [" + keys + "]) || []";
  }
}
