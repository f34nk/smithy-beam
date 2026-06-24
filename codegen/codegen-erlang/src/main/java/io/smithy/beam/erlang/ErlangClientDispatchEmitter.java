package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamSigV4Metadata;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.List;

final class ErlangClientDispatchEmitter {

    enum DispatchBodyMode {
        SINGLE_PAGE,
        PAGINATED_PAGE
    }

    private ErlangClientDispatchEmitter() {}

    static void emitDispatchBody(
            ErlangContext ctx,
            OperationShape op,
            BeamErlangLayout layout,
            boolean wrapWithRetry,
            String retryModule,
            boolean paginated,
            ErlangWriter writer,
            DispatchBodyMode mode) {
        SymbolProvider sp = ctx.symbolProvider();
        Symbol opSym = sp.toSymbol(op);
        boolean sigv4 = BeamSigV4Metadata.from(ctx.service()).isPresent();
        String sigv4Module = layout.sigv4ModuleName();
        String codecModule =
                layout.clientCodecModuleName(ctx.resolvedProtocolTraitId(), ctx.integrations());

        if (mode == DispatchBodyMode.SINGLE_PAGE && wrapWithRetry) {
            writer.write("RetryOpts = maps:get(retry, Config, #{}),");
            writer.write("$L:with_retry(fun() ->", retryModule);
            writer.indent();
        }

        if (ErlangRestJson1Emitter.serviceHasHostLabelOperations(ctx.model(), ctx.service())
                || ErlangRestXmlEmitter.serviceEncodesWithConfig(ctx.model(), ctx.service())) {
            writer.write("Req = $L:encode_$L_request(Config, Input),",
                    codecModule, opSym.getName());
        } else {
            writer.write("Req = $L:encode_$L_request(Input),",
                    codecModule, opSym.getName());
        }
        if (sigv4) {
            writer.write("SignedReq = case maps:get(credentials, Config, undefined) of");
            writer.indent();
            writer.write("undefined -> Req;");
            writer.write("_ -> $L:sign(Config, $L, Req)", sigv4Module, opSym.getName());
            writer.dedent();
            writer.write("end,");
            writer.write("case $L:dispatch(Config, SignedReq) of",
                    layout.runtimeHttpModuleName());
        } else {
            writer.write("case $L:dispatch(Config, Req) of",
                    layout.runtimeHttpModuleName());
        }
        writer.indent();
        writer.write("{ok, Resp} ->");
        writer.indent();
        if (mode == DispatchBodyMode.SINGLE_PAGE) {
            writer.write("$L:decode_$L_response(Resp);",
                    codecModule, opSym.getName());
        } else if (paginated && wrapWithRetry) {
            writer.write("$L:decode_$L_response(Resp);",
                    codecModule, opSym.getName());
        } else {
            writer.write("case $L:decode_$L_response(Resp) of",
                    codecModule, opSym.getName());
            writer.indent();
            writer.write("{ok, Output} ->");
            writer.indent();
            PaginationInfo pi = BeamClientPaginationSupport.requirePaginationInfo(
                    ctx.model(), ctx.service(), op);
            StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
            StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
            String inputRecord = ErlangClientPaginationEmitter.recordName(sp.toSymbol(input));
            String inputToken = ErlangClientPaginationEmitter.fieldName(sp, pi.getInputTokenMember());
            String outputTokenExpr = ErlangClientPaginationEmitter.recordAccess(
                    "Output", output, pi.getOutputTokenMemberPath(), ctx.model(), sp);
            List<MemberShape> itemsPath = pi.getItemsMemberPath();
            boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
            String itemsExpr = hasItems
                    ? ErlangClientPaginationEmitter.recordAccess(
                            "Output", output, itemsPath, ctx.model(), sp)
                    : null;
            ErlangClientPaginationEmitter.emitAccumulationAndRecursion(
                    writer,
                    opSym,
                    hasItems,
                    itemsExpr,
                    outputTokenExpr,
                    inputRecord,
                    inputToken);
            writer.dedent();
            writer.write("{error, Reason} ->");
            writer.indent();
            writer.write("{error, Reason}");
            writer.dedent();
            writer.dedent();
            writer.write("end;");
        }
        writer.dedent();
        writer.write("{error, Reason} ->");
        writer.indent();
        writer.write("{error, Reason}");
        writer.dedent();
        writer.dedent();

        if (mode == DispatchBodyMode.SINGLE_PAGE) {
            if (wrapWithRetry) {
                writer.write("end");
                writer.dedent();
                writer.write("end, RetryOpts).");
            } else {
                writer.write("end.");
            }
        } else if (!paginated || !wrapWithRetry) {
            writer.write("end.");
        }
    }
}
