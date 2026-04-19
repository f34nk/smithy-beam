package io.smithy.beam.elixir.server;

import io.smithy.beam.core.Mode;
import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirIntegration;
import io.smithy.beam.elixir.codegen.ElixirReservedWords;
import io.smithy.beam.elixir.codegen.ElixirSettings;
import io.smithy.beam.elixir.codegen.ElixirSymbolProvider;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import io.smithy.beam.elixir.codegen.sections.EnumValuesSection;
import io.smithy.beam.elixir.codegen.sections.ModuleAttributesSection;
import io.smithy.beam.elixir.codegen.sections.OperationDocSection;
import io.smithy.beam.elixir.codegen.sections.ServerHandlerCallbackSection;
import io.smithy.beam.elixir.codegen.sections.ServerRouteSection;
import io.smithy.beam.elixir.codegen.sections.StructTypeSection;
import io.smithy.beam.elixir.codegen.sections.UnionVariantsSection;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.CreateContextDirective;
import software.amazon.smithy.codegen.core.directed.CreateSymbolProviderDirective;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.codegen.core.directed.DirectedCodegen;
import software.amazon.smithy.codegen.core.directed.GenerateEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateErrorDirective;
import software.amazon.smithy.codegen.core.directed.GenerateIntEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.utils.CaseUtils;

/**
 * {@link DirectedCodegen} implementation for the Elixir server plugin.
 *
 * <p>{@code S} is fixed to {@link ElixirSettings} (the base settings type) because
 * {@link ElixirContext} is typed on {@code ElixirSettings}. Server-specific
 * settings ({@link ElixirServerSettings}) are accessed via {@code (ElixirServerSettings)
 * d.context().settings()} inside methods that need them.
 *
 * <p>Key differences from the client codegen:
 * <ul>
 *   <li>{@link #generateService} emits a {@code ServerRouteSection} injection point instead
 *       of inlining operation function bodies.</li>
 *   <li>{@link #generateOperation} emits a {@code handle_<op>/2} callback stub per
 *       operation, wrapped by {@link ServerHandlerCallbackSection} so protocol
 *       integrations can replace the stub body.</li>
 * </ul>
 */
public final class ElixirServerCodegen
        implements DirectedCodegen<ElixirContext, ElixirSettings, ElixirIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(CreateSymbolProviderDirective<ElixirSettings> d) {
        return SymbolProvider.cache(
                new ElixirSymbolProvider(d.model(), d.settings(), Mode.SERVER));
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
            writer.writeDefModule(namespace + ".Server", () -> {
                // Empty injection point — ElixirServerBehaviourIntegration adds @behaviour here.
                writer.injectSection(new ModuleAttributesSection(d.service()));
                // Empty injection point — router integrations can emit a dispatch table here.
                writer.injectSection(new ServerRouteSection(d.service()));

                for (var op : d.operations()) {
                    String handlerName = "handle_" + toFunctionName(op.getId().getName());
                    writer.injectSection(new OperationDocSection(op));
                    writer.pushState(new ServerHandlerCallbackSection(op));
                    writer.write("def $L(request, state) do", handlerName);
                    writer.indent();
                    writer.write("{:error, :not_implemented}");
                    writer.dedent();
                    writer.write("end");
                    writer.popState();
                    writer.write("");
                }
            });
        });
    }

    /**
     * No-op: handler stubs are emitted inside {@link #generateService} so they
     * appear within the {@code defmodule} block.
     */
    @Override
    public void generateOperation(GenerateOperationDirective<ElixirContext, ElixirSettings> d) {
        // Intentionally empty — see generateService.
    }

    @Override
    public void generateStructure(GenerateStructureDirective<ElixirContext, ElixirSettings> d) {
        d.context().writerDelegator().useShapeWriter(d.shape(), writer -> {
            writer.pushState(new StructTypeSection(d.shape()));
            writer.writeStructModule(d.shape(), d.symbolProvider());
            writer.popState();
        });
    }

    @Override
    public void generateError(GenerateErrorDirective<ElixirContext, ElixirSettings> d) {
        d.context().writerDelegator().useShapeWriter(d.shape(), writer -> {
            writer.pushState(new StructTypeSection(d.shape()));
            writer.writeStructModule(d.shape(), d.symbolProvider());
            writer.popState();
        });
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
    }

    /** Converts a shape's local name to a snake_case Elixir function name fragment. */
    private static String toFunctionName(String shapeName) {
        return ElixirReservedWords.MEMBER_NAMES.escape(CaseUtils.toSnakeCase(shapeName));
    }
}
