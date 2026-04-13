package io.smithy.beam.core.pipeline;

import io.smithy.beam.core.ir.EnumSpec;
import io.smithy.beam.core.ir.ModuleTypeSpec;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.Role;
import io.smithy.beam.core.ir.StructSpec;
import io.smithy.beam.core.ir.UnionSpec;
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

        output.write(outDir + "/" + base + "_handler"    + ext, buildHandler(base, service, ops, types, writer));
        output.write(outDir + "/" + base + "_router"     + ext, buildRouter(base, service, ops, writer));
        output.write(outDir + "/" + base + "_dispatcher" + ext, buildDispatcher(base, service, ops, writer));
        output.writeIfAbsent(
                settings.scaffoldDir().replace('\\', '/'),
                base + "_impl" + ext,
                buildImplScaffold(base, service, ops, types, writer));

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
     * Builds the {@code <svc>_handler.erl} behaviour module.
     *
     * <p>Contains type definitions for all reachable shapes and one
     * {@code -callback} per operation.
     */
    private String buildHandler(
            String baseName, ServiceShape service, List<OperationSpec> ops,
            ModuleTypeSpec types, LanguageWriter writer) {

        StringBuilder buf = new StringBuilder();
        buf.append(writer.moduleHeader(baseName + "_handler"));
        buf.append(writer.renderModuleComment(
                "Behaviour definition for " + service.getId().getName() + " server implementations."));
        buf.append("\n");

        // Type definitions — identical set to the client module for self-contained use.
        for (StructSpec s : types.structures()) buf.append(writer.renderStructType(s));
        for (EnumSpec   e : types.enums())       buf.append(writer.renderEnumType(e));
        for (UnionSpec  u : types.unions())      buf.append(writer.renderUnionType(u));
        for (StructSpec e : types.errors())      buf.append(writer.renderStructType(e));

        buf.append("\n");

        for (OperationSpec op : ops) {
            buf.append(writer.renderServerCallbackDeclaration(op));
        }
        return buf.toString();
    }

    /**
     * Builds the {@code <svc>_router.erl} module.
     *
     * <p>Exports {@code route/2} with one clause per operation and a catch-all
     * {@code {error, not_found}} clause.
     */
    private String buildRouter(
            String baseName, ServiceShape service, List<OperationSpec> ops, LanguageWriter writer) {

        StringBuilder buf = new StringBuilder();
        buf.append(writer.moduleHeader(baseName + "_router"));
        buf.append(writer.renderModuleComment(
                "Request router for " + service.getId().getName() + "."));
        buf.append(writer.exportSection(List.of(new ExportSpec("route", 2))));
        buf.append("\n");

        for (OperationSpec op : ops) {
            buf.append(writer.renderServerRouteClause(op));
        }
        buf.append(writer.renderServerRouteFallback());
        return buf.toString();
    }

    /**
     * Builds the {@code <svc>_dispatcher.erl} module.
     *
     * <p>Exports {@code handle/3} which routes the request and dispatches to per-operation
     * private helpers that deserialize input, call the implementation, and serialize output.
     */
    private String buildDispatcher(
            String baseName, ServiceShape service, List<OperationSpec> ops, LanguageWriter writer) {

        StringBuilder buf = new StringBuilder();
        buf.append(writer.moduleHeader(baseName + "_dispatcher"));
        buf.append(writer.renderModuleComment(
                "Request dispatcher for " + service.getId().getName() + "."));
        buf.append(writer.exportSection(List.of(new ExportSpec("handle", 3))));
        buf.append("\n");

        buf.append(writer.renderServerHandleFunction(ops, baseName));

        for (OperationSpec op : ops) {
            buf.append("\n");
            buf.append(writer.renderServerDispatchClause(op));
            buf.append("\n");
            buf.append(writer.renderServerDeserialize(op));
            buf.append("\n");
            buf.append(writer.renderServerSerialize(op));
        }
        return buf.toString();
    }

    /**
     * Builds the {@code <svc>_impl.erl} stub scaffold (written once, never overwritten).
     *
     * <p>Declares the behaviour and provides one stub function per operation that
     * returns {@code {error, not_implemented}}.
     */
    private String buildImplScaffold(
            String baseName, ServiceShape service, List<OperationSpec> ops,
            ModuleTypeSpec types, LanguageWriter writer) {

        StringBuilder buf = new StringBuilder();
        buf.append(writer.moduleHeader(baseName + "_impl"));
        buf.append(writer.renderModuleComment(
                "This file will NOT be overwritten. Add your business logic here."));
        buf.append(writer.behaviourDeclaration(baseName + "_handler"));
        buf.append("\n");

        // Export one function per operation (arity 2: input map + context map).
        List<ExportSpec> exports = new ArrayList<>();
        for (OperationSpec op : ops) {
            exports.add(new ExportSpec(writer.functionName(op.operationName()), 2));
        }
        buf.append(writer.exportSection(exports));
        buf.append("\n");

        String handlerModuleName = baseName + "_handler";
        for (OperationSpec op : ops) {
            buf.append(writer.renderServerImplStub(op, handlerModuleName));
            buf.append("\n");
        }
        return buf.toString();
    }
}
