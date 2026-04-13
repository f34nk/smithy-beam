package io.smithy.beam.core.pipeline;

import io.smithy.beam.core.ir.ModuleTypeSpec;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.Role;
import io.smithy.beam.core.model.ShapeIndex;
import io.smithy.beam.core.model.TypeSpecBuilder;
import io.smithy.beam.core.output.FileOutput;
import io.smithy.beam.core.protocol.ProtocolAnalyzer;
import io.smithy.beam.core.settings.CodegenSettings;
import io.smithy.beam.core.writer.ExportSpec;
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
 * Orchestrates server-side generation: handler behaviour, router, dispatcher, and a
 * once-written impl scaffold.
 *
 * <p>Contains zero target-language string literals — all source-text emission is
 * delegated to {@link LanguageWriter}.
 */
public final class ServerPipeline {

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

        List<OperationSpec> ops    = analyzeOperations(service, model, protocol, Role.SERVER);
        ModuleTypeSpec      types  = buildTypeSpec(service, model);
        String              base   = moduleBaseName(service, settings);
        String              ext    = writer.fileExtension();
        String              outDir = settings.outputDir().replace('\\', '/');

        String server = writer.renderServerModule(base, ops, types);
        if (!server.isEmpty()) {
            output.write(outDir + "/" + base + "_server" + ext, server);
        }
        output.writeIfAbsent(
                settings.scaffoldDir().replace('\\', '/'),
                base + "_impl" + ext,
                buildImplScaffold(base, ops, writer));

        for (String path : writer.serverRuntimeModules()) {
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

    // -------------------------------------------------------------------------
    // File builders
    // -------------------------------------------------------------------------

    /**
     * Builds the {@code <svc>_impl.erl} stub scaffold (written once, never overwritten).
     *
     * <p>Declares the behaviour and provides one stub function per operation that
     * returns {@code {error, not_implemented}}.
     */
    private String buildImplScaffold(
            String baseName, List<OperationSpec> ops,
            LanguageWriter writer) {

        StringBuilder buf = new StringBuilder();
        buf.append(writer.moduleHeader(baseName + "_impl"));
        buf.append(writer.renderModuleComment(
                "This file will NOT be overwritten. Add your business logic here."));
        buf.append(writer.behaviourDeclaration(baseName + "_server"));
        buf.append("\n");

        // Export one function per operation (arity 2: input map + context map).
        List<ExportSpec> exports = new ArrayList<>();
        for (OperationSpec op : ops) {
            exports.add(new ExportSpec(writer.functionName(op.operationName()), 2));
        }
        buf.append(writer.exportSection(exports));
        buf.append("\n");

        String handlerModuleName = baseName + "_server";
        for (OperationSpec op : ops) {
            buf.append(writer.renderServerImplStub(op, handlerModuleName));
            buf.append("\n");
        }
        return buf.toString();
    }
}
