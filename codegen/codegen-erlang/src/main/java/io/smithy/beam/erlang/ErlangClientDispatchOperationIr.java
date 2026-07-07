package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.Fun;
import io.beam.ir.erlang.FunClause;
import io.beam.ir.erlang.InfixExpr;
import io.beam.ir.erlang.ListExpr;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.MapExpr;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.OpaqueExpr;
import io.beam.ir.erlang.Pattern;
import io.beam.ir.erlang.RecordExpr;
import io.beam.ir.erlang.RecordField;
import io.beam.ir.erlang.RecordFieldAccessExpr;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ErlangClientDispatchOperationIr {
  private ErlangClientDispatchOperationIr() {}

  enum DispatchBodyMode {
    SINGLE_PAGE,
    PAGINATED_PAGE
  }

  private record DispatchContext(
      ErlangContext ctx,
      OperationShape op,
      BeamErlangLayout layout,
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

  static List<Expression> buildDispatchBody(
      ErlangContext ctx,
      OperationShape op,
      BeamErlangLayout layout,
      boolean wrapWithRetry,
      String retryModule,
      boolean paginated,
      DispatchBodyMode mode) {
    DispatchContext dispatch =
        buildContext(ctx, op, layout, wrapWithRetry, retryModule, paginated, mode);
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
      Symbol opSym,
      boolean hasItems,
      Expression itemsExpr,
      Expression outputTokenExpr,
      String inputRecord,
      String inputToken) {
    List<Expression> body = new ArrayList<>();
    if (hasItems) {
      body.add(
          MatchExpr.bindValue(
              "NewAcc", InfixExpr.of(Variable.of("Acc"), "++", itemsExpr)));
    } else {
      body.add(
          MatchExpr.bindValue(
              "NewAcc",
              ListExpr.of(List.of(Variable.of("Output")), Variable.of("Acc"))));
    }
    Expression undefinedSuccess =
        hasItems
            ? TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("NewAcc")))
            : TupleExpr.of(
                List.of(
                    AtomExpr.of("ok"),
                    RemoteCallExpr.of(
                        "lists", "reverse", List.of(Variable.of("NewAcc")))));
    Expression nextInput =
        RecordExpr.update(
            Variable.of("Input"),
            inputRecord,
            List.of(RecordField.of(inputToken, Variable.of("NextToken"))));
    Expression recurse =
        LocalCallExpr.of(
            opSym.getName(),
            List.of(Variable.of("Config"), Variable.of("NextInput"), Variable.of("NewAcc")));
    Clause undefinedClause = Clause.of(AtomPattern.of("undefined"), undefinedSuccess);
    Expression tokenCase =
        CaseExpr.of(
            outputTokenExpr,
            List.of(
                undefinedClause,
                Clause.of(
                    VariablePattern.of("NextToken"),
                    BlockExpr.commaSeparated(
                        List.of(
                            MatchExpr.bindValue("NextInput", nextInput),
                            recurse),
                        false))));
    body.add(tokenCase);
    return body;
  }

  static Expression buildRecordAccessExpr(
      String rootVar,
      StructureShape rootShape,
      List<MemberShape> path,
      Model model,
      SymbolProvider sp) {
    Expression expr = Variable.of(rootVar);
    Shape container = rootShape;
    for (MemberShape member : path) {
      String record = recordName(sp.toSymbol(container));
      String field = fieldName(sp, member);
      expr = RecordFieldAccessExpr.of(expr, record, field);
      container = model.expectShape(member.getTarget(), Shape.class);
    }
    return expr;
  }

  static String recordName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }

  static String fieldName(SymbolProvider sp, MemberShape member) {
    return sp.toSymbol(member).getProperty("fieldName", String.class).orElseThrow();
  }

  private static DispatchContext buildContext(
      ErlangContext ctx,
      OperationShape op,
      BeamErlangLayout layout,
      boolean wrapWithRetry,
      String retryModule,
      boolean paginated,
      DispatchBodyMode mode) {
    SymbolProvider sp = ctx.symbolProvider();
    Symbol opSym = sp.toSymbol(op);
    boolean sigv4 = BeamSigV4Metadata.from(ctx.service()).isPresent();
    boolean encodeWithConfig =
        ErlangRestJsonSupport.serviceHasHostLabelOperations(ctx.model(), ctx.service())
            || ErlangRestXmlSupport.serviceEncodesWithConfig(ctx.model(), ctx.service());
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
        layout.clientCodecModuleName(ctx.resolvedProtocolTraitId(), ctx.integrations()),
        layout.runtimeHttpModuleName(),
        layout.sigv4ModuleName(),
        sigv4,
        encodeWithConfig);
  }

  private static Expression buildEncodeRequestMatch(DispatchContext ctx) {
    Expression encodeCall =
        ctx.encodeWithConfig()
            ? RemoteCallExpr.of(
                ctx.codecModule(),
                "encode_" + ctx.opName() + "_request",
                List.of(Variable.of("Config"), Variable.of("Input")))
            : RemoteCallExpr.of(
                ctx.codecModule(),
                "encode_" + ctx.opName() + "_request",
                List.of(Variable.of("Input")));
    return MatchExpr.bindValue("Req", encodeCall);
  }

  private static Expression buildSignedRequestMatch(DispatchContext ctx) {
    String sigv4Mod = ctx.sigv4Module();
    String opName = ctx.opName();
    return MatchExpr.bindValue(
        "SignedReq",
        OpaqueExpr.of(
            """
            case maps:get(credentials, Config, undefined) of
                undefined ->
                    case aws_credentials:get_credentials() of
                        undefined ->
                            Req;
                        Creds0 ->
                            Creds = #{
                                access_key_id => maps:get(access_key_id, Creds0),
                                secret_access_key => maps:get(secret_access_key, Creds0),
                                session_token => maps:get(token, Creds0, undefined)
                            },
                            %s:sign(Config#{credentials => Creds}, %s, Req)
                    end;
                _ ->
                    %s:sign(Config, %s, Req)
            end"""
                .formatted(sigv4Mod, opName, sigv4Mod, opName)
                .strip()));
  }

  private static Expression dispatchRequestVar(DispatchContext ctx) {
    return ctx.sigv4() ? Variable.of("SignedReq") : Variable.of("Req");
  }

  private static Expression buildDispatchCase(DispatchContext ctx) {
    Expression successExpr = buildDecodeSuccessExpr(ctx);
    return CaseExpr.of(
        RemoteCallExpr.of(
            ctx.runtimeHttpModule(),
            "dispatch",
            List.of(Variable.of("Config"), dispatchRequestVar(ctx))),
        List.of(
            Clause.of(
                TuplePattern.of(
                    List.of(AtomPattern.of("ok"), VariablePattern.of("Resp"))),
                successExpr),
            Clause.of(
                TuplePattern.of(
                    List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
                TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("Reason"))))));
  }

  private static Expression buildDecodeSuccessExpr(DispatchContext ctx) {
    Expression decode =
        RemoteCallExpr.of(
            ctx.codecModule(), "decode_" + ctx.opName() + "_response", List.of(Variable.of("Resp")));
    if (ctx.mode() == DispatchBodyMode.SINGLE_PAGE) {
      return decode;
    }
    if (ctx.paginated() && ctx.wrapWithRetry()) {
      return decode;
    }
    return buildPaginatedDecodeCase(ctx, decode);
  }

  private static Expression buildPaginatedDecodeCase(DispatchContext ctx, Expression decodeCall) {
    PaginationInfo pi =
        BeamClientPaginationSupport.requirePaginationInfo(
            ctx.ctx().model(), ctx.ctx().service(), ctx.op());
    SymbolProvider sp = ctx.ctx().symbolProvider();
    StructureShape output =
        ctx.ctx().model().expectShape(ctx.op().getOutputShape(), StructureShape.class);
    StructureShape input =
        ctx.ctx().model().expectShape(ctx.op().getInputShape(), StructureShape.class);
    String inputRecord = recordName(sp.toSymbol(input));
    String inputToken = fieldName(sp, pi.getInputTokenMember());
    Expression outputTokenExpr =
        buildRecordAccessExpr(
            "Output", output, pi.getOutputTokenMemberPath(), ctx.ctx().model(), sp);
    boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
    Expression itemsExpr =
        hasItems
            ? buildRecordAccessExpr(
                "Output", output, pi.getItemsMemberPath(), ctx.ctx().model(), sp)
            : null;

    return CaseExpr.of(
        decodeCall,
        List.of(
            Clause.of(
                TuplePattern.of(
                    List.of(AtomPattern.of("ok"), VariablePattern.of("Output"))),
                BlockExpr.commaSeparated(
                    buildAccumulationAndRecursion(
                        ctx.opSym(),
                        hasItems,
                        itemsExpr,
                        outputTokenExpr,
                        inputRecord,
                        inputToken),
                    false)),
            Clause.of(
                TuplePattern.of(
                    List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
                TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("Reason"))))));
  }

  private static List<Expression> buildRetryWrappedBody(
      DispatchContext ctx, List<Expression> core) {
    Expression retryFun =
        Fun.of(
            List.of(
                FunClause.of(
                    List.of(),
                    core.size() == 1 ? core.get(0) : BlockExpr.commaSeparated(core, false))));
    return List.of(
        MatchExpr.bindValue(
            "RetryOpts",
            RemoteCallExpr.of(
                "maps",
                "get",
                List.of(
                    AtomExpr.of("retry"),
                    Variable.of("Config"),
                    MapExpr.of(List.of())))),
        RemoteCallExpr.of(
            ctx.retryModule(),
            "with_retry",
            List.of(retryFun, Variable.of("RetryOpts"))));
  }
}
