package io.smithy.beam.core.pipeline;

import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.BodySpec;
import io.smithy.beam.core.ir.EnumSpec;
import io.smithy.beam.core.ir.ErrorBinding;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.ErrorSpec;
import io.smithy.beam.core.ir.FieldSpec;
import io.smithy.beam.core.ir.HeaderBinding;
import io.smithy.beam.core.ir.LabelBinding;
import io.smithy.beam.core.ir.ModuleTypeSpec;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.QueryBinding;
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
        buf.append(writer.exportSection(buildExports(ops, types, writer)));
        buf.append(buildExportTypes(types, writer));
        buf.append("\n-dialyzer([no_contracts, no_match]).\n\n");

        for (StructSpec s : types.structures()) {
            buf.append(writer.renderStructType(s));
        }
        for (EnumSpec e : types.enums()) {
            buf.append(writer.renderEnumType(e));
        }
        for (UnionSpec u : types.unions()) {
            buf.append(writer.renderUnionType(u));
        }
        for (StructSpec e : types.errors()) {
            buf.append(writer.renderStructType(e));
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

        copyClientRuntime(ops, protocol, service, output, writer.languageId(), resourceLoader);
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
            List<OperationSpec> ops, ModuleTypeSpec types, LanguageWriter writer) {
        List<ExportSpec> exports = new ArrayList<>();

        // Client constructor
        exports.add(new ExportSpec("new", 1));

        // Public operation functions
        for (OperationSpec op : ops) {
            String name = writer.functionName(op.operationName());
            exports.add(new ExportSpec(name, 2));
            exports.add(new ExportSpec(name, 3));
        }

        // Enum encode/decode helpers
        for (EnumSpec e : types.enums()) {
            String baseName = writer.functionName(e.name());
            exports.add(new ExportSpec("encode_" + baseName, 1));
            exports.add(new ExportSpec("decode_" + baseName, 1));
        }

        // Union encode/decode helpers
        for (UnionSpec u : types.unions()) {
            String baseName = writer.functionName(u.name());
            exports.add(new ExportSpec("encode_" + baseName, 1));
            exports.add(new ExportSpec("decode_" + baseName, 1));
        }

        // Validate helpers for structs with required fields
        for (StructSpec s : types.structures()) {
            boolean hasRequired = s.fields().stream().anyMatch(FieldSpec::required);
            if (hasRequired) {
                exports.add(new ExportSpec("validate_" + writer.functionName(s.name()), 1));
            }
        }

        // parse_error/2 — always present for client modules
        exports.add(new ExportSpec("parse_error", 2));

        return exports;
    }

    /**
     * Builds an {@code -export_type([...])} attribute for all generated types, preventing
     * "type X is unused" compiler warnings when a type is only used internally.
     */
    private String buildExportTypes(ModuleTypeSpec types, LanguageWriter writer) {
        List<String> typeNames = new ArrayList<>();
        for (StructSpec s : types.structures()) {
            typeNames.add(writer.functionName(s.name()) + "/0");
        }
        for (EnumSpec e : types.enums()) {
            typeNames.add(writer.functionName(e.name()) + "/0");
        }
        for (UnionSpec u : types.unions()) {
            typeNames.add(writer.functionName(u.name()) + "/0");
        }
        for (StructSpec e : types.errors()) {
            typeNames.add(writer.functionName(e.name()) + "/0");
        }
        if (typeNames.isEmpty()) {
            return "";
        }
        return "-export_type([" + String.join(", ", typeNames) + "]).\n";
    }

    // -------------------------------------------------------------------------
    // Client constructor
    // -------------------------------------------------------------------------

    private String renderClientConstructor(LanguageWriter writer, CodegenSettings settings) {
        return "\n"
             + "-spec new(map()) -> {ok, map()}.\n"
             + "new(Config) ->\n"
             + "    {ok, Config}.\n";
    }

    // -------------------------------------------------------------------------
    // Operation function blocks
    // -------------------------------------------------------------------------

    private String renderOperation(OperationSpec op, LanguageWriter writer) {
        String opName    = writer.functionName(op.operationName());
        String inputType = writer.typeName(op.operationName() + "Input");
        String outType   = writer.typeName(op.operationName() + "Output");
        String makeOp    = "make_" + opName + "_request";

        StringBuilder sb = new StringBuilder();
        sb.append("\n");

        // 2-arity public wrapper
        sb.append("-spec ").append(opName).append("(map(), ").append(inputType).append(") ->\n");
        sb.append("    {ok, ").append(outType).append("} | {error, term()}.\n");
        sb.append(opName).append("(Client, Input) ->\n");
        sb.append("    ").append(opName).append("(Client, Input, #{}).\n\n");

        // 3-arity with options (retry wrapper)
        sb.append("-spec ").append(opName).append("(map(), ").append(inputType).append(", map()) ->\n");
        sb.append("    {ok, ").append(outType).append("} | {error, term()}.\n");
        sb.append(opName).append("(Client, Input, Options) when is_map(Input), is_map(Options) ->\n");
        sb.append("    RequestFun = fun() -> ").append(makeOp).append("(Client, Input) end,\n");
        sb.append("    case maps:get(enable_retry, Options, true) of\n");
        sb.append("        true -> aws_retry:with_retry(RequestFun, Options);\n");
        sb.append("        false -> RequestFun()\n");
        sb.append("    end.\n\n");

        // Internal make_X_request/2
        sb.append("-spec ").append(makeOp).append("(map(), ").append(inputType).append(") ->\n");
        sb.append("    {ok, ").append(outType).append("} | {error, term()}.\n");
        sb.append(makeOp).append("(Client, Input) when is_map(Input) ->\n");
        sb.append("    Method = <<\"").append(op.http().method()).append("\">>,\n");
        sb.append("    Endpoint = maps:get(endpoint, Client),\n");

        // Query string
        List<QueryBinding> queries = op.queries() != null ? op.queries() : List.of();
        if (queries.isEmpty()) {
            sb.append("    QueryString = <<>>,\n");
        } else {
            sb.append("    QsParams = [");
            for (int i = 0; i < queries.size(); i++) {
                QueryBinding q = queries.get(i);
                if (i > 0) sb.append(", ");
                sb.append("{<<\"").append(q.queryKey()).append("\">>, ")
                  .append("maps:get(<<\"").append(q.smithyMemberName()).append("\">>, Input, undefined)}");
            }
            sb.append("],\n");
            sb.append("    QsFiltered = [{K, V} || {K, V} <- QsParams, V =/= undefined],\n");
            sb.append("    QueryString = case QsFiltered of\n");
            sb.append("        [] -> <<>>;\n");
            sb.append("        _ -> <<\"?\", (uri_string:compose_query(QsFiltered))/binary>>\n");
            sb.append("    end,\n");
        }

        // URI with label substitution
        List<LabelBinding> labels = op.labels() != null ? op.labels() : List.of();
        if (labels.isEmpty()) {
            sb.append("    Uri = <<\"").append(op.http().uriTemplate()).append("\">>,\n");
        } else {
            sb.append("    Uri0 = <<\"").append(op.http().uriTemplate()).append("\">>,\n");
            for (int i = 0; i < labels.size(); i++) {
                LabelBinding label = labels.get(i);
                String varName = writer.varName(label.smithyMemberName());
                sb.append("    ").append(varName).append("Value = maps:get(<<\"")
                  .append(label.smithyMemberName()).append("\">>, Input),\n");
                sb.append("    ").append(varName).append("Encoded = url_encode(ensure_binary(")
                  .append(varName).append("Value)),\n");
                sb.append("    Uri").append(i + 1)
                  .append(" = binary:replace(Uri").append(i).append(", <<\"{")
                  .append(label.uriPlaceholder()).append("}\">>, ").append(varName).append("Encoded),\n");
            }
            sb.append("    Uri = Uri").append(labels.size()).append(",\n");
        }

        // URL
        sb.append("    Url = <<Endpoint/binary, Uri/binary, QueryString/binary>>,\n");

        // Body
        BodySpec body = op.body();
        boolean hasBodyMembers = body != null
                && body.encoding() != BodyEncoding.NONE
                && !body.bodyMemberNames().isEmpty();
        String contentType = resolveContentType(body);

        if (hasBodyMembers) {
            sb.append("    BodyMap = maps:filter(fun(_, V) -> V =/= undefined end, #{");
            List<String> members = body.bodyMemberNames();
            for (int i = 0; i < members.size(); i++) {
                String m = members.get(i);
                if (i > 0) sb.append(", ");
                sb.append("<<\"").append(m).append("\">> => maps:get(<<\"").append(m).append("\">>, Input, undefined)");
            }
            sb.append("}),\n");
            switch (body.encoding()) {
                case XML:
                    sb.append("    Body = aws_xml:encode(BodyMap, <<\"Body\">>),\n");
                    break;
                case FORM_URLENCODED:
                    sb.append("    Body = aws_query:encode(BodyMap),\n");
                    break;
                default:
                    sb.append("    Body = jsx:encode(BodyMap),\n");
            }
        } else if (op.auth().requiresSigV4()) {
            // Empty body still needs to be bound for the SigV4 signing call below.
            sb.append("    Body = <<>>,\n");
        }

        // Headers
        List<HeaderBinding> headerBindings = op.headers() != null ? op.headers() : List.of();
        sb.append("    Headers = [{<<\"Content-Type\">>, <<\"").append(contentType).append("\">>}");
        for (HeaderBinding h : headerBindings) {
            sb.append(",\n               {<<\"").append(h.headerName()).append("\">>, ")
              .append("maps:get(<<\"").append(h.smithyMemberName()).append("\">>, Input)}");
        }
        sb.append("],\n");

        // SigV4 auth
        if (op.auth().requiresSigV4()) {
            sb.append("    FinalHeaders = aws_sigv4:sign_request(Method, Url, Headers, Body, Client),\n");
        } else {
            sb.append("    FinalHeaders = Headers,\n");
        }

        // httpc request
        sb.append("    StringUrl = binary_to_list(Url),\n");
        sb.append("    StringHeaders = [{binary_to_list(K), binary_to_list(V)} || {K, V} <- FinalHeaders],\n");
        if (hasBodyMembers) {
            sb.append("    Request = {StringUrl, StringHeaders, \"").append(contentType).append("\", Body},\n");
        } else {
            sb.append("    Request = {StringUrl, StringHeaders},\n");
        }

        // Response handling
        sb.append("    case httpc:request(binary_to_atom(string:lowercase(Method), utf8), Request, [], [{body_format, binary}]) of\n");
        sb.append("        {ok, {{_, StatusCode, _}, _RespHeaders, ResponseBody}} when StatusCode >= 200, StatusCode < 300 ->\n");
        sb.append("            case ResponseBody of\n");
        sb.append("                <<>> -> {ok, #{}};\n");
        sb.append("                _ ->\n");
        sb.append("                    try jsx:decode(ResponseBody, [return_maps]) of\n");
        sb.append("                        DecodedBody -> {ok, DecodedBody}\n");
        sb.append("                    catch\n");
        sb.append("                        _:DecodeError -> {error, {json_decode_error, DecodeError}}\n");
        sb.append("                    end\n");
        sb.append("            end;\n");
        sb.append("        {ok, {{_, ErrStatusCode, _}, _RespHeaders, ErrorBody}} ->\n");
        sb.append("            parse_error(ErrStatusCode, ErrorBody);\n");
        sb.append("        {error, Reason} ->\n");
        sb.append("            {error, {http_error, Reason}}\n");
        sb.append("    end.\n");

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helper functions
    // -------------------------------------------------------------------------

    private String renderHelpers(ModuleTypeSpec types, List<OperationSpec> ops, LanguageWriter writer) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n%% ===================================================================\n");
        sb.append("%% Internal helpers\n");
        sb.append("%% ===================================================================\n\n");

        // url_encode/1
        sb.append("url_encode(Binary) when is_binary(Binary) ->\n");
        sb.append("    url_encode(binary_to_list(Binary));\n");
        sb.append("url_encode(String) when is_list(String) ->\n");
        sb.append("    list_to_binary(uri_string:quote(String)).\n\n");

        // ensure_binary/1
        sb.append("ensure_binary(Bin) when is_binary(Bin) -> Bin;\n");
        sb.append("ensure_binary(List) when is_list(List) -> list_to_binary(List);\n");
        sb.append("ensure_binary(Int) when is_integer(Int) -> integer_to_binary(Int);\n");
        sb.append("ensure_binary(Float) when is_float(Float) -> float_to_binary(Float);\n");
        sb.append("ensure_binary(Atom) when is_atom(Atom) -> atom_to_binary(Atom, utf8);\n");
        sb.append("ensure_binary(Other) -> list_to_binary(io_lib:format(\"~p\", [Other])).\n\n");

        // Enum encode/decode
        for (EnumSpec e : types.enums()) {
            sb.append(renderEnumHelpers(e, writer));
        }

        // Union encode/decode
        for (UnionSpec u : types.unions()) {
            sb.append(renderUnionHelpers(u, writer));
        }

        // Validate helpers for structs with required fields
        for (StructSpec s : types.structures()) {
            List<FieldSpec> required = s.fields().stream()
                    .filter(FieldSpec::required)
                    .collect(Collectors.toList());
            if (!required.isEmpty()) {
                sb.append(renderValidateHelper(s, writer));
            }
        }

        // parse_error/2 — aggregated from all operations, deduped by HTTP code
        sb.append(renderParseError(ops, writer));

        return sb.toString();
    }

    private String renderEnumHelpers(EnumSpec e, LanguageWriter writer) {
        String baseName = writer.functionName(e.name());
        String typeName = writer.typeName(e.name());
        StringBuilder sb = new StringBuilder();

        // encode_<enum>/1
        sb.append("-spec encode_").append(baseName).append("(").append(typeName).append(") -> binary().\n");
        for (String v : e.values()) {
            sb.append("encode_").append(baseName).append("('").append(v).append("') -> <<\"").append(v).append("\">>;\n");
        }
        sb.append("encode_").append(baseName).append("(Other) -> ensure_binary(Other).\n\n");

        // decode_<enum>/1
        sb.append("-spec decode_").append(baseName).append("(binary()) ->\n");
        sb.append("    {ok, ").append(typeName).append("} | {error, {invalid_enum_value, binary()}}.\n");
        for (String v : e.values()) {
            sb.append("decode_").append(baseName).append("(<<\"").append(v).append("\">>) -> {ok, '").append(v).append("'};\n");
        }
        sb.append("decode_").append(baseName).append("(Other) -> {error, {invalid_enum_value, Other}}.\n\n");

        return sb.toString();
    }

    private String renderUnionHelpers(UnionSpec u, LanguageWriter writer) {
        String baseName = writer.functionName(u.name());
        String typeName = writer.typeName(u.name());
        StringBuilder sb = new StringBuilder();

        // encode_<union>/1
        sb.append("-spec encode_").append(baseName).append("(").append(typeName).append(") -> map().\n");
        for (FieldSpec variant : u.variants()) {
            sb.append("encode_").append(baseName).append("({").append(variant.name()).append(", Value}) ->\n");
            sb.append("    #{<<\"").append(variant.name()).append("\">> => Value};\n");
        }
        sb.append("encode_").append(baseName).append("({unknown, Value}) ->\n");
        sb.append("    #{<<\"unknown\">> => Value}.\n\n");

        // decode_<union>/1 — nested maps:find
        sb.append("-spec decode_").append(baseName).append("(map()) -> ").append(typeName).append(".\n");
        sb.append("decode_").append(baseName).append("(Map) when is_map(Map) ->\n");
        renderUnionDecodeBody(sb, u.variants(), 0);
        sb.append(".\n\n");

        return sb.toString();
    }

    private void renderUnionDecodeBody(StringBuilder sb, List<FieldSpec> variants, int depth) {
        String indent = "    ".repeat(depth + 1);
        if (variants.isEmpty()) {
            sb.append(indent).append("{unknown, Map}");
            return;
        }
        FieldSpec head = variants.get(0);
        List<FieldSpec> tail = variants.subList(1, variants.size());
        sb.append(indent).append("case maps:find(<<\"").append(head.name()).append("\">>, Map) of\n");
        sb.append(indent).append("    {ok, Value} -> {").append(head.name()).append(", Value};\n");
        sb.append(indent).append("    error ->\n");
        renderUnionDecodeBody(sb, tail, depth + 1);
        sb.append("\n").append(indent).append("end");
    }

    private String renderValidateHelper(StructSpec s, LanguageWriter writer) {
        String funcName = "validate_" + writer.functionName(s.name());
        List<String> requiredFields = s.fields().stream()
                .filter(FieldSpec::required)
                .map(FieldSpec::name)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("-spec ").append(funcName).append("(map()) ->\n");
        sb.append("    ok | {error, {missing_required_fields, [binary()]}}.\n");
        sb.append(funcName).append("(Input) ->\n");
        sb.append("    RequiredFields = [");
        for (int i = 0; i < requiredFields.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("<<\"").append(requiredFields.get(i)).append("\">>");
        }
        sb.append("],\n");
        sb.append("    Missing = [F || F <- RequiredFields, not maps:is_key(F, Input)],\n");
        sb.append("    case Missing of\n");
        sb.append("        [] -> ok;\n");
        sb.append("        _ -> {error, {missing_required_fields, Missing}}\n");
        sb.append("    end.\n\n");

        return sb.toString();
    }

    private String renderParseError(List<OperationSpec> ops, LanguageWriter writer) {
        // Deduplicate error bindings by HTTP status code (first seen wins)
        Map<Integer, ErrorBinding> byCode = new LinkedHashMap<>();
        for (OperationSpec op : ops) {
            if (op.errors() != null) {
                for (ErrorBinding eb : op.errors().errors()) {
                    byCode.putIfAbsent(eb.httpCode(), eb);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("-spec parse_error(integer(), binary()) -> {error, term()}.\n");
        sb.append("parse_error(StatusCode, Body) ->\n");
        if (byCode.isEmpty()) {
            sb.append("    {error, {http_error, StatusCode, Body}}.\n");
        } else {
            ErrorSpec combined = new ErrorSpec(new ArrayList<>(byCode.values()), ErrorCodeStrategy.REST_JSON);
            // Inline the error dispatch rather than using writer.renderErrorSerializer
            // since the latter generates a full function; here we only want the body.
            sb.append("    case StatusCode of\n");
            for (ErrorBinding eb : byCode.values()) {
                String atom = writer.functionName(eb.smithyName());
                sb.append("        ").append(eb.httpCode())
                  .append(" -> {error, {").append(atom).append(", Body}};\n");
            }
            sb.append("        _ -> {error, {http_error, StatusCode, Body}}\n");
            sb.append("    end.\n");
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Runtime module copying
    // -------------------------------------------------------------------------

    private void copyClientRuntime(
            List<OperationSpec> ops,
            ProtocolAnalyzer protocol,
            ServiceShape service,
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
        if (protocol.requiresS3Runtime(service)) {
            output.copyRuntime(languageId, "client/aws_s3.erl", resourceLoader);
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static String resolveContentType(BodySpec body) {
        if (body == null) {
            return "application/json";
        }
        switch (body.encoding()) {
            case XML:            return "application/xml";
            case FORM_URLENCODED: return "application/x-www-form-urlencoded";
            default:             return "application/json";
        }
    }
}
