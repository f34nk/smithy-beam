package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AnonFun;
import io.beam.dsl.elixir.AnonFunClause;
import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.AtomPattern;
import io.beam.dsl.elixir.BlockExpr;
import io.beam.dsl.elixir.CaptureExpr;
import io.beam.dsl.elixir.CaseExpr;
import io.beam.dsl.elixir.Clause;
import io.beam.dsl.elixir.DotCallExpr;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.InfixExpr;
import io.beam.dsl.elixir.ListExpr;
import io.beam.dsl.elixir.LocalCallExpr;
import io.beam.dsl.elixir.MapEntry;
import io.beam.dsl.elixir.MapExpr;
import io.beam.dsl.elixir.MatchExpr;
import io.beam.dsl.elixir.NilPattern;
import io.beam.dsl.elixir.RemoteCallExpr;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ElixirClientDispatchOperationDsl {
  private ElixirClientDispatchOperationDsl() {}

  enum DispatchBodyMode {
    SINGLE_PAGE,
    PAGINATED_PAGE
  }

  private record DispatchContext(
      ElixirContext ctx,
      OperationShape op,
      BeamElixirLayout layout,
      boolean wrapWithRetry,
      String clientModule,
      boolean paginated,
      DispatchBodyMode mode,
      Symbol opSym,
      String opName,
      String codecModule,
      String runtimeHttpModule,
      boolean sigv4,
      boolean encodeWithConfig) {}

  static List<Expression> buildDispatchBody(
      ElixirContext ctx,
      OperationShape op,
      BeamElixirLayout layout,
      boolean wrapWithRetry,
      String clientModule,
      boolean paginated,
      DispatchBodyMode mode) {
    DispatchContext dispatch =
        buildContext(ctx, op, layout, wrapWithRetry, clientModule, paginated, mode);
    List<Expression> core = new ArrayList<>();
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

  static List<Expression> buildAccumulationAndRecursion(
      String opName,
      boolean hasItems,
      Expression itemsExpr,
      Expression outputTokenExpr,
      String inputToken) {
    List<Expression> body = new ArrayList<>();
    if (hasItems) {
      body.add(MatchExpr.bind("new_acc", InfixExpr.of(Variable.of("acc"), "++", itemsExpr)));
    } else {
      body.add(
          MatchExpr.bind(
              "new_acc", ListExpr.of(List.of(Variable.of("output")), Variable.of("acc"))));
    }
    Expression undefinedSuccess =
        hasItems
            ? TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("new_acc")))
            : TupleExpr.of(
                List.of(
                    AtomExpr.of("ok"),
                    RemoteCallExpr.of("Enum", "reverse", List.of(Variable.of("new_acc")))));
    Expression nextInput =
        MapExpr.of(
            Variable.of("input"), List.of(MapEntry.atomKey(inputToken, Variable.of("next_token"))));
    Expression recurse =
        LocalCallExpr.of(
            opName,
            List.of(Variable.of("config"), Variable.of("next_input"), Variable.of("new_acc")));
    body.add(
        CaseExpr.of(
            outputTokenExpr,
            List.of(
                Clause.of(NilPattern.of(), undefinedSuccess),
                Clause.of(
                    VariablePattern.of("next_token"),
                    BlockExpr.of(List.of(MatchExpr.bind("next_input", nextInput), recurse))))));
    return body;
  }

  static Expression buildFieldAccessExpr(
      String rootVar,
      StructureShape rootShape,
      List<MemberShape> path,
      Model model,
      SymbolProvider sp) {
    if (path.size() == 1) {
      return DotCallExpr.of(Variable.of(rootVar), fieldName(sp, path.get(0)), List.of());
    }
    List<Expression> keys =
        path.stream().map(m -> (Expression) AtomExpr.of(fieldName(sp, m))).toList();
    return RemoteCallExpr.of("Kernel", "get_in", List.of(Variable.of(rootVar), ListExpr.of(keys)));
  }

  static Expression buildItemsAccessExpr(
      String rootVar,
      StructureShape rootShape,
      List<MemberShape> path,
      Model model,
      SymbolProvider sp) {
    Expression access = buildFieldAccessExpr(rootVar, rootShape, path, model, sp);
    return InfixExpr.of(access, "||", ListExpr.of(List.of()));
  }

  static String fieldName(SymbolProvider sp, MemberShape member) {
    return sp.toSymbol(member).getProperty("fieldName", String.class).orElseThrow();
  }

  static Expression retryOptsBinding() {
    return MatchExpr.bind(
        "retry_opts",
        RemoteCallExpr.of(
            "Map",
            "get",
            List.of(Variable.of("config"), AtomExpr.of("retry"), ListExpr.of(List.of()))));
  }

  static Expression withRetryCall(String clientModule, Expression retryFun) {
    return RemoteCallExpr.of(
        "RuntimeHttp",
        "with_retry",
        List.of(
            retryFun,
            RemoteCallExpr.of(
                "Keyword",
                "merge",
                List.of(
                    ListExpr.of(
                        List.of(
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("should_retry"),
                                    CaptureExpr.of(clientModule + ".should_retry?", 1))))),
                    Variable.of("retry_opts")))));
  }

  private static DispatchContext buildContext(
      ElixirContext ctx,
      OperationShape op,
      BeamElixirLayout layout,
      boolean wrapWithRetry,
      String clientModule,
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
        clientModule,
        paginated,
        mode,
        opSym,
        opSym.getName(),
        ElixirSymbolProvider.toModuleName(
            layout.clientCodecModuleName(ctx.resolvedProtocolTraitId(), ctx.integrations())),
        ElixirSymbolProvider.toModuleName(layout.runtimeHttpModuleName()),
        sigv4,
        encodeWithConfig);
  }

  private static MatchExpr buildEncodeRequestMatch(DispatchContext ctx) {
    Expression encodeCall =
        ctx.encodeWithConfig()
            ? RemoteCallExpr.of(
                ctx.codecModule(),
                "encode_" + ctx.opName() + "_request",
                List.of(Variable.of("config"), Variable.of("input")))
            : RemoteCallExpr.of(
                ctx.codecModule(),
                "encode_" + ctx.opName() + "_request",
                List.of(Variable.of("input")));
    return MatchExpr.bind("req", encodeCall);
  }

  private static MatchExpr buildSignedRequestMatch(DispatchContext ctx) {
    String opName = ctx.opName();
    Expression signWithConfig =
        RemoteCallExpr.of(
            "AwsSigv4",
            "sign",
            List.of(Variable.of("config"), AtomExpr.of(opName), Variable.of("req")));
    Expression credsMap =
        MapExpr.of(
            List.of(
                MapEntry.atomKey(
                    "access_key_id",
                    RemoteCallExpr.of(
                        "Map",
                        "get",
                        List.of(Variable.of("creds0"), AtomExpr.of("access_key_id")))),
                MapEntry.atomKey(
                    "secret_access_key",
                    RemoteCallExpr.of(
                        "Map",
                        "get",
                        List.of(Variable.of("creds0"), AtomExpr.of("secret_access_key")))),
                MapEntry.atomKey(
                    "session_token",
                    RemoteCallExpr.of(
                        "Map", "get", List.of(Variable.of("creds0"), AtomExpr.of("token"))))));
    Expression signWithMergedCreds =
        RemoteCallExpr.of(
            "AwsSigv4",
            "sign",
            List.of(
                RemoteCallExpr.of(
                    "Map",
                    "put",
                    List.of(
                        Variable.of("config"),
                        AtomExpr.of("credentials"),
                        Variable.of("creds"))),
                AtomExpr.of(opName),
                Variable.of("req")));
    Expression undefinedCredentialsBranch =
        CaseExpr.of(
            RemoteCallExpr.of(":aws_credentials", "get_credentials", List.of()),
            List.of(
                Clause.of(AtomPattern.of("undefined"), Variable.of("req")),
                Clause.of(
                    VariablePattern.of("creds0"),
                    MatchExpr.bind("creds", credsMap, signWithMergedCreds))));
    Expression credentialsCase =
        CaseExpr.of(
            RemoteCallExpr.of(
                "Map", "get", List.of(Variable.of("config"), AtomExpr.of("credentials"))),
            List.of(
                Clause.of(NilPattern.of(), undefinedCredentialsBranch),
                Clause.of(VariablePattern.of("_"), signWithConfig)));
    return MatchExpr.bind("signed_req", credentialsCase);
  }

  private static Expression dispatchRequestVar(DispatchContext ctx) {
    return ctx.sigv4() ? Variable.of("signed_req") : Variable.of("req");
  }

  private static CaseExpr buildDispatchCase(DispatchContext ctx) {
    Expression successExpr = buildDecodeSuccessExpr(ctx);
    return CaseExpr.of(
        RemoteCallExpr.of(
            ctx.runtimeHttpModule(),
            "dispatch",
            List.of(Variable.of("config"), dispatchRequestVar(ctx))),
        List.of(
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("resp"))),
                successExpr),
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("reason"))),
                TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("reason"))))));
  }

  private static Expression buildDecodeSuccessExpr(DispatchContext ctx) {
    Expression decode =
        RemoteCallExpr.of(
            ctx.codecModule(),
            "decode_" + ctx.opName() + "_response",
            List.of(Variable.of("resp")));
    if (ctx.mode() == DispatchBodyMode.SINGLE_PAGE) {
      return decode;
    }
    if (ctx.paginated() && ctx.wrapWithRetry()) {
      return decode;
    }
    return buildPaginatedDecodeCase(ctx, decode);
  }

  private static CaseExpr buildPaginatedDecodeCase(DispatchContext ctx, Expression decodeCall) {
    PaginationInfo pi =
        BeamClientPaginationSupport.requirePaginationInfo(
            ctx.ctx().model(), ctx.ctx().service(), ctx.op());
    SymbolProvider sp = ctx.ctx().symbolProvider();
    StructureShape output =
        ctx.ctx().model().expectShape(ctx.op().getOutputShape(), StructureShape.class);
    String inputToken = fieldName(sp, pi.getInputTokenMember());
    Expression outputTokenExpr =
        buildFieldAccessExpr(
            "output", output, pi.getOutputTokenMemberPath(), ctx.ctx().model(), sp);
    boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
    Expression itemsExpr =
        hasItems
            ? buildItemsAccessExpr("output", output, pi.getItemsMemberPath(), ctx.ctx().model(), sp)
            : null;

    return CaseExpr.of(
        decodeCall,
        List.of(
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("output"))),
                BlockExpr.of(
                    buildAccumulationAndRecursion(
                        ctx.opName(), hasItems, itemsExpr, outputTokenExpr, inputToken))),
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("reason"))),
                TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("reason"))))));
  }

  private static List<Expression> buildRetryWrappedBody(
      DispatchContext ctx, List<Expression> core) {
    Expression retryFun =
        AnonFun.of(
            List.of(
                AnonFunClause.of(List.of(), core.size() == 1 ? core.get(0) : BlockExpr.of(core))));
    return List.of(retryOptsBinding(), withRetryCall(ctx.clientModule(), retryFun));
  }
}
