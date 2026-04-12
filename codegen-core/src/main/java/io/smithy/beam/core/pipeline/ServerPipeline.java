package io.smithy.beam.core.pipeline;

import io.smithy.beam.core.ir.ModuleTypeSpec;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.Role;
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
 * Orchestrates server-side generation: handler, router, dispatcher, and a once-written impl scaffold.
 */
public final class ServerPipeline {

    public void generate(
            ServiceShape service,
            Model model,
            ProtocolAnalyzer protocol,
            LanguageWriter writer,
            CodegenSettings settings,
            FileOutput output) {
        List<OperationSpec> ops = analyzeOperations(service, model, protocol, Role.SERVER);
        ModuleTypeSpec types = buildTypeSpec(service, model);

        String baseName = moduleBaseName(service, settings);
        String ext = writer.fileExtension();
        String outDir = settings.outputDir().replace('\\', '/');

        output.write(outDir + "/" + baseName + "_handler" + ext, buildHandler(service, ops, types, writer));
        output.write(outDir + "/" + baseName + "_router" + ext, buildRouter(service, ops, writer));
        output.write(outDir + "/" + baseName + "_dispatcher" + ext, buildDispatcher(service, ops, protocol, writer));
        output.writeIfAbsent(
                settings.scaffoldDir().replace('\\', '/'),
                baseName + "_impl" + ext,
                buildImplScaffold(service, ops, types, writer));
    }

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

    private String buildHandler(ServiceShape service, List<OperationSpec> ops, ModuleTypeSpec types, LanguageWriter writer) {
        return "";
    }

    private String buildRouter(ServiceShape service, List<OperationSpec> ops, LanguageWriter writer) {
        return "";
    }

    private String buildDispatcher(
            ServiceShape service, List<OperationSpec> ops, ProtocolAnalyzer protocol, LanguageWriter writer) {
        return "";
    }

    private String buildImplScaffold(
            ServiceShape service, List<OperationSpec> ops, ModuleTypeSpec types, LanguageWriter writer) {
        return "";
    }
}
