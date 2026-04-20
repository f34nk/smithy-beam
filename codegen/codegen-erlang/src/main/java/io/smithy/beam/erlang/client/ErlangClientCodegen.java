package io.smithy.beam.erlang.client;

import io.smithy.beam.core.Mode;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangIntegration;
import io.smithy.beam.erlang.codegen.ErlangReservedWords;
import io.smithy.beam.erlang.codegen.ErlangSettings;
import io.smithy.beam.erlang.codegen.ErlangSymbolProvider;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import io.smithy.beam.core.binding.EventStreamHelper;
import io.smithy.beam.core.binding.PaginationHelper;
import io.smithy.beam.core.binding.WaiterHelper;
import io.smithy.beam.erlang.codegen.sections.EnumValuesSection;
import io.smithy.beam.erlang.codegen.sections.EventStreamSection;
import io.smithy.beam.erlang.codegen.sections.ModuleAttributesSection;
import io.smithy.beam.erlang.codegen.sections.OperationDocSection;
import io.smithy.beam.erlang.codegen.sections.OperationReceiveSection;
import io.smithy.beam.erlang.codegen.sections.OperationRequestSection;
import io.smithy.beam.erlang.codegen.sections.OperationResponseSection;
import io.smithy.beam.erlang.codegen.sections.OperationSendSection;
import io.smithy.beam.erlang.codegen.sections.OperationSpecSection;
import io.smithy.beam.erlang.codegen.sections.OperationValidationSection;
import io.smithy.beam.erlang.codegen.sections.PaginationHelperSection;
import io.smithy.beam.erlang.codegen.sections.ServiceErrorHelpersSection;
import io.smithy.beam.erlang.codegen.sections.StructTypeSection;
import io.smithy.beam.erlang.codegen.sections.UnionVariantsSection;
import io.smithy.beam.erlang.codegen.sections.WaiterSection;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.CreateContextDirective;
import software.amazon.smithy.codegen.core.directed.CreateSymbolProviderDirective;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.codegen.core.directed.DirectedCodegen;
import software.amazon.smithy.codegen.core.directed.GenerateEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateErrorDirective;
import software.amazon.smithy.codegen.core.directed.GenerateIntEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.utils.CaseUtils;

/**
 * {@link DirectedCodegen} implementation for the Erlang client plugin.
 *
 * <p>{@code S} is fixed to {@link ErlangSettings} (the base settings type) because
 * {@link ErlangContext} is typed on {@code ErlangSettings}. Client-specific
 * settings ({@link ErlangClientSettings}) are accessed via {@code (ErlangClientSettings)
 * d.context().settings()} inside methods that need them.
 */
