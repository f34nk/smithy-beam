package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExMapUpdate;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExStructAccess;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ElixirClientDispatchOperationIr {
  private ElixirClientDispatchOperationIr() {}

  enum DispatchBodyMode {
    SINGLE_PAGE,
    PAGINATED_PAGE
  }

  private record DispatchContext(
      ElixirContext ctx,
      OperationShape op,
      BeamElixirLayout layout,
      boolean wrapWithRetry,
      String retryModule,
      boolean paginated,
      DispatchBodyMode mode,
      Symbol opSym,
      String opName,
      String codecModule,
      String runtimeHttpModule,
      String sigv4Module,
      boolean sigv4,
      boolean encodeWithConfig) {}

  static List<ExExpr> buildDispatchBody(
      ElixirContext ctx,
      OperationShape op,
      BeamElixirLayout layout,
      boolean wrapWithRetry,
      String retryModule,
      boolean paginated,
      DispatchBodyMode mode) {
    DispatchContext dispatch =
        buildContext(ctx, op, layout, wrapWithRetry, retryModule, paginated, mode);
    List<ExExpr> core = new ArrayList<>();
    core.add(buildEncodeRequestMatch(dispatch));
    if (dispatch.sigv4()) {
      core.add(buildSignedRequestMatch(dispatch));
    }
    core.add(buildDispatchCase(dispatch));
    if (dispatch.mode() == DispatchBodyMode.SINGLE_PAGE && dispatch.wrapWithRetry()) {
      return buildRetryWrappedBody(dispatch, core);
    }
    return core;
  }

  static List<ExExpr> buildAccumulationAndRecursion(
      String opName,
      boolean hasItems,
      ExExpr itemsExpr,
      ExExpr outputTokenExpr,
      String inputToken) {
    List<ExExpr> body = new ArrayList<>();
    if (hasItems) {
      body.add(
          ExMatch.match(
              ExVarPattern.var("new_acc"), ExOp.op("++", ExVar.var("acc"), itemsExpr)));
    } else {
      body.add(
          ExMatch.match(
              ExVarPattern.var("new_acc"),
              ExList.cons(ExVar.var("output"), ExVar.var("acc"))));
    }
    ExExpr undefinedSuccess =
        hasItems
            ? ExTuple.tuple(ExAtom.atom("ok"), ExVar.var("new_acc"))
            : ExTuple.tuple(
                ExAtom.atom("ok"), ExCall.call("Enum", "reverse", ExVar.var("new_acc")));
    ExExpr nextInput =
        ExMapUpdate.mapUpdate(
            ExVar.var("input"),
            ExMapEntry.entry(ExAtom.atom(inputToken), ExVar.var("next_token")));
    ExExpr recurse =
        ExCallLocal.callLocal(
            opName, ExVar.var("config"), ExVar.var("next_input"), ExVar.var("new_acc"));
    body.add(
        ExCase.caseExpr(
            outputTokenExpr,
            ExCaseBranch.branch(ExNilPattern.nil(), undefinedSuccess),
            ExCaseBranch.branch(
                ExVarPattern.var("next_token"),
                ExExprBlock.block(
                    ExMatch.match(ExVarPattern.var("next_input"), nextInput), recurse))));
    return body;
  }

  static ExExpr buildFieldAccessExpr(
      String rootVar,
      StructureShape rootShape,
      List<MemberShape> path,
      Model model,
      SymbolProvider sp) {
    if (path.size() == 1) {
      return ExStructAccess.structAccess(ExVar.var(rootVar), fieldName(sp, path.get(0)));
    }
    ExExpr[] keys =
        path.stream()
            .map(m -> (ExExpr) ExAtom.atom(fieldName(sp, m)))
            .toArray(ExExpr[]::new);
    return ExCall.call("Kernel", "get_in", ExVar.var(rootVar), ExList.list(keys));
  }

  static ExExpr buildItemsAccessExpr(
      String rootVar,
      StructureShape rootShape,
      List<MemberShape> path,
      Model model,
      SymbolProvider sp) {
    ExExpr access = buildFieldAccessExpr(rootVar, rootShape, path, model, sp);
    return ExOp.op("||", access, ExList.list());
  }

  static String fieldName(SymbolProvider sp, MemberShape member) {
    return sp.toSymbol(member).getProperty("fieldName", String.class).orElseThrow();
  }

  private static DispatchContext buildContext(
      ElixirContext ctx,
      OperationShape op,
      BeamElixirLayout layout,
      boolean wrapWithRetry,
      String retryModule,
      boolean paginated,
      DispatchBodyMode mode) {
    SymbolProvider sp = ctx.symbolProvider();
    Symbol opSym = sp.toSymbol(op);
    boolean sigv4 = BeamSigV4Metadata.from(ctx.service()).isPresent();
    boolean encodeWithConfig =
        ElixirRestJsonSupport.serviceHasHostLabelOperations(ctx.model(), ctx.service())
            || ElixirRestXmlSupport.serviceEncodesWithConfig(ctx.model(), ctx.service());
    return new DispatchContext(
        ctx,
        op,
        layout,
        wrapWithRetry,
        retryModule,
        paginated,
        mode,
        opSym,
        opSym.getName(),
        ElixirSymbolProvider.toModuleName(
            layout.clientCodecModuleName(ctx.resolvedProtocolTraitId(), ctx.integrations())),
        ElixirSymbolProvider.toModuleName(layout.runtimeHttpModuleName()),
        ElixirSymbolProvider.toModuleName(layout.sigv4ModuleName()),
        sigv4,
        encodeWithConfig);
  }

  private static ExMatch buildEncodeRequestMatch(DispatchContext ctx) {
    ExExpr encodeCall =
        ctx.encodeWithConfig()
            ? ExCall.call(
                ctx.codecModule(),
                "encode_" + ctx.opName() + "_request",
                ExVar.var("config"),
                ExVar.var("input"))
            : ExCall.call(
                ctx.codecModule(), "encode_" + ctx.opName() + "_request", ExVar.var("input"));
    return ExMatch.match(ExVarPattern.var("req"), encodeCall);
  }

  private static ExMatch buildSignedRequestMatch(DispatchContext ctx) {
    ExCase credentialsCase =
        ExCase.caseExpr(
            ExCall.call("Map", "get", ExVar.var("config"), ExAtom.atom("credentials")),
            ExCaseBranch.branch(ExNilPattern.nil(), ExVar.var("req")),
            ExCaseBranch.branch(
                ExVarPattern.var("_"),
                ExCall.call(
                    ctx.sigv4Module(),
                    "sign",
                    ExVar.var("config"),
                    ExAtom.atom(ctx.opName()),
                    ExVar.var("req"))));
    return ExMatch.match(ExVarPattern.var("signed_req"), credentialsCase);
  }

  private static ExExpr dispatchRequestVar(DispatchContext ctx) {
    return ctx.sigv4() ? ExVar.var("signed_req") : ExVar.var("req");
  }

  private static ExExpr buildDispatchCase(DispatchContext ctx) {
    ExExpr successExpr = buildDecodeSuccessExpr(ctx);
    return ExCase.caseExpr(
        ExCall.call(
            ctx.runtimeHttpModule(), "dispatch", ExVar.var("config"), dispatchRequestVar(ctx)),
        ExCaseBranch.branch(
            ExTuplePattern.tuple(
                ExAtomPattern.atom("ok"), ExVarPattern.var("resp")),
            successExpr),
        ExCaseBranch.branch(
            ExTuplePattern.tuple(
                ExAtomPattern.atom("error"), ExVarPattern.var("reason")),
            ExTuple.tuple(ExAtom.atom("error"), ExVar.var("reason"))));
  }

  private static ExExpr buildDecodeSuccessExpr(DispatchContext ctx) {
    ExExpr decode =
        ExCall.call(ctx.codecModule(), "decode_" + ctx.opName() + "_response", ExVar.var("resp"));
    if (ctx.mode() == DispatchBodyMode.SINGLE_PAGE) {
      return decode;
    }
    if (ctx.paginated() && ctx.wrapWithRetry()) {
      return decode;
    }
    return buildPaginatedDecodeCase(ctx, decode);
  }

  private static ExCase buildPaginatedDecodeCase(DispatchContext ctx, ExExpr decodeCall) {
    PaginationInfo pi =
        BeamClientPaginationSupport.requirePaginationInfo(
            ctx.ctx().model(), ctx.ctx().service(), ctx.op());
    SymbolProvider sp = ctx.ctx().symbolProvider();
    StructureShape output =
        ctx.ctx().model().expectShape(ctx.op().getOutputShape(), StructureShape.class);
    String inputToken = fieldName(sp, pi.getInputTokenMember());
    ExExpr outputTokenExpr =
        buildFieldAccessExpr(
            "output", output, pi.getOutputTokenMemberPath(), ctx.ctx().model(), sp);
    boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
    ExExpr itemsExpr =
        hasItems
            ? buildItemsAccessExpr(
                "output", output, pi.getItemsMemberPath(), ctx.ctx().model(), sp)
            : null;

    return ExCase.caseExpr(
        decodeCall,
        ExCaseBranch.branch(
            ExTuplePattern.tuple(
                ExAtomPattern.atom("ok"), ExVarPattern.var("output")),
            ExExprBlock.block(
                buildAccumulationAndRecursion(
                        ctx.opName(), hasItems, itemsExpr, outputTokenExpr, inputToken)
                    .toArray(ExExpr[]::new))),
        ExCaseBranch.branch(
            ExTuplePattern.tuple(
                ExAtomPattern.atom("error"), ExVarPattern.var("reason")),
            ExTuple.tuple(ExAtom.atom("error"), ExVar.var("reason"))));
  }

  private static List<ExExpr> buildRetryWrappedBody(DispatchContext ctx, List<ExExpr> core) {
    List<ExExpr> body = new ArrayList<>();
    body.add(
        ExMatch.match(
            ExVarPattern.var("retry_opts"),
            ExCall.call(
                "Map", "get", ExVar.var("config"), ExAtom.atom("retry"), ExList.list())));
    body.add(
        ExCall.call(
            ctx.retryModule(),
            "with_retry",
            ExAnonymousFn.fn(
                ExClause.blockClause(List.of(), ExExprBlock.block(core.toArray(ExExpr[]::new)))),
            ExVar.var("retry_opts")));
    return body;
  }
}
