package io.smithy.beam.core.pipeline;

import io.smithy.beam.core.ir.EnumSpec;
import io.smithy.beam.core.ir.ModuleTypeSpec;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.Role;
import io.smithy.beam.core.ir.StructSpec;
import io.smithy.beam.core.ir.UnionSpec;
import io.smithy.beam.core.model.ShapeIndex;
import io.smithy.beam.core.model.TypeSpecBuilder;
import io.smithy.beam.core.output.CodeBuffer;
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
 * Orchestrates client SDK generation for one service: IR, render via {@link LanguageWriter}, one primary
 * module file plus optional runtime copies.
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

        String moduleName = moduleBaseName(service, settings);
        CodeBuffer buf = new CodeBuffer();
        buf.append(writer.moduleHeader(moduleName));
        buf.append(writer.exportSection(buildExports(ops, types)));

        for (StructSpec s : types.structures()) {
            buf.append(writer.renderStructType(s));
        }
        for (EnumSpec e : types.enums()) {
            buf.append(writer.renderEnumType(e));
        }
        for (UnionSpec u : types.unions()) {
            buf.append(writer.renderUnionType(u));
        }

        buf.append(renderClientConstructor(writer, settings));
        for (OperationSpec op : ops) {
            buf.append(renderOperation(op, writer));
            if (op.pagination() != null) {
                buf.append(writer.renderPaginationHelper(op, op.pagination()));
            }
        }
        buf.append(renderHelpers(types, ops, writer));
        buf.append(writer.moduleFooter());

        String outPath = settings.outputDir() + "/" + moduleName + writer.fileExtension();
        output.write(outPath.replace('\\', '/'), buf.toString());

        copyClientRuntime(ops, protocol, output, writer.languageId(), resourceLoader);
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

    private List<ExportSpec> buildExports(List<OperationSpec> ops, ModuleTypeSpec types) {
        return List.of();
    }

    private String renderClientConstructor(LanguageWriter writer, CodegenSettings settings) {
        return "";
    }

    private String renderOperation(OperationSpec op, LanguageWriter writer) {
        return "";
    }

    private String renderHelpers(ModuleTypeSpec types, List<OperationSpec> ops, LanguageWriter writer) {
        return "";
    }

    private void copyClientRuntime(
            List<OperationSpec> ops,
            ProtocolAnalyzer protocol,
            FileOutput output,
            String languageId,
            ClassLoader resourceLoader) {
        boolean needsSigV4 = ops.stream().anyMatch(o -> o.auth().requiresSigV4());
        if (needsSigV4) {
            output.copyRuntime(languageId, "client/aws_sigv4.erl", resourceLoader);
            output.copyRuntime(languageId, "client/aws_credentials.erl", resourceLoader);
        }
        output.copyRuntime(languageId, "client/aws_retry.erl", resourceLoader);
        output.copyRuntime(languageId, "client/aws_config.erl", resourceLoader);
        if (protocol.requiresXmlRuntime()) {
            output.copyRuntime(languageId, "client/aws_xml.erl", resourceLoader);
        }
        if (protocol.requiresQueryRuntime()) {
            output.copyRuntime(languageId, "client/aws_query.erl", resourceLoader);
        }
    }
}
