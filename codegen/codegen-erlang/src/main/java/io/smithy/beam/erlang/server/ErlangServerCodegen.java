package io.smithy.beam.erlang.server;

import io.smithy.beam.core.Mode;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangIntegration;
import io.smithy.beam.erlang.codegen.ErlangReservedWords;
import io.smithy.beam.erlang.codegen.ErlangSettings;
import io.smithy.beam.erlang.codegen.ErlangSymbolProvider;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import io.smithy.beam.erlang.codegen.sections.EnumValuesSection;
import io.smithy.beam.erlang.codegen.sections.ModuleAttributesSection;
import io.smithy.beam.erlang.codegen.sections.OperationDocSection;
import io.smithy.beam.erlang.codegen.sections.OperationSpecSection;
import io.smithy.beam.erlang.codegen.sections.OperationValidationSection;
import io.smithy.beam.erlang.codegen.sections.ServerDeserializeSection;
import io.smithy.beam.erlang.codegen.sections.ServerDispatchSection;
import io.smithy.beam.erlang.codegen.sections.ServerHandlerCallbackSection;
import io.smithy.beam.erlang.codegen.sections.ServerImplCallbackSection;
import io.smithy.beam.erlang.codegen.sections.ServerRouteSection;
import io.smithy.beam.erlang.codegen.sections.ServerSerializeSection;
import io.smithy.beam.erlang.codegen.sections.StructTypeSection;
import io.smithy.beam.erlang.codegen.sections.UnionVariantsSection;
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
 * {@link DirectedCodegen} implementation for the Erlang server plugin.
 *
 * <p>{@code S} is fixed to {@link ErlangSettings} (the base settings type) because
 * {@link ErlangContext} is typed on {@code ErlangSettings}. Server-specific
 * settings ({@link ErlangServerSettings}) are accessed via {@code (ErlangServerSettings)
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
public final class ErlangServerCodegen
        implements DirectedCodegen<ErlangContext, ErlangSettings, ErlangIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(CreateSymbolProviderDirective<ErlangSettings> d) {
        return SymbolProvider.cache(
                new ErlangSymbolProvider(d.model(), d.settings(), Mode.SERVER));
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
            // Empty injection point — ErlangServerBehaviourIntegration adds -behaviour(…) here.
            writer.injectSection(new ModuleAttributesSection(d.service()));
            // Empty injection point — router integrations can emit a dispatch table here.
            writer.injectSection(new ServerRouteSection(d.service()));
        });
    }

    /**
     * Emits per-operation server sections in the prescribed order:
     * doc, spec, dispatch, deserialize, validation, handler callback, serialize, impl callback.
     */
    @Override
    public void generateOperation(GenerateOperationDirective<ErlangContext, ErlangSettings> d) {
        String handlerName = "handle_" + toFunctionName(d.shape().getId().getName());
        d.context().writerDelegator().useShapeWriter(d.service(), writer -> {
            // Doc comment (empty by default).
            writer.injectSection(new OperationDocSection(d.shape()));
            // -spec line (empty by default; ErlangSpecIntegration populates).
            writer.injectSection(new OperationSpecSection(d.shape()));
            // route(Method, Path) clause (empty by default).
            writer.injectSection(new ServerDispatchSection(d.shape()));
            // deserialize_<op>/3 helper (empty by default).
            writer.injectSection(new ServerDeserializeSection(d.shape()));
            // validate_<op>_input/1 helper (empty by default).
            writer.injectSection(new OperationValidationSection(d.shape()));

            // Handler callback stub; protocol integrations replace the body.
            writer.pushState(new ServerHandlerCallbackSection(d.shape()));
            writer.write("$L(Req, State) ->", handlerName);
            writer.write("    {error, not_implemented}.");
            writer.popState();

            // serialize_<op>/1 helper (empty by default).
            writer.injectSection(new ServerSerializeSection(d.shape()));
            // -callback / @callback line (empty by default).
            writer.injectSection(new ServerImplCallbackSection(d.shape()));

            writer.write("");
            writer.addExport(handlerName, 2);
        });
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
        // Flush the accumulated export list now that all generateOperation() calls are done.
        d.context().writerDelegator().useShapeWriter(d.service(), ErlangWriter::flushExports);
    }

    /** Converts a shape's local name to a snake_case Erlang function name fragment. */
    private static String toFunctionName(String shapeName) {
        return ErlangReservedWords.MEMBER_NAMES.escape(CaseUtils.toSnakeCase(shapeName));
    }
}
