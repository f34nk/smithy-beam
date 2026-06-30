package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExDoc;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExPattern;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ElixirClientPaginationIr {
  private ElixirClientPaginationIr() {}

  static List<ExFunction> paginatedOperationFunctions(
      ElixirContext ctx,
      ServiceShape service,
      OperationShape op,
      BeamElixirLayout layout,
      boolean wrapWithRetry,
      String retryModule,
      String successReturnType,
      ExDoc docOrNull) {
    SymbolProvider sp = ctx.symbolProvider();
    Symbol opSym = sp.toSymbol(op);
    StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String opName = opSym.getName();
    String inType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
    String specOutput = "{:ok, " + successReturnType + "} | {:error, term()}";

    ExFunction arity2 =
        ExFunction.functionWithDocAndSpec(
            "def",
            opName,
            docOrNull,
            ExSpec.functionSpec(opName, "map(), " + inType, specOutput),
            List.of(
                ExClause.blockClause(
                    List.of(ExVarPattern.var("config"), ExVarPattern.var("input")),
                    ExCallLocal.callLocal(
                        opName, ExVar.var("config"), ExVar.var("input"), ExList.list()))));

    List<ExExpr> pageBody =
        ElixirClientDispatchIr.operationBodyExprs(
            ctx,
            op,
            layout,
            wrapWithRetry,
            retryModule,
            true,
            ElixirClientDispatchOperationIr.DispatchBodyMode.PAGINATED_PAGE);

    ExFunction arity3 =
        ExFunction.functionWithSpec(
            "defp",
            opName,
            ExSpec.functionSpec(opName, "map(), " + inType + ", " + successReturnType, specOutput),
            List.of(
                paginatedArity3Clause(
                    ctx, service, op, wrapWithRetry, retryModule, pageBody, sp, opName)));

    return List.of(arity2, arity3);
  }

  private static ExClause paginatedArity3Clause(
      ElixirContext ctx,
      ServiceShape service,
      OperationShape op,
      boolean wrapWithRetry,
      String retryModule,
      List<ExExpr> pageBody,
      SymbolProvider sp,
      String opName) {
    List<ExPattern> patterns =
        List.of(ExVarPattern.var("config"), ExVarPattern.var("input"), ExVarPattern.var("acc"));
    if (wrapWithRetry) {
      return ExClause.blockClause(
          patterns,
          ExExprBlock.block(
              retryWrappedPageBody(ctx, service, op, retryModule, pageBody, sp, opName)
                  .toArray(ExExpr[]::new)));
    }
    return ExClause.blockClause(patterns, ExExprBlock.block(pageBody.toArray(ExExpr[]::new)));
  }

  private static List<ExExpr> retryWrappedPageBody(
      ElixirContext ctx,
      ServiceShape service,
      OperationShape op,
      String retryModule,
      List<ExExpr> pageBody,
      SymbolProvider sp,
      String opName) {
    PaginationInfo pi = BeamClientPaginationSupport.requirePaginationInfo(ctx.model(), service, op);
    StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
    String inputToken = ElixirClientDispatchOperationIr.fieldName(sp, pi.getInputTokenMember());
    ExExpr outputTokenExpr =
        ElixirClientDispatchOperationIr.buildFieldAccessExpr(
            "output", output, pi.getOutputTokenMemberPath(), ctx.model(), sp);
    boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
    ExExpr itemsExpr =
        hasItems
            ? ElixirClientDispatchOperationIr.buildItemsAccessExpr(
                "output", output, pi.getItemsMemberPath(), ctx.model(), sp)
            : null;

    List<ExExpr> body = new ArrayList<>();
    body.add(
        ExMatch.match(
            ExVarPattern.var("retry_opts"),
            ExCall.call("Map", "get", ExVar.var("config"), ExAtom.atom("retry"), ExList.list())));
    ExCase retryCase =
        ExCase.caseExpr(
            ExCall.call(
                retryModule,
                "with_retry",
                ExAnonymousFn.fn(
                    ExClause.blockClause(
                        List.of(), ExExprBlock.block(pageBody.toArray(ExExpr[]::new)))),
                ExVar.var("retry_opts")),
            ExCaseBranch.branch(
                ExTuplePattern.tuple(ExAtomPattern.atom("ok"), ExVarPattern.var("output")),
                ExExprBlock.block(
                    ElixirClientDispatchOperationIr.buildAccumulationAndRecursion(
                            opName, hasItems, itemsExpr, outputTokenExpr, inputToken)
                        .toArray(ExExpr[]::new))),
            ExCaseBranch.branch(
                ExTuplePattern.tuple(ExAtomPattern.atom("error"), ExVarPattern.var("reason")),
                ExTuple.tuple(ExAtom.atom("error"), ExVar.var("reason"))));
    body.add(retryCase);
    return body;
  }
}
