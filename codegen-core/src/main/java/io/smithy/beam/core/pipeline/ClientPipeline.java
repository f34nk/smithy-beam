package io.smithy.beam.core.pipeline;

import io.smithy.beam.core.ir.EnumSpec;
import io.smithy.beam.core.ir.ErrorBinding;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.FieldSpec;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        String moduleName = moduleBaseName(service, settings);

        boolean needsSigV4 = ops.stream().anyMatch(o -> o.auth().requiresSigV4());
        boolean needsXml   = protocol.requiresXmlRuntime();
        boolean needsQuery = protocol.requiresQueryRuntime();
        boolean needsS3    = protocol.requiresS3Runtime(service);

        // Dominant error strategy — derived from the first operation's protocol strategy.
        ErrorCodeStrategy errorStrategy = ops.isEmpty()
                ? ErrorCodeStrategy.REST_JSON
                : ops.get(0).protocolErrorStrategy();

        // Collect operation input type names to restrict validate_ generation to callsite types.
        Set<String> inputTypeNames = ops.stream()
                .filter(o -> o.inputTypeName() != null)
                .map(OperationSpec::inputTypeName)
                .collect(Collectors.toSet());

        CodeBuffer buf = new CodeBuffer();

        // ── Module header ──────────────────────────────────────────────────────
        buf.append(writer.moduleHeader(moduleName));
        buf.append(writer.renderModuleComment("Generated Smithy client for " + service.getId().getName()));
        buf.append(writer.exportSection(buildExports(ops, types, writer, inputTypeNames)));
        buf.append(writer.exportTypes(buildExportTypeNames(types, writer)));
        buf.append(writer.renderToolingAttributes());

        // ── Type definitions ───────────────────────────────────────────────────
        for (StructSpec s : types.structures()) buf.append(writer.renderStructType(s));
        for (EnumSpec e : types.enums())        buf.append(writer.renderEnumType(e));
        for (UnionSpec u : types.unions())      buf.append(writer.renderUnionType(u));
        for (StructSpec e : types.errors())     buf.append(writer.renderStructType(e));

        // ── Enum / union codecs (encode_*/decode_*) — placed before operations
        // so every codec is defined before any operation that might call it.
        for (EnumSpec e : types.enums())        buf.append(writer.renderEnumCodec(e));
        for (UnionSpec u : types.unions())      buf.append(writer.renderUnionCodec(u));

        // ── Client constructor + shared helpers (url_encode, ensure_binary) ───
        buf.append(writer.renderClientConstructor());
        buf.append(writer.renderSharedHelpers());

        // ── Operations ────────────────────────────────────────────────────────
        // Pagination helpers are emitted by renderClientOperation itself.
        for (OperationSpec op : ops) {
            buf.append(writer.renderClientOperation(op));
        }

        // ── Validation helpers (only for operation input types) ───────────────
        for (StructSpec s : types.structures()) {
            if (inputTypeNames.contains(s.name())) {
                buf.append(writer.renderValidateHelper(s));
            }
        }
        buf.append(writer.renderModuleParseError(aggregateErrors(ops), errorStrategy));

        // ── Footer ────────────────────────────────────────────────────────────
        buf.append(writer.moduleFooter());

        // ── Write file ────────────────────────────────────────────────────────
        String outPath = settings.outputDir() + "/" + moduleName + writer.fileExtension();
        output.write(outPath.replace('\\', '/'), buf.toString());

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

    // -------------------------------------------------------------------------
    // Export section
    // -------------------------------------------------------------------------

    private List<ExportSpec> buildExports(
            List<OperationSpec> ops, ModuleTypeSpec types, LanguageWriter writer,
            Set<String> inputTypeNames) {
        List<ExportSpec> exports = new ArrayList<>();
        exports.add(new ExportSpec("new", 1));
        for (OperationSpec op : ops) {
            String name = writer.functionName(op.operationName());
            exports.add(new ExportSpec(name, 2));
            exports.add(new ExportSpec(name, 3));
        }
        for (EnumSpec e : types.enums()) {
            String baseName = writer.functionName(e.name());
            exports.add(new ExportSpec("encode_" + baseName, 1));
            exports.add(new ExportSpec("decode_" + baseName, 1));
        }
        for (UnionSpec u : types.unions()) {
            String baseName = writer.functionName(u.name());
            exports.add(new ExportSpec("encode_" + baseName, 1));
            exports.add(new ExportSpec("decode_" + baseName, 1));
        }
        // Only export validate_ for operation input types, not for every struct.
        for (StructSpec s : types.structures()) {
            if (inputTypeNames.contains(s.name()) && s.fields().stream().anyMatch(FieldSpec::required)) {
                exports.add(new ExportSpec("validate_" + writer.functionName(s.name()), 1));
            }
        }
        exports.add(new ExportSpec("parse_error", 2));
        return exports;
    }

    private List<String> buildExportTypeNames(ModuleTypeSpec types, LanguageWriter writer) {
        List<String> names = new ArrayList<>();
        for (StructSpec s : types.structures()) names.add(writer.functionName(s.name()) + "/0");
        for (EnumSpec e : types.enums())        names.add(writer.functionName(e.name()) + "/0");
        for (UnionSpec u : types.unions())      names.add(writer.functionName(u.name()) + "/0");
        for (StructSpec e : types.errors())     names.add(writer.functionName(e.name()) + "/0");
        return names;
    }

    // -------------------------------------------------------------------------
    // Error aggregation
    // -------------------------------------------------------------------------

    /**
     * Collects all error bindings across operations, deduplicated by Smithy name.
     *
     * <p>Deduplication is intentionally by name (not HTTP status code) because
     * multiple distinct errors can share the same HTTP status code. The generated
     * {@code parse_error/2} dispatches on the error code string extracted from the
     * XML response body, so each Smithy error name must appear exactly once.
     */
    private static List<ErrorBinding> aggregateErrors(List<OperationSpec> ops) {
        Map<String, ErrorBinding> byName = new LinkedHashMap<>();
        for (OperationSpec op : ops) {
            if (op.errors() != null) {
                for (ErrorBinding eb : op.errors().errors()) {
                    byName.putIfAbsent(eb.smithyName(), eb);
                }
            }
        }
        return new ArrayList<>(byName.values());
    }
}
