package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AnonFun;
import io.beam.dsl.elixir.AnonFunClause;
import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.AtomPattern;
import io.beam.dsl.elixir.BlockExpr;
import io.beam.dsl.elixir.CaseExpr;
import io.beam.dsl.elixir.Clause;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionDoc;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.ListExpr;
import io.beam.dsl.elixir.LocalCallExpr;
import io.beam.dsl.elixir.Spec;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamElixirLayout;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ElixirClientPaginationDsl {
  private ElixirClientPaginationDsl() {}

  static List<Function> paginatedOperationFunctions(
      ElixirContext ctx,
      ServiceShape service,
      OperationShape op,
      BeamElixirLayout layout,
      boolean wrapWithRetry,
      String clientModule,
      String successReturnType,
      FunctionDoc docOrNull) {
    SymbolProvider sp = ctx.symbolProvider();
    Symbol opSym = sp.toSymbol(op);
    StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String opName = opSym.getName();
    String inType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
    String specOutput = "{:ok, " + successReturnType + "} | {:error, term()}";

    Function arity2 =
        Function.of(
            opName,
            false,
            List.of(
                FunctionHead.of(
                    List.of(VariablePattern.of("config"), VariablePattern.of("input")))),
            LocalCallExpr.of(
                opName,
                List.of(Variable.of("config"), Variable.of("input"), ListExpr.of(List.of()))),
            Spec.of(opName + "(client_config(), " + inType + ") :: " + specOutput),
            docOrNull,
            true);

    List<Expression> pageBody =
        ElixirClientDispatchDsl.operationBodyExprs(
            ctx,
            op,
            layout,
            wrapWithRetry,
            clientModule,
            true,
            ElixirClientDispatchOperationDsl.DispatchBodyMode.PAGINATED_PAGE);

    Function arity3 =
        Function.of(
            opName,
            true,
            List.of(
                FunctionHead.of(
                    List.of(
                        VariablePattern.of("config"),
                        VariablePattern.of("input"),
                        VariablePattern.of("acc")))),
            paginatedArity3Body(
                ctx, service, op, wrapWithRetry, clientModule, pageBody, sp, opName),
            Spec.of(
                opName
                    + "(client_config(), "
                    + inType
                    + ", "
                    + successReturnType
                    + ") :: "
                    + specOutput),
            null,
            false);

    return List.of(arity2, arity3);
  }

  private static Expression paginatedArity3Body(
      ElixirContext ctx,
      ServiceShape service,
      OperationShape op,
      boolean wrapWithRetry,
      String clientModule,
      List<Expression> pageBody,
      SymbolProvider sp,
      String opName) {
    if (wrapWithRetry) {
      return blockExpr(retryWrappedPageBody(ctx, service, op, clientModule, pageBody, sp, opName));
    }
    return blockExpr(pageBody);
  }

  private static Expression blockExpr(List<Expression> exprs) {
    return exprs.size() == 1 ? exprs.get(0) : BlockExpr.of(exprs);
  }

  private static List<Expression> retryWrappedPageBody(
      ElixirContext ctx,
      ServiceShape service,
      OperationShape op,
      String clientModule,
      List<Expression> pageBody,
      SymbolProvider sp,
      String opName) {
    PaginationInfo pi = BeamClientPaginationSupport.requirePaginationInfo(ctx.model(), service, op);
    StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
    String inputToken = ElixirClientDispatchOperationDsl.fieldName(sp, pi.getInputTokenMember());
    Expression outputTokenExpr =
        ElixirClientDispatchOperationDsl.buildFieldAccessExpr(
            "output", output, pi.getOutputTokenMemberPath(), ctx.model(), sp);
    boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
    Expression itemsExpr =
        hasItems
            ? ElixirClientDispatchOperationDsl.buildItemsAccessExpr(
                "output", output, pi.getItemsMemberPath(), ctx.model(), sp)
            : null;

    Expression pageFun =
        AnonFun.of(
            List.of(
                AnonFunClause.of(
                    List.of(), pageBody.size() == 1 ? pageBody.get(0) : BlockExpr.of(pageBody))));
    Expression retryCase =
        CaseExpr.of(
            ElixirClientDispatchOperationDsl.withRetryCall(clientModule, pageFun),
            List.of(
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("output"))),
                    BlockExpr.of(
                        ElixirClientDispatchOperationDsl.buildAccumulationAndRecursion(
                            opName, hasItems, itemsExpr, outputTokenExpr, inputToken))),
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("reason"))),
                    TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("reason"))))));
    List<Expression> body = new ArrayList<>();
    body.add(ElixirClientDispatchOperationDsl.retryOptsBinding());
    body.add(retryCase);
    return body;
  }
}
