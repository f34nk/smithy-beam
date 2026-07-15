package io.smithy.beam.elixir;

import io.beam.ir.elixir.AnonFun;
import io.beam.ir.elixir.AnonFunClause;
import io.beam.ir.elixir.AtomExpr;
import io.beam.ir.elixir.AtomPattern;
import io.beam.ir.elixir.BlockExpr;
import io.beam.ir.elixir.CaseExpr;
import io.beam.ir.elixir.Clause;
import io.beam.ir.elixir.Expression;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.FunctionDoc;
import io.beam.ir.elixir.FunctionHead;
import io.beam.ir.elixir.ListExpr;
import io.beam.ir.elixir.LocalCallExpr;
import io.beam.ir.elixir.Spec;
import io.beam.ir.elixir.TupleExpr;
import io.beam.ir.elixir.TuplePattern;
import io.beam.ir.elixir.Variable;
import io.beam.ir.elixir.VariablePattern;
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

final class ElixirClientPaginationIr {
  private ElixirClientPaginationIr() {}

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
        new Function(
            opName,
            false,
            List.of(
                FunctionHead.of(
                    List.of(VariablePattern.of("config"), VariablePattern.of("input")))),
            LocalCallExpr.of(
                opName,
                List.of(
                    Variable.of("config"),
                    Variable.of("input"),
                    ListExpr.of(List.of()))),
            Spec.of(opName + "(map(), " + inType + ") :: " + specOutput),
            docOrNull,
            true);

    List<Expression> pageBody =
        ElixirClientDispatchIr.operationBodyExprs(
            ctx,
            op,
            layout,
            wrapWithRetry,
            clientModule,
            true,
            ElixirClientDispatchOperationIr.DispatchBodyMode.PAGINATED_PAGE);

    Function arity3 =
        new Function(
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
                opName + "(map(), " + inType + ", " + successReturnType + ") :: " + specOutput),
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
      return blockExpr(
          retryWrappedPageBody(ctx, service, op, clientModule, pageBody, sp, opName));
    }
    return blockExpr(pageBody);
  }

  private static Expression blockExpr(List<Expression> exprs) {
    return exprs.size() == 1 ? exprs.get(0) : new BlockExpr(exprs);
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
    String inputToken = ElixirClientDispatchOperationIr.fieldName(sp, pi.getInputTokenMember());
    Expression outputTokenExpr =
        ElixirClientDispatchOperationIr.buildFieldAccessExpr(
            "output", output, pi.getOutputTokenMemberPath(), ctx.model(), sp);
    boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
    Expression itemsExpr =
        hasItems
            ? ElixirClientDispatchOperationIr.buildItemsAccessExpr(
                "output", output, pi.getItemsMemberPath(), ctx.model(), sp)
            : null;

    Expression pageFun =
        new AnonFun(
            List.of(
                AnonFunClause.of(
                    List.of(),
                    pageBody.size() == 1 ? pageBody.get(0) : new BlockExpr(pageBody))));
    Expression retryCase =
        new CaseExpr(
            ElixirClientDispatchOperationIr.withRetryCall(clientModule, pageFun),
            List.of(
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("output"))),
                    new BlockExpr(
                        ElixirClientDispatchOperationIr.buildAccumulationAndRecursion(
                            opName, hasItems, itemsExpr, outputTokenExpr, inputToken))),
                Clause.of(
                    TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("reason"))),
                    TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("reason"))))));
    List<Expression> body = new ArrayList<>();
    body.add(ElixirClientDispatchOperationIr.retryOptsBinding());
    body.add(retryCase);
    return body;
  }
}
