package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.binding.PaginationHelper;
import io.smithy.beam.erlang.codegen.sections.PaginationHelperSection;
import java.util.List;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Erlang feature integration that emits pagination helpers ({@code <op>_stream/3}
 * and {@code <op>_pages/2}) for every operation that carries a {@code @paginated}
 * trait.
 *
 * <p>The integration appends to {@link PaginationHelperSection} — that section
 * is only pushed by {@code ErlangClientCodegen.generateService} when
 * {@link PaginationHelper#isPaginated} is true, so this interceptor never has
 * to gate on the protocol or on the trait itself.
 *
 * <p>The generated helpers delegate to {@code smithy_pagination:stream/3} and
 * {@code smithy_pagination:pages/3} (see
 * {@code runtime-erlang/client/smithy_pagination.erl}). The runtime stays
 * record-shape-agnostic by accepting setter/getter closures as part of the
 * Opts map, so this integration emits inline lambdas that close over the
 * generated record type for the operation's input and output.
 */
public final class ErlangPaginationIntegration implements ErlangIntegration {

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext ctx) {
        return List.of(
                CodeInterceptor.appender(PaginationHelperSection.class, (writer, section) ->
                        PaginationHelper.info(ctx.model(), ctx.service(), section.operation())
                                .ifPresent(info -> emit(writer, ctx, section.operation(), info))));
    }

    private static void emit(ErlangWriter w, ErlangContext ctx, OperationShape op, PaginationInfo info) {
        w.addDependency(ErlangDependency.SMITHY_PAGINATION);

        String fnName = CaseUtils.toSnakeCase(op.getId().getName());

        StructureShape inputShape = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        StructureShape outputShape = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        String inputRecord = CaseUtils.toSnakeCase(inputShape.getId().getName());
        String outputRecord = CaseUtils.toSnakeCase(outputShape.getId().getName());

        String inputTokenField = memberFieldName(info.getInputTokenMember());
        String outputTokenField = memberFieldName(lastMember(info.getOutputTokenMemberPath()));
        String itemsField = info.getItemsMemberPath().isEmpty()
                ? null
                : memberFieldName(lastMember(info.getItemsMemberPath()));

        emitPagesHelper(w, fnName, inputRecord, outputRecord, inputTokenField, outputTokenField);
        emitStreamHelper(w, fnName, inputRecord, outputRecord, inputTokenField, outputTokenField, itemsField);

        w.addExport(fnName + "_pages", 2);
        w.addExport(fnName + "_stream", 3);
    }

    private static void emitPagesHelper(ErlangWriter w,
                                        String fnName,
                                        String inputRecord,
                                        String outputRecord,
                                        String inputTokenField,
                                        String outputTokenField) {
        w.write("");
        w.write("%% Returns a continuation that yields one full $L output per call.", fnName);
        w.write("-spec $L_pages(Client :: map(), Input :: $L()) -> fun(() -> term()).",
                fnName, inputRecord);
        w.write("$L_pages(Client, Input) ->", fnName);
        w.indent();
        w.write("smithy_pagination:pages(");
        w.indent();
        w.write("fun(I) -> $L(Client, I) end,", fnName);
        w.write("Input,");
        w.write("#{");
        w.indent();
        w.write("set_input_token => fun(I, T) -> I#$L{$L = T} end,", inputRecord, inputTokenField);
        w.write("output_token => fun(O) -> O#$L.$L end", outputRecord, outputTokenField);
        w.dedent();
        w.write("}");
        w.dedent();
        w.write(").");
        w.dedent();
        w.write("");
    }

    private static void emitStreamHelper(ErlangWriter w,
                                         String fnName,
                                         String inputRecord,
                                         String outputRecord,
                                         String inputTokenField,
                                         String outputTokenField,
                                         String itemsField) {
        w.write("%% Returns a continuation that yields the items of $L across pages.", fnName);
        w.write("-spec $L_stream(Client :: map(), Input :: $L(), Opts :: map()) -> fun(() -> term()).",
                fnName, inputRecord);
        w.write("$L_stream(Client, Input, _Opts) ->", fnName);
        w.indent();
        w.write("smithy_pagination:stream(");
        w.indent();
        w.write("fun(I) -> $L(Client, I) end,", fnName);
        w.write("Input,");
        w.write("#{");
        w.indent();
        w.write("set_input_token => fun(I, T) -> I#$L{$L = T} end,", inputRecord, inputTokenField);
        if (itemsField == null) {
            w.write("output_token => fun(O) -> O#$L.$L end", outputRecord, outputTokenField);
        } else {
            w.write("output_token => fun(O) -> O#$L.$L end,", outputRecord, outputTokenField);
            w.write("items => fun(O) -> O#$L.$L end", outputRecord, itemsField);
        }
        w.dedent();
        w.write("}");
        w.dedent();
        w.write(").");
        w.dedent();
        w.write("");
    }

    private static MemberShape lastMember(List<MemberShape> path) {
        return path.get(path.size() - 1);
    }

    private static String memberFieldName(MemberShape member) {
        return ErlangReservedWords.MEMBER_NAMES.escape(
                CaseUtils.toSnakeCase(member.getMemberName()));
    }
}
