package io.smithy.beam.core.pipeline;

import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.ModuleTypeSpec;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.Role;
import io.smithy.beam.core.model.ModelValidator;
import io.smithy.beam.core.model.ShapeIndex;
import io.smithy.beam.core.model.TypeSpecBuilder;
import io.smithy.beam.core.output.FileOutput;
import io.smithy.beam.core.protocol.ProtocolAnalyzer;
import io.smithy.beam.core.settings.CodegenSettings;
import io.smithy.beam.core.writer.LanguageWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates client SDK generation for one service: IR, render via {@link LanguageWriter}, one primary
 * module file plus optional runtime copies.
 *
 * <p>Contains zero target-language string literals — all source-text emission is
 * delegated to {@link LanguageWriter}.
 */
public final class ClientPipeline {

    public void generate(
            ServiceShape service,
            Model model,
            ProtocolAnalyzer protocol,
            LanguageWriter writer,
            CodegenSettings settings,
            FileOutput output) {
        generate(service, model, protocol, writer, settings, output, ClassLoader.getSystemClassLoader());
    }

    public void generate(
            ServiceShape service,
            Model model,
            ProtocolAnalyzer protocol,
            LanguageWriter writer,
            CodegenSettings settings,
            FileOutput output,
            ClassLoader resourceLoader) {

        ModuleTypeSpec types = buildTypeSpec(service, model);
        List<OperationSpec> ops = analyzeOperations(service, model, protocol, Role.CLIENT);
        ModelValidator.validate(service, types, ops);
        String moduleName = moduleBaseName(service, settings);

        boolean needsSigV4 = ops.stream().anyMatch(o -> o.auth().requiresSigV4());
        boolean needsXml   = protocol.requiresXmlRuntime();
        boolean needsQuery = protocol.requiresQueryRuntime();
        boolean needsS3    = protocol.requiresS3Runtime(service);

        // Dominant error strategy — derived from the protocol analyzer so it is
        // available even when the ops list is empty.
        ErrorCodeStrategy errorStrategy = protocol.errorStrategy(service);

        // ── Delegate all text assembly to the writer ───────────────────────────
        String moduleSrc = writer.renderClientModule(moduleName, ops, types, errorStrategy);
        String outPath   = settings.outputDir() + "/" + moduleName + writer.fileExtension();
        output.write(outPath.replace('\\', '/'), moduleSrc);

        // ── Copy runtime modules ───────────────────────────────────────────────
        for (String path : writer.clientRuntimeModules(needsSigV4, needsXml, needsQuery, needsS3)) {
            output.copyRuntime(writer.languageId(), path, resourceLoader);
        }
    }

    // -------------------------------------------------------------------------
    // IR assembly
    // -------------------------------------------------------------------------

    private ModuleTypeSpec buildTypeSpec(ServiceShape service, Model model) {
        Set<Shape> reachable = ShapeIndex.reachable(service, model);
        return TypeSpecBuilder.build(service, model, reachable);
    }

    private static List<OperationSpec> analyzeOperations(
            ServiceShape service, Model model, ProtocolAnalyzer protocol, Role role) {
        List<OperationSpec> specs = new ArrayList<>();
        for (OperationShape op : TopDownIndex.of(model).getContainedOperations(service)) {
            if (role == Role.CLIENT) {
                specs.add(protocol.analyzeClientOperation(op, model, service));
            } else {
                specs.add(protocol.analyzeServerOperation(op, model, service));
            }
        }
        return specs;
    }

    private static String moduleBaseName(ServiceShape service, CodegenSettings settings) {
        return settings.moduleName().orElse(service.getId().getName());
    }

}
