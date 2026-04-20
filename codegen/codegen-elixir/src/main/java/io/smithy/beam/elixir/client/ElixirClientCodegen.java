package io.smithy.beam.elixir.client;

import io.smithy.beam.core.Mode;
import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirIntegration;
import io.smithy.beam.elixir.codegen.ElixirReservedWords;
import io.smithy.beam.elixir.codegen.ElixirSettings;
import io.smithy.beam.elixir.codegen.ElixirSymbolProvider;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import io.smithy.beam.core.binding.EventStreamHelper;
import io.smithy.beam.core.binding.PaginationHelper;
import io.smithy.beam.core.binding.WaiterHelper;
import io.smithy.beam.elixir.codegen.sections.EnumValuesSection;
import io.smithy.beam.elixir.codegen.sections.EventStreamSection;
import io.smithy.beam.elixir.codegen.sections.ModuleAttributesSection;
import io.smithy.beam.elixir.codegen.sections.OperationDocSection;
import io.smithy.beam.elixir.codegen.sections.OperationErrorSection;
import io.smithy.beam.elixir.codegen.sections.OperationReceiveSection;
import io.smithy.beam.elixir.codegen.sections.OperationRequestSection;
import io.smithy.beam.elixir.codegen.sections.OperationResponseSection;
import io.smithy.beam.elixir.codegen.sections.OperationSendSection;
import io.smithy.beam.elixir.codegen.sections.OperationSpecSection;
import io.smithy.beam.elixir.codegen.sections.OperationValidationSection;
import io.smithy.beam.elixir.codegen.sections.PaginationHelperSection;
import io.smithy.beam.elixir.codegen.sections.UnionVariantsSection;
import io.smithy.beam.elixir.codegen.sections.WaiterSection;
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
 * {@link DirectedCodegen} implementation for the Elixir client plugin.
 *
 * <p>{@code S} is fixed to {@link ElixirSettings} (the base settings type) because
 * {@link ElixirContext} is typed on {@code ElixirSettings}. Client-specific
 * settings ({@link ElixirClientSettings}) are accessed via {@code (ElixirClientSettings)
 * d.context().settings()} inside methods that need them.
 */
public final class ElixirClientCodegen
        implements DirectedCodegen<ElixirContext, ElixirSettings, ElixirIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(CreateSymbolProviderDirective<ElixirSettings> d) {
        return SymbolProvider.cache(
                new ElixirSymbolProvider(d.model(), d.settings(), Mode.CLIENT));
    }

    @Override
    public ElixirContext createContext(CreateContextDirective<ElixirSettings, ElixirIntegration> d) {
        WriterDelegator<ElixirWriter> delegator = new WriterDelegator<>(
                d.fileManifest(),
                d.symbolProvider(),
                (filename, namespace) -> new ElixirWriter(filename));
        return new ElixirContext(
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
    public void generateService(GenerateServiceDirective<ElixirContext, ElixirSettings> d) {
        String namespace = d.settings().getNamespace();
        d.context().writerDelegator().useShapeWriter(d.service(), writer -> {
            writer.writeDefModule(namespace + ".Client", () -> {
                writer.injectSection(new ModuleAttributesSection(d.service()));

                for (OperationShape op : d.operations()) {
                    String fnName = toFunctionName(op);

                    // @spec line (empty by default; ElixirSpecIntegration populates).
                    writer.injectSection(new OperationSpecSection(op));
                    // @doc comment (empty by default; doc integrations populate).
                    writer.injectSection(new OperationDocSection(op));
                    // validate_<op>_input/1 helper (empty by default).
                    writer.injectSection(new OperationValidationSection(op));
                    // make_<op>_request/2 helper (empty by default).
                    writer.injectSection(new OperationRequestSection(op));

                    // Function clause: def op_name(config, input) do
                    writer.write("def $L(config, input) do", fnName);
                    writer.indent();

                    // OperationSendSection: default stub body; protocol integrations replace this.
                    writer.pushState(new OperationSendSection(op));
                    writer.write("{:error, :not_implemented}");
                    writer.popState();

                    // Status branch + decode helper (empty by default).
                    writer.injectSection(new OperationResponseSection(op));
                    // Top-level fn body continuation (empty by default).
                    writer.injectSection(new OperationReceiveSection(op));

                    writer.dedent();
                    writer.write("end");

                    // parse_error/2 helper (empty by default).
                    writer.injectSection(new OperationErrorSection(op));

                    // Pagination stream helper — only emitted for paginated operations.
                    if (PaginationHelper.isPaginated(d.model(), d.service(), op)) {
                        writer.injectSection(new PaginationHelperSection(op));
                    }

                    // Waiter helpers — one section per named waiter.
                    WaiterHelper.waiters(d.model(), op).forEach((name, waiter) ->
                            writer.injectSection(new WaiterSection(op, waiter)));

                    // Event-stream helpers (empty by default; ElixirEventStreamIntegration populates).
                    if (EventStreamHelper.hasEventStream(d.model(), op)) {
                        writer.injectSection(new EventStreamSection(op));
                    }

                    writer.write("");
                }
            });
        });
    }

    @Override
    public void generateStructure(GenerateStructureDirective<ElixirContext, ElixirSettings> d) {
        d.context().writerDelegator().useShapeWriter(d.shape(), writer ->
                writer.writeStructModule(d.shape(), d.symbolProvider()));
    }

    @Override
    public void generateError(GenerateErrorDirective<ElixirContext, ElixirSettings> d) {
        d.context().writerDelegator().useShapeWriter(d.shape(), writer ->
                writer.writeStructModule(d.shape(), d.symbolProvider()));
    }

    @Override
    public void generateUnion(GenerateUnionDirective<ElixirContext, ElixirSettings> d) {
        d.context().writerDelegator().useShapeWriter(d.shape(), writer -> {
            writer.pushState(new UnionVariantsSection(d.shape()));
            writer.writeUnionModule(d.shape(), d.symbolProvider());
            writer.popState();
        });
    }

    @Override
    public void generateEnumShape(GenerateEnumDirective<ElixirContext, ElixirSettings> d) {
        EnumShape enumShape = d.expectEnumShape();
        d.context().writerDelegator().useShapeWriter(enumShape, writer -> {
            writer.pushState(new EnumValuesSection(enumShape));
            writer.writeEnumModule(enumShape, d.symbolProvider());
            writer.popState();
        });
    }

    @Override
    public void generateIntEnumShape(GenerateIntEnumDirective<ElixirContext, ElixirSettings> d) {
        IntEnumShape intEnumShape = d.expectIntEnumShape();
        d.context().writerDelegator().useShapeWriter(intEnumShape, writer ->
                writer.writeIntEnumModule(intEnumShape, d.symbolProvider()));
    }

    @Override
    public void customizeBeforeIntegrations(CustomizeDirective<ElixirContext, ElixirSettings> d) {
        // No-op: Elixir modules are closed immediately by writeDefModule.
        // This hook is available for integrations to add top-level module attributes.
    }

    /** Converts an operation shape's local name to a snake_case Elixir function name. */
    private static String toFunctionName(OperationShape op) {
        return ElixirReservedWords.MEMBER_NAMES.escape(
                CaseUtils.toSnakeCase(op.getId().getName()));
    }
}
