package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlFunctionDoc;
import io.smithy.beam.ir.erlang.ErlFunctionSpec;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.OperationShape;

/** Temporary compat until Step 12 migrates client dispatch and pagination to beam-ir. */
final class ErlangClientOperationCodegenIrCompat {
  private ErlangClientOperationCodegenIrCompat() {}

  static String successReturnType(
      boolean paginated,
      PaginationInfo paginationInfo,
      ErlangContext ctx,
      SymbolProvider sp,
      Symbol outSym) {
    if (paginated && BeamClientPaginationSupport.hasItemsMember(paginationInfo)) {
      return "["
          + BeamClientPaginationSupport.itemsElementSymbol(ctx.model(), sp, paginationInfo)
              .orElseThrow()
              .getName()
          + "]";
    }
    if (paginated) {
      return "[" + outSym.getName() + "]";
    }
    return outSym.getName();
  }

  static ErlFunctionDoc operationDoc(OperationShape op, ErlangContext ctx) {
    StringBuilder text = new StringBuilder();
    BeamDocumentation.forShape(op).ifPresent(doc -> text.append(doc).append('\n'));
    if (ctx.protocolCodegen() != null) {
      Map<String, HttpBinding> bindings = ctx.httpBindings().requestBindings(op);
      if (!bindings.isEmpty()) {
        text.append("HTTP request bindings for ").append(op.getId()).append(':').append('\n');
        for (Map.Entry<String, HttpBinding> entry : bindings.entrySet()) {
          HttpBinding binding = entry.getValue();
          text.append("  ")
              .append(entry.getKey())
              .append(" @ ")
              .append(binding.getLocation())
              .append('\n');
        }
      }
    }
    if (text.isEmpty()) {
      return null;
    }
    return ErlFunctionDoc.functionDoc(text.toString().strip());
  }

  static ErlFunction singlePageOperationFunction(
      ErlangContext ctx,
      OperationShape op,
      BeamErlangLayout layout,
      Symbol opSym,
      Symbol inSym,
      String successReturnType,
      boolean hasProtocol,
      boolean wrapWithRetry,
      String retryModule,
      ErlFunctionDoc doc) {
    String specOutput = "{'ok', " + successReturnType + "} | {'error', term()}";
    if (!hasProtocol) {
      return new ErlFunction(
          opSym.getName(),
          2,
          doc,
          ErlFunctionSpec.functionSpec(
              opSym.getName(), "client_config(), " + inSym.getName(), specOutput),
          List.of(
              ErlClause.clause(
                  List.of(ErlVarPattern.varPattern("_Config"), ErlVarPattern.varPattern("_Input")),
                  ErlTuple.tuple(ErlAtom.atom("error"), ErlAtom.atom("not_implemented")))));
    }

    List<ErlExpr> body =
        ErlangClientDispatchIr.operationBodyExprs(
            ctx,
            op,
            layout,
            wrapWithRetry,
            retryModule,
            false,
            ErlangClientDispatchOperationIr.DispatchBodyMode.SINGLE_PAGE);
    return new ErlFunction(
        opSym.getName(),
        2,
        doc,
        ErlFunctionSpec.functionSpec(
            opSym.getName(), "client_config(), " + inSym.getName(), specOutput),
        List.of(
            ErlClause.blockClause(
                List.of(ErlVarPattern.varPattern("Config"), ErlVarPattern.varPattern("Input")),
                ErlExprBlock.block(body.toArray(ErlExpr[]::new)))));
  }

  static List<ErlFunction> paginatedOperationFunctions(
      ErlangContext ctx,
      software.amazon.smithy.model.shapes.ServiceShape service,
      OperationShape op,
      BeamErlangLayout layout,
      boolean wrapWithRetry,
      String retryModule,
      String successReturnType,
      ErlFunctionDoc doc) {
    return ErlangClientPaginationIr.paginatedOperationFunctions(
        ctx, service, op, layout, wrapWithRetry, retryModule, successReturnType, doc);
  }
}