public final class ErlangClientCodegen
        implements DirectedCodegen<ErlangContext, ErlangSettings, ErlangIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(CreateSymbolProviderDirective<ErlangSettings> d) {
        return SymbolProvider.cache(
                new ErlangSymbolProvider(d.model(), d.settings(), Mode.CLIENT));
    }

    @Override
    public ErlangContext createContext(CreateContextDirective<ErlangSettings, ErlangIntegration> d) {
        WriterDelegator<ErlangWriter> delegator = new WriterDelegator<>(
                d.fileManifest(),
                d.symbolProvider(),
                (filename, namespace) -> new ErlangWriter(filename));
        return new ErlangContext(
                d.model(),
                ModelTransformer.create(),
                d.settings(),
                d.symbolProvider(),
                d.fileManifest(),
                delegator,
                d.integrations(),
                d.service());
    }

    @Override
    public void generateService(GenerateServiceDirective<ErlangContext, ErlangSettings> d) {
        d.context().writerDelegator().useShapeWriter(d.service(), writer -> {
            writer.injectSection(new ModuleAttributesSection(d.service()));

            for (OperationShape op : d.operations()) {
                String fnName = toFunctionName(op);
                String inputType = operationInputType(d.context(), op);
                String outputType = operationOutputType(d.context(), op);
                String inputRecord = inputType;

                // ── arity-2 public overload (simple delegate) ───────────────
                // Doc comment (empty by default; doc integrations populate).
                writer.injectSection(new OperationDocSection(op));
                // -spec line (empty by default; ErlangSpecIntegration populates).
                writer.injectSection(new OperationSpecSection(op));
                writer.write("$L(Client, Input) ->", fnName);
                writer.write("    $L(Client, Input, #{}).", fnName);
                writer.write("");

                // ── arity-3 public overload (options + retry) ───────────────
                writer.write("%% Calls the $L operation with options", op.getId().getName());
                writer.write("-spec $L(Client :: map(), Input :: $L(), Options :: map()) ->",
                        fnName, inputType);
                writer.write("    {ok, $L()} | {error, term()}.", outputType);
                writer.write("$L(Client, Input, Options) when is_record(Input, $L), is_map(Options) ->",
                        fnName, inputRecord);

                // validate_<op>_input/1 helper (empty by default).
                writer.injectSection(new OperationValidationSection(op));

                // OperationSendSection: default stub body; protocol integrations replace this.
                writer.pushState(new OperationSendSection(op));
                writer.write("    {error, not_implemented}.");
                writer.popState();

                writer.write("");

                // ── internal helper (below the public functions) ────────────
                // make_<op>_request/2 helper (empty by default; protocol integrations populate).
                writer.injectSection(new OperationRequestSection(op));
                // Status branch + decode helper (empty by default).
                writer.injectSection(new OperationResponseSection(op));
                // Top-level fn body continuation (empty by default).
                writer.injectSection(new OperationReceiveSection(op));

                // Pagination stream helper — only emitted for paginated operations.
                if (PaginationHelper.isPaginated(d.model(), d.service(), op)) {
                    writer.injectSection(new PaginationHelperSection(op));
                }

                // Waiter helpers — one section per named waiter.
                WaiterHelper.waiters(d.model(), op).forEach((name, waiter) ->
                        writer.injectSection(new WaiterSection(op, waiter)));

                // Event-stream helpers (empty by default; ErlangEventStreamIntegration populates).
                if (EventStreamHelper.hasEventStream(d.model(), op)) {
                    writer.injectSection(new EventStreamSection(op));
                }

                writer.write("");
                writer.addExport(fnName, 2);
                writer.addExport(fnName, 3);
            }

            // Service-level error helper functions (errors/0, is_error/1,
            // error_to_atom/1). Empty by default; ErlangErrorIntegration
            // populates these. Protocol integrations append parse_error/2
            // to the same section.
            writer.injectSection(new ServiceErrorHelpersSection(d.service()));
        });
    }

    private static String operationInputType(ErlangContext ctx, OperationShape op) {
        return CaseUtils.toSnakeCase(
                ctx.model().expectShape(op.getInputShape()).getId().getName());
    }

    private static String operationOutputType(ErlangContext ctx, OperationShape op) {
        return CaseUtils.toSnakeCase(
                ctx.model().expectShape(op.getOutputShape()).getId().getName());
    }

    @Override
    public void generateStructure(GenerateStructureDirective<ErlangContext, ErlangSettings> d) {
        d.context().writerDelegator().useShapeWriter(d.shape(), writer -> {
            writer.pushState(new StructTypeSection(d.shape()));
            writer.writeRecord(d.shape(), d.symbolProvider());
            writer.popState();
        });
    }

    @Override
    public void generateError(GenerateErrorDirective<ErlangContext, ErlangSettings> d) {
        d.context().writerDelegator().useShapeWriter(d.shape(), writer -> {
            writer.pushState(new StructTypeSection(d.shape()));
            writer.writeRecord(d.shape(), d.symbolProvider());
            writer.popState();
        });
    }

    @Override
    public void generateUnion(GenerateUnionDirective<ErlangContext, ErlangSettings> d) {
        d.context().writerDelegator().useShapeWriter(d.shape(), writer -> {
            writer.pushState(new UnionVariantsSection(d.shape()));
            writer.writeUnionType(d.shape(), d.symbolProvider());
            writer.popState();
        });
    }

    @Override
    public void generateEnumShape(GenerateEnumDirective<ErlangContext, ErlangSettings> d) {
        EnumShape enumShape = d.expectEnumShape();
        d.context().writerDelegator().useShapeWriter(enumShape, writer -> {
            writer.pushState(new EnumValuesSection(enumShape));
            writer.writeEnumType(enumShape);
            writer.popState();
        });
    }

    @Override
    public void generateIntEnumShape(GenerateIntEnumDirective<ErlangContext, ErlangSettings> d) {
        IntEnumShape intEnumShape = d.expectIntEnumShape();
        d.context().writerDelegator().useShapeWriter(intEnumShape, writer ->
                writer.writeIntEnumType(intEnumShape));
    }

    @Override
    public void customizeBeforeIntegrations(CustomizeDirective<ErlangContext, ErlangSettings> d) {
        // flushExports() is a marker that signals all addExport() calls are done.
        // The actual -module(…) and -export([…]) header is assembled lazily in ErlangWriter.toString().
        d.context().writerDelegator().useShapeWriter(d.service(), ErlangWriter::flushExports);
    }

    /** Converts an operation shape's local name to a snake_case Erlang function name. */
    private static String toFunctionName(OperationShape op) {
        return ErlangReservedWords.MEMBER_NAMES.escape(
                CaseUtils.toSnakeCase(op.getId().getName()));
    }
}
