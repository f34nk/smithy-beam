package io.smithy.beam.erlang.writer;

import io.smithy.beam.core.ir.AuthSpec;
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
import io.smithy.beam.core.ir.PaginationSpec;
import io.smithy.beam.core.ir.PrimitiveKind;
import io.smithy.beam.core.ir.QueryBinding;
import io.smithy.beam.core.ir.RetrySpec;
import io.smithy.beam.core.ir.StructSpec;
import io.smithy.beam.core.ir.TypeRef;
import io.smithy.beam.core.ir.UnionSpec;
import io.smithy.beam.core.writer.ExportSpec;
import io.smithy.beam.core.writer.LanguageWriter;
import io.smithy.beam.core.writer.MapEntrySpec;
import io.smithy.beam.core.writer.ParamSpec;
import io.smithy.beam.erlang.symbol.ErlangSymbolProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Erlang code emitter implementing {@link LanguageWriter}.
 *
 * <p>All methods return pure Erlang source text that the pipeline assembles
 * into {@code .erl} files. No mutable writer state is held — each call is
 * side-effect-free.
 */
public final class ErlangWriter implements LanguageWriter {

    // -------------------------------------------------------------------------
    // Step 9.2 — identity, naming, module structure
    // -------------------------------------------------------------------------

    @Override
    public String languageId() {
        return "erlang";
    }

    @Override
    public String fileExtension() {
        return ".erl";
    }

    @Override
    public String moduleName(String smithyName) {
        return ErlangSymbolProvider.toModuleName(smithyName);
    }

    @Override
    public String functionName(String smithyName) {
        return ErlangSymbolProvider.toFunctionName(smithyName);
    }

    @Override
    public String typeName(String smithyName) {
        return ErlangSymbolProvider.toTypeName(smithyName);
    }

    @Override
    public String varName(String smithyName) {
        return ErlangSymbolProvider.toVarName(smithyName);
    }

    /**
     * Converts a Smithy member name to an Erlang binary map key.
     *
     * <p>Example: {@code "cityId" → "<<\"cityId\">>"}
     */
    @Override
    public String mapKey(String smithyMemberName) {
        return "<<\"" + smithyMemberName + "\">>";
    }

    /**
     * Generates the Erlang module header.
     *
     * <p>Example output:
     * <pre>
     * -module(weather_service).
     * </pre>
     */
    @Override
    public String moduleHeader(String name) {
        return "-module(" + ErlangSymbolProvider.toModuleName(name) + ").\n";
    }

    /** Erlang has no module footer; returns an empty string. */
    @Override
    public String moduleFooter() {
        return "";
    }

    /**
     * Returns a module-level Erlang comment followed by a blank line.
     *
     * <p>Example: {@code "%% Generated Smithy client for AmazonS3\n\n"}
     */
    @Override
    public String renderModuleComment(String text) {
        return "\n%% " + text + "\n";
    }

    /**
     * Generates a multiline {@code -export([...])} attribute, one entry per line.
     *
     * <p>Example output:
     * <pre>
     * -export([
     *     get_weather/2,
     *     new/1
     * ]).
     * </pre>
     */
    @Override
    public String exportSection(List<ExportSpec> exports) {
        if (exports.isEmpty()) {
            return "-export([]).\n";
        }
        StringBuilder sb = new StringBuilder("-export([\n");
        for (int i = 0; i < exports.size(); i++) {
            ExportSpec e = exports.get(i);
            String comma = (i < exports.size() - 1) ? "," : "";
            sb.append("    ").append(e.functionName()).append("/").append(e.arity())
              .append(comma).append("\n");
        }
        sb.append("]).\n");
        return sb.toString();
    }

    /**
     * Generates a {@code -behaviour(...)} attribute.
     *
     * <p>Example output:
     * <pre>
     * -behaviour(weather_service_handler).
     * </pre>
     */
    @Override
    public String behaviourDeclaration(String behaviourName) {
        return "-behaviour(" + ErlangSymbolProvider.toModuleName(behaviourName) + ").\n";
    }

    // -------------------------------------------------------------------------
    // Step 9.3 — type rendering
    // -------------------------------------------------------------------------

    /**
     * Renders an Erlang map type for a Smithy structure.
     *
     * <p>All fields use {@code =>} (optional presence); required fields are
     * distinguished at the call site via validation, not in the type spec.
     * Optional fields omit {@code | undefined} — the {@code =>} operator
     * already implies the key may be absent.
     *
     * <p>Example output:
     * <pre>
     * -type get_weather_input() :: #{
     *     city => binary(),
     *     unit => temperature_unit()
     * }.
     * </pre>
     */
    @Override
    public String renderStructType(StructSpec struct) {
        String typeName = ErlangSymbolProvider.toSafeSnakeCase(struct.name());
        if (struct.fields().isEmpty()) {
            return "-type " + typeName + "() :: #{}.\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("-type ").append(typeName).append("() :: #{\n");
        List<FieldSpec> fields = struct.fields();
        for (int i = 0; i < fields.size(); i++) {
            FieldSpec f = fields.get(i);
            String comma = (i < fields.size() - 1) ? "," : "";
            // Use => for all fields; required annotation lives in validate_* helpers
            sb.append("    ").append(ErlangSymbolProvider.toAtomTag(f.name())).append(" => ")
              .append(typeRefToErlang(f.type()))
              .append(comma).append("\n");
        }
        sb.append("}.\n");
        return sb.toString();
    }

    /**
     * Renders an Erlang union type for a Smithy enum, using lowercase atoms.
     *
     * <p>Example output:
     * <pre>
     * -type temperature_unit() :: celsius | fahrenheit.
     * </pre>
     */
    @Override
    public String renderEnumType(EnumSpec e) {
        String typeName = ErlangSymbolProvider.toSafeSnakeCase(e.name());
        String variants = e.values().stream()
            .map(ErlangWriter::toErlangAtom)
            .collect(Collectors.joining(" | "));
        return "-type " + typeName + "() :: " + variants + ".\n";
    }

    /**
     * Renders an Erlang union type for a Smithy union.
     *
     * <p>Example output:
     * <pre>
     * -type result() :: {ok, success_output()} | {error, failure_output()}.
     * </pre>
     */
    @Override
    public String renderUnionType(UnionSpec u) {
        String typeName = ErlangSymbolProvider.toSafeSnakeCase(u.name());
        String variants = u.variants().stream()
            .map(f -> "{" + ErlangSymbolProvider.toAtomTag(f.name()) + ", " + typeRefToErlang(f.type()) + "}")
            .collect(Collectors.joining(" | "));
        return "-type " + typeName + "() :: " + variants + ".\n";
    }

    /**
     * Renders an Erlang {@code -callback} declaration.
     *
     * <p>Example output:
     * <pre>
     * -callback get_weather(Input :: get_weather_input(), Context :: map()) ->
     *     {ok, get_weather_output()} | {error, term()}.
     * </pre>
     */
    @Override
    public String renderCallbackDeclaration(String name, List<ParamSpec> params, TypeRef returnType) {
        String funcName = ErlangSymbolProvider.toFunctionName(name);
        String paramStr = params.stream()
            .map(p -> ErlangSymbolProvider.toVarName(p.name()) + " :: " + typeRefToErlang(p.type()))
            .collect(Collectors.joining(", "));
        return "-callback " + funcName + "(" + paramStr + ") ->\n"
             + "    " + typeRefToErlang(returnType) + ".\n";
    }

    /**
     * Renders an Erlang {@code -spec} declaration with unnamed parameters.
     *
     * <p>Example output:
     * <pre>
     * -spec get_weather(get_weather_input(), map()) -> {ok, get_weather_output()} | {error, term()}.
     * </pre>
     */
    @Override
    public String renderFunctionSpec(String name, List<ParamSpec> params, TypeRef returnType) {
        String funcName = ErlangSymbolProvider.toFunctionName(name);
        String paramStr = params.stream()
            .map(p -> typeRefToErlang(p.type()))
            .collect(Collectors.joining(", "));
        return "-spec " + funcName + "(" + paramStr + ") -> " + typeRefToErlang(returnType) + ".\n";
    }

    /**
     * Renders the opening line of a function clause (head + {@code ->}).
     *
     * <p>Example output: {@code get_weather(Input, Config) ->}
     */
    @Override
    public String renderFunctionHead(String name, List<String> paramPatterns) {
        String funcName = ErlangSymbolProvider.toFunctionName(name);
        String params = String.join(", ", paramPatterns);
        return funcName + "(" + params + ") ->";
    }

    /** Returns {@code "."} — the Erlang function terminator. */
    @Override
    public String renderFunctionEnd() {
        return ".";
    }

    // -------------------------------------------------------------------------
    // Step 10.1 — Map operations
    // -------------------------------------------------------------------------

    /**
     * Renders a {@code maps:get/3} call.
     *
     * <p>Example: {@code maps:get(<<"CityId">>, Input, undefined)}
     */
    @Override
    public String renderMapGet(String mapVar, String smithyMemberName, String defaultVal) {
        return "maps:get(<<\"" + smithyMemberName + "\">>, " + mapVar + ", " + defaultVal + ")";
    }

    /**
     * Renders a map literal.
     *
     * <p>Example: {@code #{<<"city">> => City, <<"unit">> => Unit}}
     */
    @Override
    public String renderMapBuild(List<MapEntrySpec> entries) {
        if (entries.isEmpty()) {
            return "#{}";
        }
        String body = entries.stream()
            .map(e -> "<<\"" + e.key() + "\">> => " + e.valueExpr())
            .collect(Collectors.joining(", "));
        return "#{" + body + "}";
    }

    // -------------------------------------------------------------------------
    // Step 10.2 — JSON and XML
    // -------------------------------------------------------------------------

    /** Example: {@code jsx:encode(BodyMap)} */
    @Override
    public String renderJsonEncode(String mapVar) {
        return "jsx:encode(" + mapVar + ")";
    }

    /** Example: {@code jsx:decode(Body, [return_maps])} */
    @Override
    public String renderJsonDecode(String bodyVar) {
        return "jsx:decode(" + bodyVar + ", [return_maps])";
    }

    /** Example: {@code smithy_xml:encode(Map, <<"RootElement">>)} */
    @Override
    public String renderXmlEncode(String mapVar, String rootElement) {
        return "smithy_xml:encode(" + mapVar + ", <<\"" + rootElement + "\">>)";
    }

    /** Example: {@code smithy_xml:decode(Body)} */
    @Override
    public String renderXmlDecode(String bodyVar) {
        return "smithy_xml:decode(" + bodyVar + ")";
    }

    /** Example: {@code smithy_query:encode(<<"ListUsers">>, Map)} */
    @Override
    public String renderFormEncode(String actionName, String mapVar) {
        return "smithy_query:encode(<<\"" + actionName + "\">>, " + mapVar + ")";
    }

    // -------------------------------------------------------------------------
    // Step 10.3 — URI and headers
    // -------------------------------------------------------------------------

    /**
     * Renders a URI binary string with label values substituted.
     *
     * <p>Labels that require encoding are wrapped in {@code url_encode/1}.
     *
     * <p>Example output:
     * <pre>
     * <<"/weather/", (url_encode(maps:get(<<"city">>, Input)))/binary>>
     * </pre>
     */
    @Override
    public String renderUriSubstitution(String template, List<LabelBinding> labels, String inputVar) {
        if (labels.isEmpty()) {
            return "<<\"" + template + "\">>";
        }

        String processed = template;
        for (LabelBinding label : labels) {
            String mapGet = "maps:get(<<\"" + label.smithyMemberName() + "\">>, " + inputVar + ")";
            String segment;
            if (label.requiresEncoding()) {
                segment = "(url_encode(" + mapGet + "))/binary";
            } else {
                segment = "(" + mapGet + ")/binary";
            }
            processed = processed.replace("{" + label.uriPlaceholder() + "}", "\">>, " + segment + ", <<\"");
        }
        return "<<\"" + processed + "\">>";
    }

    /**
     * Renders query-string builder statements.
     *
     * <p>Example output:
     * <pre>
     * QueryParams = [{<<"unit">>, maps:get(<<"unit">>, Input, undefined)}],
     * QueryString = uri_string:compose_query([{K, V} || {K, V} <- QueryParams, V =/= undefined]),
     * </pre>
     */
    @Override
    public String renderQueryStringBuilder(List<QueryBinding> queries, String inputVar) {
        if (queries.isEmpty()) {
            return "QueryString = \"\",\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("QueryParams = [\n");
        for (int i = 0; i < queries.size(); i++) {
            QueryBinding q = queries.get(i);
            String comma = (i < queries.size() - 1) ? "," : "";
            sb.append("    {<<\"").append(q.queryKey()).append("\">>, ")
              .append("maps:get(<<\"").append(q.smithyMemberName()).append("\">>, ")
              .append(inputVar).append(", undefined)}")
              .append(comma).append("\n");
        }
        sb.append("],\n");
        sb.append("QueryString = uri_string:compose_query([{K, V} || {K, V} <- QueryParams, V =/= undefined]),\n");
        return sb.toString();
    }

    /**
     * Renders header builder statements.
     *
     * <p>Example output:
     * <pre>
     * Headers = [{<<"Content-Type">>, <<"application/json">>}
     *            | [{<<"X-Custom">>, maps:get(<<"custom">>, Input)} || ...]],
     * </pre>
     */
    @Override
    public String renderHeaderBuilder(String contentType, List<HeaderBinding> headers, String inputVar) {
        StringBuilder sb = new StringBuilder();
        sb.append("Headers = [{<<\"Content-Type\">>, <<\"").append(contentType).append("\">>}");
        for (HeaderBinding h : headers) {
            String mapGet = "maps:get(<<\"" + h.smithyMemberName() + "\">>, " + inputVar + ")";
            if (h.required()) {
                sb.append(",\n           {<<\"").append(h.headerName()).append("\">>, ").append(mapGet).append("}");
            } else {
                sb.append("\n           | [{<<\"").append(h.headerName()).append("\">>, ").append(mapGet)
                  .append("} || maps:is_key(<<\"").append(h.smithyMemberName()).append("\">>, ").append(inputVar).append(")]");
            }
        }
        sb.append("],\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Step 10.4 — HTTP client block
    // -------------------------------------------------------------------------

    /**
     * Renders an {@code httpc:request/4} call with a response pattern-match.
     *
     * <p>Example output:
     * <pre>
     * case httpc:request(get, {Url, Headers}, [], []) of
     *     {ok, {{_, 200, _}, _RespHeaders, RespBody}} ->
     *         {ok, deserialize_get_weather(jsx:decode(RespBody, [return_maps]))};
     *     {ok, {{_, StatusCode, _}, _RespHeaders, RespBody}} ->
     *         parse_error(StatusCode, jsx:decode(RespBody, [return_maps]));
     *     {error, Reason} ->
     *         {error, Reason}
     * end
     * </pre>
     */
    @Override
    public String renderHttpClientBlock(
            OperationSpec spec,
            String urlVar,
            String headersVar,
            String bodyVar,
            String methodVar) {

        String opName = ErlangSymbolProvider.toFunctionName(spec.operationName());
        int successCode = spec.http().successCode();

        String requestArgs = buildHttpcRequestArgs(spec, urlVar, headersVar, bodyVar, methodVar);
        String deserializeCall = buildDeserializeCall(spec, opName);

        return "case httpc:request(" + requestArgs + ") of\n"
             + "    {ok, {{_, " + successCode + ", _}, _RespHeaders, RespBody}} ->\n"
             + "        " + deserializeCall + ";\n"
             + "    {ok, {{_, StatusCode, _}, _RespHeaders, RespBody}} ->\n"
             + "        parse_error(StatusCode, " + renderJsonDecode("RespBody") + ");\n"
             + "    {error, Reason} ->\n"
             + "        {error, Reason}\n"
             + "end";
    }

    private String buildHttpcRequestArgs(
            OperationSpec spec,
            String urlVar,
            String headersVar,
            String bodyVar,
            String methodVar) {

        String method = spec.http().method().toLowerCase();
        boolean hasBody = spec.body() != null
            && spec.body().encoding() != BodyEncoding.NONE
            && !spec.body().bodyMemberNames().isEmpty();

        if (hasBody) {
            String contentType = resolveContentType(spec.body().encoding());
            return method + ", {" + urlVar + ", " + headersVar + ", \""
                + contentType + "\", " + bodyVar + "}, [], []";
        } else {
            return method + ", {" + urlVar + ", " + headersVar + "}, [], []";
        }
    }

    private String buildDeserializeCall(OperationSpec spec, String opName) {
        BodyEncoding enc = spec.body() != null ? spec.body().encoding() : BodyEncoding.NONE;
        switch (enc) {
            case XML:
                return "{ok, deserialize_" + opName + "(" + renderXmlDecode("RespBody") + ")}";
            case FORM_URLENCODED:
                return "{ok, deserialize_" + opName + "(RespBody)}";
            default:
                return "{ok, deserialize_" + opName + "(" + renderJsonDecode("RespBody") + ")}";
        }
    }

    private static String resolveContentType(BodyEncoding encoding) {
        return switch (encoding) {
            case XML -> "application/xml";
            case FORM_URLENCODED -> "application/x-www-form-urlencoded";
            default -> "application/json";
        };
    }

    // -------------------------------------------------------------------------
    // Step 10.5 — Auth and retry wrappers
    // -------------------------------------------------------------------------

    /**
     * Wraps an inner block with SigV4 signing if required.
     *
     * <p>If SigV4 auth is needed, prepends a signing call:
     * <pre>
     * SignedHeaders = smithy_sigv4:sign_request(Method, Url, Headers, Body, Config),
     * &lt;innerBlock&gt;
     * </pre>
     * If no auth is required, returns {@code innerBlock} unchanged.
     */
    @Override
    public String renderAuthWrapper(AuthSpec auth, String innerBlock) {
        if (!auth.requiresSigV4()) {
            return innerBlock;
        }
        return "SignedHeaders = smithy_sigv4:sign_request(Method, Url, Headers, Body, Config),\n"
             + innerBlock;
    }

    /**
     * Wraps a request function with retry logic.
     *
     * <p>Example output:
     * <pre>
     * smithy_retry:with_retry(RequestFun, #{max_retries => 3})
     * </pre>
     * If retry is disabled, returns the bare function variable.
     */
    @Override
    public String renderRetryWrapper(RetrySpec retry, String requestFunVar) {
        if (!retry.enabled()) {
            return requestFunVar + "()";
        }
        return "smithy_retry:with_retry(" + requestFunVar + ", #{max_retries => " + retry.maxRetries() + "})";
    }

    // -------------------------------------------------------------------------
    // Step 10.6 — Response handler and error serializer
    // -------------------------------------------------------------------------

    /**
     * Renders the response decode path for an operation's success output.
     *
     * <p>Generates a {@code deserialize_op_name/1} function that extracts
     * fields from the decoded response body map.
     */
    @Override
    public String renderResponseHandler(OperationSpec spec, String responseVar) {
        String opName = ErlangSymbolProvider.toFunctionName(spec.operationName());
        StringBuilder sb = new StringBuilder();
        sb.append("deserialize_").append(opName).append("(").append(responseVar).append(") ->\n");

        if (spec.body() != null && !spec.body().bodyMemberNames().isEmpty()) {
            sb.append("    #{");
            List<String> members = spec.body().bodyMemberNames();
            for (int i = 0; i < members.size(); i++) {
                String m = members.get(i);
                String comma = (i < members.size() - 1) ? ", " : "";
                sb.append("<<\"").append(m).append("\">> => maps:get(<<\"").append(m).append("\">>, ")
                  .append(responseVar).append(", undefined)").append(comma);
            }
            sb.append("}");
        } else {
            sb.append("    #{}");
        }
        sb.append(".\n");
        return sb.toString();
    }

    /**
     * Renders a {@code parse_error/2} function that dispatches on error code strings
     * extracted from XML error responses.
     *
     * <p>Example output:
     * <pre>
     * parse_error(<<"NoSuchKey">>, Body) ->
     *     {error, #{error_type => no_such_key, message => maps:get(<<"Message">>, Body, <<"">>)}};
     * parse_error(_, Body) ->
     *     {error, #{error_type => unknown, body => Body}}.
     * </pre>
     */
    @Override
    public String renderErrorSerializer(ErrorSpec errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("-spec parse_error(binary(), map()) -> {error, term()}.\n");
        for (ErrorBinding eb : errors.errors()) {
            String errorAtom = ErlangSymbolProvider.toFunctionName(eb.smithyName());
            sb.append("parse_error(<<\"").append(eb.smithyName()).append("\">>, Body) ->\n");
            sb.append("    {error, #{error_type => ").append(errorAtom)
              .append(", message => maps:get(<<\"Message\">>, Body, <<\"\">>)}};\n");
        }
        sb.append("parse_error(_, Body) ->\n");
        sb.append("    {error, #{error_type => unknown, body => Body}}.\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Step 10.7 — Pagination
    // -------------------------------------------------------------------------

    /**
     * Renders a streaming helper function that loops through paginated results.
     */
    @Override
    public String renderPaginationHelper(OperationSpec spec, PaginationSpec pagination) {
        String opName = ErlangSymbolProvider.toFunctionName(spec.operationName());
        String streamName = opName + "_stream";
        String inputToken = pagination.inputTokenMember();
        String outputToken = pagination.outputTokenMember();
        String items = pagination.itemsMember();

        StringBuilder sb = new StringBuilder();

        // 2-arity public entry point
        sb.append(streamName).append("(Input, Config) ->\n");
        sb.append("    ").append(streamName).append("(Input, Config, []).\n\n");

        // 3-arity recursive worker
        sb.append(streamName).append("(Input, Config, Acc) ->\n");
        sb.append("    case ").append(opName).append("(Input, Config) of\n");
        sb.append("        {ok, Response} ->\n");
        sb.append("            Items = maps:get(<<\"").append(items).append("\">>, Response, []),\n");
        sb.append("            NewAcc = Acc ++ Items,\n");
        sb.append("            case maps:get(<<\"").append(outputToken).append("\">>, Response, undefined) of\n");
        sb.append("                undefined -> {ok, NewAcc};\n");
        sb.append("                NextToken ->\n");
        sb.append("                    NextInput = Input#{<<\"").append(inputToken).append("\">> => NextToken},\n");
        sb.append("                    ").append(streamName).append("(NextInput, Config, NewAcc)\n");
        sb.append("            end;\n");
        sb.append("        Error -> Error\n");
        sb.append("    end.\n");

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Step 10.8 — Runtime library name methods
    // -------------------------------------------------------------------------

    /** Returns {@code jsx:encode(<expr>)}. */
    @Override
    public String jsonEncodeCall(String expr) {
        return "jsx:encode(" + expr + ")";
    }

    /** Returns {@code jsx:decode(<expr>, [return_maps])}. */
    @Override
    public String jsonDecodeCall(String expr) {
        return "jsx:decode(" + expr + ", [return_maps])";
    }

    /** Returns the SigV4 signing function reference {@code smithy_sigv4:sign_request}. */
    @Override
    public String sigv4SignCall() {
        return "smithy_sigv4:sign_request";
    }

    /** Returns {@code smithy_retry:with_retry(<fun>, <opts>)}. */
    @Override
    public String retryCall(String funExpr, String optsExpr) {
        return "smithy_retry:with_retry(" + funExpr + ", " + optsExpr + ")";
    }

    // -------------------------------------------------------------------------
    // LanguageWriter delegation methods
    // -------------------------------------------------------------------------

    @Override
    public String renderToolingAttributes() {
        return "-dialyzer([no_contracts, no_match]).\n\n";
    }

    @Override
    public String exportTypes(List<String> typeNames) {
        if (typeNames.isEmpty()) return "";
        StringJoiner sj = new StringJoiner(",\n    ", "-export_type([\n    ", "\n]).\n");
        for (String name : typeNames) {
            sj.add(name);
        }
        return sj.toString();
    }

    @Override
    public String renderClientConstructor() {
        return "\n-spec new(Config :: map()) -> {ok, map()}.\n"
             + "new(Config) ->\n"
             + "    {ok, Config}.\n";
    }

    @Override
    public String renderClientOperation(OperationSpec op) {
        String opName  = ErlangSymbolProvider.toFunctionName(op.operationName());
        String inType  = ErlangSymbolProvider.toTypeName(op.inputTypeName());
        String outType = ErlangSymbolProvider.toTypeName(op.outputTypeName());
        String makeOp  = "make_" + opName + "_request";
        // Human-readable Smithy name for doc comments
        String smithyName = op.operationName();

        StringBuilder sb = new StringBuilder();
        sb.append("\n");

        // 2-arity public spec + wrapper
        sb.append("%% Calls the ").append(smithyName).append(" operation\n");
        sb.append("-spec ").append(opName)
          .append("(Client :: map(), Input :: ").append(inType).append(") ->\n");
        sb.append("    {ok, ").append(outType).append("} | {error, term()}.\n");
        sb.append(opName).append("(Client, Input) ->\n");
        sb.append("    ").append(opName).append("(Client, Input, #{}).\n\n");

        // 3-arity public spec + retry dispatcher
        sb.append("%% Calls the ").append(smithyName).append(" operation with options\n");
        sb.append("-spec ").append(opName)
          .append("(Client :: map(), Input :: ").append(inType).append(", Options :: map()) ->\n");
        sb.append("    {ok, ").append(outType).append("} | {error, term()}.\n");
        sb.append(opName).append("(Client, Input, Options) when is_map(Input), is_map(Options) ->\n");
        sb.append("    RequestFun = fun() -> ").append(makeOp).append("(Client, Input) end,\n");
        sb.append("    case maps:get(enable_retry, Options, true) of\n");
        sb.append("        true -> smithy_retry:with_retry(RequestFun, Options);\n");
        sb.append("        false -> RequestFun()\n");
        sb.append("    end.\n\n");

        // internal make_<op>_request/2 spec + body
        sb.append("%% Internal function to make the ").append(smithyName).append(" request\n");
        sb.append("-spec ").append(makeOp)
          .append("(Client :: map(), Input :: ").append(inType).append(") ->\n");
        sb.append("    {ok, ").append(outType).append("} | {error, term()}.\n");
        sb.append(makeOp).append("(Client, Input) when is_map(Input) ->\n");
        sb.append("    Method = <<\"").append(op.http().method()).append("\">>,\n");

        appendQueryString(sb, op);
        appendUrlBuilding(sb, op);
        appendBody(sb, op);
        appendHeaders(sb, op);
        appendHttpcCall(sb, op);

        return sb.toString();
    }

    @Override
    public String renderEnumCodec(EnumSpec e) {
        String baseName = ErlangSymbolProvider.toFunctionName(e.name());
        String typeName = ErlangSymbolProvider.toTypeName(e.name());
        StringBuilder sb = new StringBuilder();

        sb.append("-spec encode_").append(baseName).append("(").append(typeName).append(") -> binary().\n");
        for (String v : e.values()) {
            sb.append("encode_").append(baseName).append("(").append(toErlangAtom(v))
              .append(") -> <<\"").append(v).append("\">>;\n");
        }
        // Remove last ";\n" and replace with ".\n\n"
        int lastSemi = sb.lastIndexOf(";\n");
        sb.replace(lastSemi, lastSemi + 2, ".\n\n");

        sb.append("-spec decode_").append(baseName).append("(binary()) ->\n");
        sb.append("    {ok, ").append(typeName).append("} | {error, {invalid_enum_value, binary()}}.\n");
        for (String v : e.values()) {
            sb.append("decode_").append(baseName).append("(<<\"").append(v)
              .append("\">>) -> {ok, ").append(toErlangAtom(v)).append("};\n");
        }
        sb.append("decode_").append(baseName).append("(Other) -> {error, {invalid_enum_value, Other}}.\n\n");

        return sb.toString();
    }

    @Override
    public String renderUnionCodec(UnionSpec u) {
        String baseName = ErlangSymbolProvider.toFunctionName(u.name());
        String typeName = ErlangSymbolProvider.toTypeName(u.name());
        StringBuilder sb = new StringBuilder();

        sb.append("-spec encode_").append(baseName).append("(").append(typeName).append(") -> map().\n");
        for (FieldSpec variant : u.variants()) {
            String atomTag = ErlangSymbolProvider.toAtomTag(variant.name());
            sb.append("encode_").append(baseName).append("({").append(atomTag).append(", Value}) ->\n");
            sb.append("    #{<<\"").append(variant.name()).append("\">> => Value};\n");
        }
        sb.append("encode_").append(baseName).append("({unknown, Value}) ->\n")
          .append("    #{<<\"unknown\">> => Value}.\n\n");

        sb.append("-spec decode_").append(baseName).append("(map()) -> ").append(typeName).append(".\n");
        sb.append("decode_").append(baseName).append("(Map) when is_map(Map) ->\n");
        renderUnionDecodeBody(sb, u.variants(), 0);
        sb.append(".\n\n");

        return sb.toString();
    }

    @Override
    public String renderValidateHelper(StructSpec s) {
        List<String> required = s.fields().stream()
                .filter(FieldSpec::required)
                .map(FieldSpec::name)
                .collect(Collectors.toList());
        if (required.isEmpty()) return "";

        String funcName = "validate_" + ErlangSymbolProvider.toFunctionName(s.name());
        StringBuilder sb = new StringBuilder();
        sb.append("-spec ").append(funcName).append("(map()) ->\n");
        sb.append("    ok | {error, {missing_required_fields, [binary()]}}.\n");
        sb.append(funcName).append("(Input) ->\n");
        sb.append("    RequiredFields = [");
        for (int i = 0; i < required.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("<<\"").append(required.get(i)).append("\">>");
        }
        sb.append("],\n");
        sb.append("    Missing = [F || F <- RequiredFields, not maps:is_key(F, Input)],\n");
        sb.append("    case Missing of\n");
        sb.append("        [] -> ok;\n");
        sb.append("        _ -> {error, {missing_required_fields, Missing}}\n");
        sb.append("    end.\n\n");
        return sb.toString();
    }

    @Override
    public String renderSharedHelpers() {
        return renderSharedHelpersImpl(true, true);
    }

    /**
     * Operations-aware overload: only emits {@code url_encode/1} and {@code ensure_binary/1}
     * when at least one operation actually uses them, avoiding unused-function warnings in
     * generated Erlang modules that have no URI labels or query/header parameters.
     *
     * <ul>
     *   <li>{@code url_encode/1} is needed when any operation has non-S3 URI label bindings.</li>
     *   <li>{@code ensure_binary/1} is needed when any operation has query bindings, non-literal
     *       header bindings, or non-S3 URI label bindings.</li>
     * </ul>
     */
    @Override
    public String renderSharedHelpers(List<OperationSpec> ops) {
        boolean needsUrlEncode = ops.stream().anyMatch(op -> {
            List<LabelBinding> labels = op.labels() != null ? op.labels() : List.of();
            if (labels.isEmpty()) return false;
            // S3-style operations delegate to smithy_s3:build_url; url_encode not called directly.
            boolean hasBucketLabel = labels.stream()
                    .anyMatch(l -> "Bucket".equals(l.smithyMemberName()));
            return !hasBucketLabel;
        });

        boolean needsEnsureBinary = needsUrlEncode
                || ops.stream().anyMatch(op -> {
                    // Query params use ensure_binary when filtering non-undefined values.
                    if (op.queries() != null && !op.queries().isEmpty()) return true;
                    // Non-literal, non-required headers use ensure_binary for value conversion.
                    if (op.headers() != null && op.headers().stream()
                            .anyMatch(h -> h.literalValue() == null)) return true;
                    return false;
                });

        return renderSharedHelpersImpl(needsUrlEncode, needsEnsureBinary);
    }

    private String renderSharedHelpersImpl(boolean needsUrlEncode, boolean needsEnsureBinary) {
        if (!needsUrlEncode && !needsEnsureBinary) return "";
        StringBuilder sb = new StringBuilder();
        if (needsUrlEncode) {
            sb.append("\nurl_encode(Binary) when is_binary(Binary) ->\n");
            sb.append("    url_encode(binary_to_list(Binary));\n");
            sb.append("url_encode(String) when is_list(String) ->\n");
            sb.append("    list_to_binary(uri_string:quote(String)).\n\n");
        }
        if (needsEnsureBinary) {
            sb.append("ensure_binary(Bin) when is_binary(Bin) -> Bin;\n");
            sb.append("ensure_binary(List) when is_list(List) -> list_to_binary(List);\n");
            sb.append("ensure_binary(Int) when is_integer(Int) -> integer_to_binary(Int);\n");
            sb.append("ensure_binary(Float) when is_float(Float) -> float_to_binary(Float);\n");
            sb.append("ensure_binary(Atom) when is_atom(Atom) -> atom_to_binary(Atom, utf8);\n");
            sb.append("ensure_binary(Other) -> list_to_binary(io_lib:format(\"~p\", [Other])).\n\n");
        }
        return sb.toString();
    }

    /**
     * Renders a {@code parse_error/2} function — JSON-protocol fallback that dispatches
     * on HTTP status codes. Called by the protocol-aware overload when not XML.
     *
     * <p>Example output:
     * <pre>
     * -spec parse_error(integer(), binary()) -> {error, term()}.
     * parse_error(404, Body) -> {error, {not_found_error, Body}};
     * parse_error(_, Body) -> {error, {http_error, unknown, Body}}.
     * </pre>
     */
    @Override
    public String renderModuleParseError(List<ErrorBinding> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("-spec parse_error(integer(), binary()) -> {error, term()}.\n");
        if (errors.isEmpty()) {
            sb.append("parse_error(StatusCode, Body) ->\n");
            sb.append("    {error, {http_error, StatusCode, Body}}.\n");
        } else {
            // Deduplicate by HTTP status code: many errors may share the same code (e.g. 400).
            // Keep the first error name encountered for each code.
            LinkedHashMap<Integer, ErrorBinding> byCode = new LinkedHashMap<>();
            for (ErrorBinding eb : errors) {
                byCode.putIfAbsent(eb.httpCode(), eb);
            }
            for (ErrorBinding eb : byCode.values()) {
                String atom = ErlangSymbolProvider.toFunctionName(eb.smithyName());
                sb.append("parse_error(").append(eb.httpCode()).append(", Body) ->\n");
                sb.append("    {error, {").append(atom).append(", Body}};\n");
            }
            sb.append("parse_error(StatusCode, Body) ->\n");
            sb.append("    {error, {http_error, StatusCode, Body}}.\n");
        }
        return sb.toString();
    }

    /**
     * Protocol-aware overload.
     *
     * <ul>
     *   <li>{@code REST_XML} and {@code AWS_JSON} — dispatch on error code binary string;
     *       return {@code #{error_type => atom, message => binary()}} structured maps.</li>
     *   <li>{@code REST_JSON} / {@code AWS_QUERY} — dispatch on HTTP status code integer;
     *       return {@code {error, {atom, Body}}} tuples.</li>
     * </ul>
     */
    @Override
    public String renderModuleParseError(List<ErrorBinding> errors, ErrorCodeStrategy strategy) {
        boolean useStringDispatch = strategy == ErrorCodeStrategy.REST_XML
                                 || strategy == ErrorCodeStrategy.AWS_JSON;
        if (!useStringDispatch) {
            return renderModuleParseError(errors);
        }
        // String-code dispatch: used for REST_XML and AWS_JSON.
        // Returns structured #{error_type => atom, message => binary()} maps.
        StringBuilder sb = new StringBuilder();
        sb.append("-spec parse_error(binary(), map()) -> {error, term()}.\n");
        for (ErrorBinding eb : errors) {
            String atom = ErlangSymbolProvider.toFunctionName(eb.smithyName());
            sb.append("parse_error(<<\"").append(eb.smithyName()).append("\">>, Body) ->\n");
            sb.append("    {error, #{error_type => ").append(atom)
              .append(", message => maps:get(<<\"Message\">>, Body, <<\"\">>)}};\n");
        }
        sb.append("parse_error(_, Body) ->\n");
        sb.append("    {error, #{error_type => unknown, body => Body}}.\n");
        return sb.toString();
    }

    @Override
    public List<String> clientRuntimeModules(
            boolean needsSigV4,
            boolean needsXml,
            boolean needsQuery,
            boolean needsS3) {
        List<String> modules = new ArrayList<>();
        if (needsSigV4) {
            modules.add("client/smithy_sigv4.erl");
            modules.add("client/smithy_credentials.erl");
        }
        modules.add("client/smithy_retry.erl");
        modules.add("client/smithy_config.erl");
        if (needsXml)   modules.add("client/smithy_xml.erl");
        if (needsQuery) modules.add("client/smithy_query.erl");
        if (needsS3)    modules.add("client/smithy_s3.erl");
        return modules;
    }

    // -------------------------------------------------------------------------
    // Step 17 — Server-side rendering
    // -------------------------------------------------------------------------

    /**
     * Renders a {@code -callback} declaration for one server operation.
     *
     * <p>Example output:
     * <pre>
     * -callback get_weather(Input :: get_weather_input(), Context :: map()) ->
     *     {ok, get_weather_output()} | {error, term()}.
     * </pre>
     */
    @Override
    public String renderServerCallbackDeclaration(OperationSpec op) {
        String opName     = ErlangSymbolProvider.toFunctionName(op.operationName());
        String inputType  = op.inputTypeName()  != null
                ? ErlangSymbolProvider.toSafeSnakeCase(op.inputTypeName())  + "()" : "map()";
        String outputType = op.outputTypeName() != null
                ? ErlangSymbolProvider.toSafeSnakeCase(op.outputTypeName()) + "()" : "map()";
        return "-callback " + opName + "(Input :: " + inputType + ", Context :: map()) ->\n"
             + "    {ok, " + outputType + "} | {error, term()}.\n";
    }

    /**
     * Renders one {@code route/2} clause for a server operation (semicolon-terminated).
     *
     * <p>For restJson1: routes on HTTP method + URI path prefix.
     * <p>For awsJson:   routes on the {@code X-Amz-Target} literal header value.
     */
    @Override
    public String renderServerRouteClause(OperationSpec op) {
        String opAtom = ErlangSymbolProvider.toFunctionName(op.operationName());
        if (isAwsJsonOp(op)) {
            String target = op.headers().stream()
                    .filter(h -> "__target__".equals(h.smithyMemberName()))
                    .findFirst().map(HeaderBinding::literalValue).orElse("");
            return "route(<<\"" + target + "\">>, _) -> {ok, " + opAtom + "};\n";
        }
        String method   = op.http().method();
        String template = op.http().uriTemplate();
        List<LabelBinding> labels = op.labels() != null ? op.labels() : List.of();
        if (labels.isEmpty()) {
            return "route(<<\"" + method + "\">>, <<\"" + template + "\">>) -> {ok, " + opAtom + "};\n";
        }
        int firstBrace = template.indexOf('{');
        String prefix  = firstBrace >= 0 ? template.substring(0, firstBrace) : template;
        return "route(<<\"" + method + "\">>, <<\"" + prefix + "\", _/binary>>) -> {ok, " + opAtom + "};\n";
    }

    /** Returns the catch-all route clause that terminates the {@code route/2} function. */
    @Override
    public String renderServerRouteFallback() {
        return "route(_, _) -> {error, not_found}.\n";
    }

    /**
     * Renders the {@code handle/3} function for the dispatcher module.
     *
     * <p>For restJson1: extracts method + path from the request and calls the router.
     * <p>For awsJson:   extracts the {@code X-Amz-Target} header and calls the router.
     */
    @Override
    public String renderServerHandleFunction(List<OperationSpec> ops, String svcModuleName) {
        boolean isAwsJson = !ops.isEmpty() && isAwsJsonOp(ops.get(0));
        String routerMod  = ErlangSymbolProvider.toModuleName(svcModuleName + "_router");
        StringBuilder sb  = new StringBuilder();

        sb.append("-spec handle(module(), term(), map()) -> {pos_integer(), list(), binary()}.\n");
        sb.append("handle(Impl, Req, Context) ->\n");

        if (isAwsJson) {
            sb.append("    {_Method, _Path, Headers, Body} = smithy_server:extract(Req),\n");
            sb.append("    Target = maps:get(<<\"x-amz-target\">>, maps:from_list(Headers), <<>>),\n");
            sb.append("    case ").append(routerMod).append(":route(Target, <<>>) of\n");
        } else {
            sb.append("    {Method, Path, Headers, Body} = smithy_server:extract(Req),\n");
            sb.append("    case ").append(routerMod).append(":route(Method, Path) of\n");
        }

        for (OperationSpec op : ops) {
            String opAtom = ErlangSymbolProvider.toFunctionName(op.operationName());
            if (isAwsJson) {
                sb.append("        {ok, ").append(opAtom).append("} -> dispatch_").append(opAtom)
                  .append("(Impl, <<>>, Headers, Body, Context);\n");
            } else {
                sb.append("        {ok, ").append(opAtom).append("} -> dispatch_").append(opAtom)
                  .append("(Impl, Path, Headers, Body, Context);\n");
            }
        }

        sb.append("        {error, not_found} -> smithy_server:not_found()\n");
        sb.append("    end.\n");
        return sb.toString();
    }

    /**
     * Renders the {@code dispatch_<op>/5} private function for one server operation.
     *
     * <p>Deserializes the input, calls the implementation, and serializes the response.
     */
    @Override
    public String renderServerDispatchClause(OperationSpec op) {
        String opAtom    = ErlangSymbolProvider.toFunctionName(op.operationName());
        int successCode  = op.http().successCode();
        StringBuilder sb = new StringBuilder();
        sb.append("dispatch_").append(opAtom).append("(Impl, Path, Headers, Body, Context) ->\n");
        sb.append("    Input = deserialize_").append(opAtom).append("(Path, Headers, Body),\n");
        sb.append("    case Impl:").append(opAtom).append("(Input, Context) of\n");
        sb.append("        {ok, Output} -> smithy_server:response(")
          .append(successCode).append(", serialize_").append(opAtom).append("(Output));\n");
        sb.append("        {error, Err} -> smithy_server:error_response(Err)\n");
        sb.append("    end.\n");
        return sb.toString();
    }

    /**
     * Renders the {@code deserialize_<op>/3} private function.
     *
     * <p>Extracts path labels, header values, and body members and combines them
     * into the operation input map. For awsJson the entire body is decoded directly.
     */
    @Override
    public String renderServerDeserialize(OperationSpec op) {
        String opAtom      = ErlangSymbolProvider.toFunctionName(op.operationName());
        List<LabelBinding> labels      = op.labels()  != null ? op.labels()  : List.of();
        List<String>       bodyMembers = op.body()    != null && !op.body().bodyMemberNames().isEmpty()
                                        ? op.body().bodyMemberNames() : List.of();
        StringBuilder sb = new StringBuilder();
        sb.append("deserialize_").append(opAtom).append("(Path, _Headers, Body) ->\n");

        if (isAwsJsonOp(op)) {
            // AWS JSON: the entire body IS the input map.
            sb.append("    _ = Path,\n");
            sb.append("    jsx:decode(Body, [return_maps]).\n");
            return sb.toString();
        }

        // Extract the first path label via binary pattern-matching (covers the common case).
        if (!labels.isEmpty()) {
            String template  = op.http().uriTemplate();
            int firstBrace   = template.indexOf('{');
            String prefix    = firstBrace >= 0 ? template.substring(0, firstBrace) : template;
            LabelBinding lbl = labels.get(0);
            String varName   = ErlangSymbolProvider.toVarName(lbl.smithyMemberName());
            sb.append("    <<\"").append(prefix).append("\", ").append(varName)
              .append("/binary>> = Path,\n");
        } else {
            sb.append("    _ = Path,\n");
        }

        if (!bodyMembers.isEmpty()) {
            sb.append("    Decoded = jsx:decode(Body, [return_maps]),\n");
            sb.append("    #{");
            boolean firstEntry = true;
            for (String m : bodyMembers) {
                if (!firstEntry) sb.append(",\n      ");
                sb.append("<<\"").append(m).append("\">> => maps:get(<<\"")
                  .append(m).append("\">>, Decoded, undefined)");
                firstEntry = false;
            }
            for (LabelBinding lbl : labels) {
                String varName = ErlangSymbolProvider.toVarName(lbl.smithyMemberName());
                sb.append(",\n      <<\"").append(lbl.smithyMemberName())
                  .append("\">> => ").append(varName);
            }
            sb.append("}.\n");
        } else if (!labels.isEmpty()) {
            sb.append("    _ = Body,\n");
            sb.append("    #{");
            for (int i = 0; i < labels.size(); i++) {
                if (i > 0) sb.append(", ");
                LabelBinding lbl = labels.get(i);
                String varName   = ErlangSymbolProvider.toVarName(lbl.smithyMemberName());
                sb.append("<<\"").append(lbl.smithyMemberName())
                  .append("\">> => ").append(varName);
            }
            sb.append("}.\n");
        } else {
            sb.append("    _ = Body,\n");
            sb.append("    #{}.\n");
        }

        return sb.toString();
    }

    /**
     * Renders the {@code serialize_<op>/1} private function.
     *
     * <p>Encodes the output map to a JSON binary for the response body.
     */
    @Override
    public String renderServerSerialize(OperationSpec op) {
        String opAtom = ErlangSymbolProvider.toFunctionName(op.operationName());
        return "serialize_" + opAtom + "(Output) ->\n"
             + "    jsx:encode(Output).\n";
    }

    /**
     * Renders one stub function for the impl scaffold.
     *
     * <p>Types are qualified with the handler module name (e.g.
     * {@code weather_service_handler:get_weather_input()}) so the impl module compiles
     * without re-declaring or importing them locally.
     *
     * <p>Example output:
     * <pre>
     * -spec get_weather(weather_service_handler:get_weather_input(), map()) ->
     *     {ok, weather_service_handler:get_weather_output()} | {error, term()}.
     * get_weather(_Input, _Context) ->
     *     {error, not_implemented}.
     * </pre>
     */
    @Override
    public String renderServerImplStub(OperationSpec op, String handlerModuleName) {
        String opAtom     = ErlangSymbolProvider.toFunctionName(op.operationName());
        String inputType  = op.inputTypeName()  != null
                ? handlerModuleName + ":" + ErlangSymbolProvider.toSafeSnakeCase(op.inputTypeName())  + "()" : "map()";
        String outputType = op.outputTypeName() != null
                ? handlerModuleName + ":" + ErlangSymbolProvider.toSafeSnakeCase(op.outputTypeName()) + "()" : "map()";
        return "-spec " + opAtom + "(" + inputType + ", map()) ->\n"
             + "    {ok, " + outputType + "} | {error, term()}.\n"
             + opAtom + "(_Input, _Context) ->\n"
             + "    {error, not_implemented}.\n";
    }

    /**
     * Renders the Cowboy 2.x plain HTTP handler module that bridges the generated
     * dispatcher to the Cowboy framework.
     *
     * <p>The generated module:
     * <ul>
     *   <li>Declares {@code -behaviour(cowboy_handler)}.</li>
     *   <li>Exports {@code init/2}.</li>
     *   <li>Calls {@code <base>_dispatcher:handle(<base>_impl, Req0, #{})}.
     *   <li>Converts the {@code [{binary(), binary()}]} header list returned by
     *       {@code smithy_server:response/2} to a map with {@code maps:from_list/1}
     *       before passing it to {@code cowboy_req:reply/4}.</li>
     * </ul>
     *
     * <p>This method is intentionally on {@code ErlangWriter} and <em>not</em> on the
     * {@code LanguageWriter} interface — Cowboy is an Erlang-ecosystem concept and the
     * core interface must remain framework-agnostic.
     *
     * @param baseName the module base name (e.g. {@code "weather_service"})
     */
    public String renderServerCowboyHandler(String baseName) {
        String moduleName    = baseName + "_cowboy";
        String dispatcherMod = baseName + "_dispatcher";
        String implMod       = baseName + "_impl";
        return "-module(" + moduleName + ").\n"
             + "-behaviour(cowboy_handler).\n"
             + "\n"
             + "-export([init/2]).\n"
             + "\n"
             + "%% Cowboy 2.x plain HTTP handler.\n"
             + "%% Delegates all routing, deserialization, dispatch, and serialization\n"
             + "%% to the generated " + dispatcherMod + ":handle/3.\n"
             + "%% smithy_server:response/2 returns headers as [{binary(), binary()}];\n"
             + "%% Cowboy 2.x reply/4 expects a map -- converted with maps:from_list/1.\n"
             + "init(Req0, State) ->\n"
             + "    {Code, Headers, Body} =\n"
             + "        " + dispatcherMod + ":handle(" + implMod + ", Req0, #{}),\n"
             + "    Req = cowboy_req:reply(Code, maps:from_list(Headers), Body, Req0),\n"
             + "    {ok, Req, State}.\n";
    }

    /** Returns the paths of server runtime modules to copy alongside generated files. */
    @Override
    public List<String> serverRuntimeModules() {
        return List.of(
                "server/smithy_server.erl",
                "server/smithy_validator.erl",
                "server/smithy_error_map.erl"
        );
    }

    /**
     * Returns the complete source of a single consolidated server module that combines
     * type definitions, behaviour callbacks, routing, dispatch, and the Cowboy HTTP
     * entry point into one {@code <baseName>_server.erl} file.
     *
     * <p>Sections in order:
     * <ol>
     *   <li>Module header — {@code -module(<baseName>_server).}</li>
     *   <li>Module comment</li>
     *   <li>{@code -behaviour(cowboy_handler).}</li>
     *   <li>{@code -export([init/2, handle/3, route/2]).}</li>
     *   <li>Type definitions (structs, enums, unions, errors)</li>
     *   <li>{@code -callback} declarations</li>
     *   <li>{@code init/2} — Cowboy glue delegating to {@code handle/3}</li>
     *   <li>{@code handle/3} — routes and dispatches, calling local {@code route/2}</li>
     *   <li>Route clauses — one per operation plus the catch-all fallback</li>
     *   <li>Per-operation dispatch/deserialize/serialize blocks</li>
     * </ol>
     */
    @Override
    public String renderServerModule(String baseName, List<OperationSpec> ops, ModuleTypeSpec types) {
        String serverModuleName = baseName + "_server";
        String implMod          = baseName + "_impl";
        StringBuilder sb = new StringBuilder();

        // 1. Module header
        sb.append(moduleHeader(serverModuleName));

        // 2. Module comment
        sb.append(renderModuleComment("Generated Smithy server for " + baseName + ". Do not edit."));
        sb.append("\n");

        // 3. -behaviour(cowboy_handler).
        sb.append("-behaviour(cowboy_handler).\n");
        sb.append("\n");

        // 4. -export([init/2, handle/3, route/2]).
        sb.append(exportSection(List.of(
                new ExportSpec("init",   2),
                new ExportSpec("handle", 3),
                new ExportSpec("route",  2)
        )));
        sb.append("\n");

        // 5. Type definitions
        for (StructSpec s : types.structures()) sb.append(renderStructType(s));
        for (EnumSpec   e : types.enums())      sb.append(renderEnumType(e));
        for (UnionSpec  u : types.unions())     sb.append(renderUnionType(u));
        for (StructSpec e : types.errors())     sb.append(renderStructType(e));
        sb.append("\n");

        // 6. -callback declarations
        for (OperationSpec op : ops) {
            sb.append(renderServerCallbackDeclaration(op));
        }
        sb.append("\n");

        // 7. init/2 — Cowboy glue: delegates to handle/3 and replies via cowboy_req
        sb.append("init(Req0, State) ->\n");
        sb.append("    {Code, Headers, Body} =\n");
        sb.append("        handle(").append(implMod).append(", Req0, #{}),\n");
        sb.append("    Req = cowboy_req:reply(Code, maps:from_list(Headers), Body, Req0),\n");
        sb.append("    {ok, Req, State}.\n");
        sb.append("\n");

        // 8. handle/3 — routes request and dispatches to per-operation helper, using local route/2
        sb.append(renderServerHandleFunctionLocal(ops));
        sb.append("\n");

        // 9. Route clauses — one per operation, then the catch-all fallback
        for (OperationSpec op : ops) {
            sb.append(renderServerRouteClause(op));
        }
        sb.append(renderServerRouteFallback());

        // 10. Per-operation blocks
        for (OperationSpec op : ops) {
            sb.append("\n");
            sb.append(renderServerDispatchClause(op));
            sb.append("\n");
            sb.append(renderServerDeserialize(op));
            sb.append("\n");
            sb.append(renderServerSerialize(op));
        }

        return sb.toString();
    }

    /**
     * Renders the {@code handle/3} function for the consolidated server module.
     *
     * <p>Identical in logic to {@link #renderServerHandleFunction} but calls
     * {@code route/2} as a local function (no module qualifier) since routing
     * is inlined in the same {@code _server.erl} file.
     */
    private String renderServerHandleFunctionLocal(List<OperationSpec> ops) {
        boolean isAwsJson = !ops.isEmpty() && isAwsJsonOp(ops.get(0));
        StringBuilder sb = new StringBuilder();

        sb.append("-spec handle(module(), term(), map()) -> {pos_integer(), list(), binary()}.\n");
        sb.append("handle(Impl, Req, Context) ->\n");

        if (isAwsJson) {
            sb.append("    {_Method, _Path, Headers, Body} = smithy_server:extract(Req),\n");
            sb.append("    Target = maps:get(<<\"x-amz-target\">>, maps:from_list(Headers), <<>>),\n");
            sb.append("    case route(Target, <<>>) of\n");
        } else {
            sb.append("    {Method, Path, Headers, Body} = smithy_server:extract(Req),\n");
            sb.append("    case route(Method, Path) of\n");
        }

        for (OperationSpec op : ops) {
            String opAtom = ErlangSymbolProvider.toFunctionName(op.operationName());
            if (isAwsJson) {
                sb.append("        {ok, ").append(opAtom).append("} -> dispatch_").append(opAtom)
                  .append("(Impl, <<>>, Headers, Body, Context);\n");
            } else {
                sb.append("        {ok, ").append(opAtom).append("} -> dispatch_").append(opAtom)
                  .append("(Impl, Path, Headers, Body, Context);\n");
            }
        }

        sb.append("        {error, not_found} -> smithy_server:not_found()\n");
        sb.append("    end.\n");
        return sb.toString();
    }

    // ── Private helper ────────────────────────────────────────────────────────

    /**
     * Returns true if the operation belongs to an AWS JSON protocol, detected by the presence
     * of a {@code __target__} header binding with a literal value.
     */
    private static boolean isAwsJsonOp(OperationSpec op) {
        return op.headers() != null && op.headers().stream()
                .anyMatch(h -> "__target__".equals(h.smithyMemberName()));
    }

    // ── Private helpers for renderClientOperation ─────────────────────────────

    private void appendQueryString(StringBuilder sb, OperationSpec op) {
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
            sb.append("    QsFiltered = [{K, ensure_binary(V)} || {K, V} <- QsParams, V =/= undefined],\n");
            sb.append("    QueryString = case QsFiltered of\n");
            sb.append("        [] -> <<>>;\n");
            sb.append("        _ -> <<\"?\", (uri_string:compose_query(QsFiltered))/binary>>\n");
            sb.append("    end,\n");
        }
    }

    /**
     * Appends URL-building code. For S3-style operations (those with a {@code Bucket} label),
     * delegates to {@code smithy_s3:build_url/4} which handles virtual-hosted-style routing.
     * For all other operations, constructs the URL from the client endpoint and URI template.
     */
    private void appendUrlBuilding(StringBuilder sb, OperationSpec op) {
        List<LabelBinding> labels = op.labels() != null ? op.labels() : List.of();
        boolean hasBucketLabel = labels.stream()
            .anyMatch(l -> "Bucket".equals(l.smithyMemberName()));

        if (hasBucketLabel) {
            // S3-specific: delegate bucket routing to smithy_s3:build_url
            sb.append("    Bucket = maps:get(<<\"Bucket\">>, Input, <<>>),\n");
            boolean hasKeyLabel = labels.stream()
                .anyMatch(l -> "Key".equals(l.smithyMemberName()));
            if (hasKeyLabel) {
                sb.append("    Key = maps:get(<<\"Key\">>, Input, <<>>),\n");
            } else {
                sb.append("    Key = <<>>,\n");
            }
            sb.append("    Url = smithy_s3:build_url(Client, Bucket, Key, QueryString),\n");
        } else {
            // Standard: Endpoint + URI template substitution
            sb.append("    Endpoint = maps:get(endpoint, Client),\n");
            if (labels.isEmpty()) {
                sb.append("    Uri = <<\"").append(op.http().uriTemplate()).append("\">>,\n");
            } else {
                sb.append("    Uri0 = <<\"").append(op.http().uriTemplate()).append("\">>,\n");
                for (int i = 0; i < labels.size(); i++) {
                    LabelBinding label = labels.get(i);
                    String varName = ErlangSymbolProvider.toVarName(label.smithyMemberName());
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
            sb.append("    Url = <<Endpoint/binary, Uri/binary, QueryString/binary>>,\n");
        }
    }

    private void appendBody(StringBuilder sb, OperationSpec op) {
        BodySpec body = op.body();
        boolean hasBodyMembers = body != null
                && body.encoding() != BodyEncoding.NONE
                && !body.bodyMemberNames().isEmpty();

        if (hasBodyMembers) {
            // AWS JSON 1.0/1.1: encode the full Input map directly — the protocol places
            // all members in the body with no HTTP label/query/header bindings.
            if (op.protocolErrorStrategy() == ErrorCodeStrategy.AWS_JSON) {
                sb.append("    Body = jsx:encode(Input),\n");
                return;
            }
            // @httpPayload: the single designated member IS the raw request body; skip the map wrapper.
            if (body.payloadMember() != null) {
                sb.append("    Body = maps:get(<<\"").append(body.payloadMember())
                  .append("\">>, Input, <<>>),\n");
                return;
            }
            sb.append("    BodyMap = maps:filter(fun(_, V) -> V =/= undefined end, #{");
            List<String> members = body.bodyMemberNames();
            java.util.Map<String, String> wireOverrides = body.wireNameOverrides() != null
                    ? body.wireNameOverrides() : java.util.Map.of();
            java.util.Map<String, java.util.Map<String, String>> nestedOverrides =
                    body.nestedWireNameOverrides() != null
                    ? body.nestedWireNameOverrides() : java.util.Map.of();
            for (int i = 0; i < members.size(); i++) {
                String smithyName = members.get(i);
                String wireName = wireOverrides.getOrDefault(smithyName, smithyName);
                if (i > 0) sb.append(", ");
                java.util.Map<String, String> nested = nestedOverrides.get(smithyName);
                if (nested != null && !nested.isEmpty()) {
                    // Wrap with smithy_query:rename_map_keys/2 to apply @xmlName overrides on
                    // the members of the nested structure (e.g. Tags → Tag inside TagSpecification).
                    String renameMapLiteral = nested.entrySet().stream()
                            .map(e -> "<<\"" + e.getKey() + "\">> => <<\"" + e.getValue() + "\">>")
                            .collect(java.util.stream.Collectors.joining(", ", "#{", "}"));
                    sb.append("<<\"").append(wireName)
                      .append("\">> => smithy_query:rename_map_keys(maps:get(<<\"")
                      .append(smithyName).append("\">>, Input, undefined), ")
                      .append(renameMapLiteral).append(")");
                } else {
                    sb.append("<<\"").append(wireName).append("\">> => maps:get(<<\"")
                      .append(smithyName).append("\">>, Input, undefined)");
                }
            }
            sb.append("}),\n");
            switch (body.encoding()) {
                case XML:
                    sb.append("    Body = smithy_xml:encode(BodyMap, <<\"Body\">>),\n");
                    break;
                case FORM_URLENCODED:
                    if (op.apiVersion() != null && !op.apiVersion().isEmpty()) {
                        sb.append("    Body = smithy_query:encode(<<\"")
                          .append(op.operationName())
                          .append("\">>, BodyMap, <<\"")
                          .append(op.apiVersion())
                          .append("\">>),\n");
                    } else {
                        sb.append("    Body = smithy_query:encode(<<\"")
                          .append(op.operationName())
                          .append("\">>, BodyMap),\n");
                    }
                    break;
                default:
                    sb.append("    Body = jsx:encode(BodyMap),\n");
            }
        } else if (op.auth().requiresSigV4()) {
            sb.append("    Body = <<>>,\n");
        }
    }

    private void appendHeaders(StringBuilder sb, OperationSpec op) {
        // Use the protocol-level Content-Type (e.g. "application/x-amz-json-1.0" for awsJson1.0,
        // "application/xml" for S3). This is set by the protocol analyzer and stored in the IR,
        // so we never fall back to the generic BodyEncoding → string mapping here.
        String contentType = op.protocolContentType() != null
                ? op.protocolContentType()
                : resolveBodyContentType(op.body(), op.responseEncoding());
        List<HeaderBinding> headerBindings = op.headers() != null ? op.headers() : List.of();

        // Reference style: numbered accumulator variables, one case expression per optional header.
        sb.append("    Headers0 = [{<<\"Content-Type\">>, <<\"").append(contentType).append("\">>}],\n");

        int idx = 0;
        for (HeaderBinding h : headerBindings) {
            String prev = "Headers" + idx;
            String next = "Headers" + (idx + 1);
            // Each case uses a unique variable name to avoid Erlang's single-assignment restriction.
            String valVar = "Val" + (idx + 1);
            if (h.literalValue() != null) {
                sb.append("    ").append(next).append(" = [{<<\"").append(h.headerName())
                  .append("\">>, <<\"").append(h.literalValue()).append("\">>} | ").append(prev).append("],\n");
            } else if (h.required()) {
                sb.append("    ").append(next).append(" = [{<<\"").append(h.headerName())
                  .append("\">>, ensure_binary(maps:get(<<\"").append(h.smithyMemberName())
                  .append("\">>, Input))} | ").append(prev).append("],\n");
            } else {
                sb.append("    ").append(next).append(" = case maps:get(<<\"")
                  .append(h.smithyMemberName()).append("\">>, Input, undefined) of\n");
                sb.append("        undefined -> ").append(prev).append(";\n");
                sb.append("        ").append(valVar).append(" -> [{<<\"").append(h.headerName())
                  .append("\">>, ensure_binary(").append(valVar).append(")} | ").append(prev).append("]\n");
                sb.append("    end,\n");
            }
            idx++;
        }

        sb.append("    Headers = Headers").append(idx).append(",\n");
    }

    /**
     * Appends the signing wrapper and httpc dispatch block.
     *
     * <p>When SigV4 is required, wraps the HTTP call in a {@code case} expression
     * so that signing failures produce {@code {error, {signing_error, Reason}}}
     * rather than a function_clause crash.
     *
     * <p>Error responses are parsed as REST-XML: the {@code <Code>} element is
     * extracted and used to dispatch {@code parse_error/2} by error name string,
     * which gives unambiguous mapping even when multiple errors share an HTTP code.
     */
    private void appendHttpcCall(StringBuilder sb, OperationSpec op) {
        BodySpec body = op.body();
        boolean hasBodyMembers = body != null
                && body.encoding() != BodyEncoding.NONE
                && !body.bodyMemberNames().isEmpty();
        String contentType = op.protocolContentType() != null
                ? op.protocolContentType()
                : resolveBodyContentType(body, op.responseEncoding());
        boolean hasSigV4 = op.auth().requiresSigV4();

        // Determine indentation prefix (deeper inside signing case when sigv4 is required)
        String i1 = "    ";   // 4 spaces — top-level statement indent
        String i2 = "        "; // 8 spaces — inside signing {ok, SignedHeaders} branch

        if (hasSigV4) {
            sb.append(i1).append("case smithy_sigv4:sign_request(Method, Url, Headers, Body, Client) of\n");
            sb.append(i1).append("    {ok, SignedHeaders} ->\n");
        }

        String ind = hasSigV4 ? i2 + "    " : i1; // 12 or 4 spaces for the body

        sb.append(ind).append("StringUrl = binary_to_list(Url),\n");
        sb.append(ind).append("StringHeaders = [{binary_to_list(K), binary_to_list(V)} || {K, V} <- ")
          .append(hasSigV4 ? "SignedHeaders" : "Headers").append("],\n");
        if (hasBodyMembers) {
            sb.append(ind).append("Request = {StringUrl, StringHeaders, \"").append(contentType).append("\", Body},\n");
        } else {
            sb.append(ind).append("Request = {StringUrl, StringHeaders},\n");
        }
        sb.append(ind).append("case httpc:request(binary_to_atom(string:lowercase(Method), utf8), Request, [], [{body_format, binary}]) of\n");

        // Success branch — three cases based on which response binding traits are present:
        //
        //  1. @httpPayload or @httpResponseCode present → assemble map from explicit HTTP bindings;
        //     the body is placed raw under the @httpPayload key (no XML/JSON decode).
        //
        //  2. Only @httpHeader bindings (no payload/code override) → decode the XML/JSON body as
        //     normal, then merge the extracted header values into the resulting map.
        //
        //  3. No response bindings at all → standard decode path (unchanged behaviour).
        boolean hasPayloadMember  = op.responsePayloadMember() != null;
        boolean hasCodeMember     = op.responseCodeMember()    != null;
        List<io.smithy.beam.core.ir.HeaderBinding> rhdrs =
                op.responseHeaders() != null ? op.responseHeaders() : List.of();
        boolean hasHeaderBindings = !rhdrs.isEmpty();

        // Bind RespHeaders only when we need to read per-header bindings from it.
        String respHeadersVar = hasHeaderBindings ? "RespHeaders" : "_RespHeaders";
        sb.append(ind).append("    {ok, {{_, StatusCode, _}, ").append(respHeadersVar)
          .append(", ResponseBody}} when StatusCode >= 200, StatusCode < 300 ->\n");

        // Extract each @httpHeader-bound output member into a uniquely-named variable.
        // httpc returns response headers as [{string(), string()}] with lowercase names.
        if (hasHeaderBindings) {
            for (int i = 0; i < rhdrs.size(); i++) {
                io.smithy.beam.core.ir.HeaderBinding h = rhdrs.get(i);
                String varName = "RespH" + i;
                String hdrKey  = h.headerName().toLowerCase();
                sb.append(ind).append("        ").append(varName)
                  .append(" = case proplists:get_value(\"").append(hdrKey)
                  .append("\", RespHeaders) of\n");
                sb.append(ind).append("            undefined -> undefined;\n");
                sb.append(ind).append("            ").append(varName).append("Str -> list_to_binary(")
                  .append(varName).append("Str)\n");
                sb.append(ind).append("        end,\n");
            }
        }

        if (hasPayloadMember || hasCodeMember) {
            // Case 1: @httpPayload / @httpResponseCode — build map from explicit bindings only.
            sb.append(ind).append("        {ok, maps:filter(fun(_, V) -> V =/= undefined end, #{\n");

            if (hasCodeMember) {
                sb.append(ind).append("            <<\"").append(op.responseCodeMember())
                  .append("\">> => StatusCode");
                if (hasPayloadMember || !rhdrs.isEmpty()) sb.append(",");
                sb.append("\n");
            }

            if (hasPayloadMember) {
                sb.append(ind).append("            <<\"").append(op.responsePayloadMember())
                  .append("\">> => ResponseBody");
                if (!rhdrs.isEmpty()) sb.append(",");
                sb.append("\n");
            }

            for (int i = 0; i < rhdrs.size(); i++) {
                sb.append(ind).append("            <<\"").append(rhdrs.get(i).smithyMemberName())
                  .append("\">> => RespH").append(i);
                if (i < rhdrs.size() - 1) sb.append(",");
                sb.append("\n");
            }

            sb.append(ind).append("        })};\n");

        } else if (hasHeaderBindings) {
            // Case 2: only @httpHeader bindings — decode the body normally and merge header values in.
            sb.append(ind).append("        HeaderMap = maps:filter(fun(_, V) -> V =/= undefined end, #{\n");
            for (int i = 0; i < rhdrs.size(); i++) {
                sb.append(ind).append("            <<\"").append(rhdrs.get(i).smithyMemberName())
                  .append("\">> => RespH").append(i);
                if (i < rhdrs.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append(ind).append("        }),\n");
            sb.append(ind).append("        case ResponseBody of\n");
            sb.append(ind).append("            <<>> -> {ok, HeaderMap};\n");
            sb.append(ind).append("            _ ->\n");
            if (op.responseEncoding() == BodyEncoding.XML) {
                if (op.protocolErrorStrategy() == ErrorCodeStrategy.AWS_QUERY) {
                    sb.append(ind).append("                case smithy_xml:decode(ResponseBody) of\n");
                    sb.append(ind).append("                    {ok, Decoded} ->\n");
                    sb.append(ind).append("                        case smithy_query:unwrap_response(Decoded) of\n");
                    sb.append(ind).append("                            {ok, BodyMap} -> {ok, maps:merge(BodyMap, HeaderMap)};\n");
                    sb.append(ind).append("                            Err -> Err\n");
                    sb.append(ind).append("                        end;\n");
                    sb.append(ind).append("                    DecodeError -> DecodeError\n");
                    sb.append(ind).append("                end\n");
                } else {
                    sb.append(ind).append("                case smithy_xml:decode(ResponseBody) of\n");
                    sb.append(ind).append("                    {ok, BodyMap} -> {ok, maps:merge(BodyMap, HeaderMap)};\n");
                    sb.append(ind).append("                    DecodeError -> DecodeError\n");
                    sb.append(ind).append("                end\n");
                }
            } else {
                sb.append(ind).append("                try jsx:decode(ResponseBody, [return_maps]) of\n");
                sb.append(ind).append("                    BodyMap -> {ok, maps:merge(BodyMap, HeaderMap)}\n");
                sb.append(ind).append("                catch\n");
                sb.append(ind).append("                    _:DecodeError -> {error, {json_decode_error, DecodeError}}\n");
                sb.append(ind).append("                end\n");
            }
            sb.append(ind).append("        end;\n");

        } else {
            // Case 3: no response bindings — standard decode path.
            sb.append(ind).append("        case ResponseBody of\n");
            sb.append(ind).append("            <<>> -> {ok, #{}};\n");
            sb.append(ind).append("            _ ->\n");
            if (op.responseEncoding() == BodyEncoding.XML) {
                if (op.protocolErrorStrategy() == ErrorCodeStrategy.AWS_QUERY) {
                    // AwsQuery responses are wrapped in <XyzResponse><XyzResult>; strip both layers
                    sb.append(ind).append("                case smithy_xml:decode(ResponseBody) of\n");
                    sb.append(ind).append("                    {ok, Decoded} -> smithy_query:unwrap_response(Decoded);\n");
                    sb.append(ind).append("                    DecodeError -> DecodeError\n");
                    sb.append(ind).append("                end\n");
                } else {
                    // REST-XML (S3 etc.): no envelope wrapper, return decoded tree directly
                    sb.append(ind).append("                smithy_xml:decode(ResponseBody)\n");
                }
            } else {
                sb.append(ind).append("                try jsx:decode(ResponseBody, [return_maps]) of\n");
                sb.append(ind).append("                    DecodedBody -> {ok, DecodedBody}\n");
                sb.append(ind).append("                catch\n");
                sb.append(ind).append("                    _:DecodeError -> {error, {json_decode_error, DecodeError}}\n");
                sb.append(ind).append("                end\n");
            }
            sb.append(ind).append("        end;\n");
        }

        // Error branch — protocol-specific dispatch
        ErrorCodeStrategy errStrategy = op.protocolErrorStrategy() != null
                ? op.protocolErrorStrategy() : ErrorCodeStrategy.REST_JSON;
        if (errStrategy == ErrorCodeStrategy.REST_XML) {
            // REST-XML (S3 etc.): parse XML body, extract <Code> element, dispatch by name string
            sb.append(ind).append("    {ok, {{_, _ErrStatusCode, _}, _RespHeaders, ErrorBody}} ->\n");
            sb.append(ind).append("        case smithy_xml:decode(ErrorBody) of\n");
            sb.append(ind).append("            {ok, #{<<\"Error\">> := ErrorMap}} ->\n");
            sb.append(ind).append("                Code = maps:get(<<\"Code\">>, ErrorMap, <<\"Unknown\">>),\n");
            sb.append(ind).append("                parse_error(Code, ErrorMap);\n");
            sb.append(ind).append("            _ ->\n");
            sb.append(ind).append("                {error, {http_error, ErrorBody}}\n");
            sb.append(ind).append("        end;\n");
        } else if (errStrategy == ErrorCodeStrategy.AWS_JSON) {
            // AWS JSON 1.0/1.1 (DynamoDB etc.): decode JSON body, extract __type, dispatch by name string
            sb.append(ind).append("    {ok, {{_, ErrStatusCode, _}, _RespHeaders, ErrorBody}} ->\n");
            sb.append(ind).append("        try\n");
            sb.append(ind).append("            ErrorMap = jsx:decode(ErrorBody, [return_maps]),\n");
            sb.append(ind).append("            ErrorType = maps:get(<<\"__type\">>, ErrorMap, <<\"Unknown\">>),\n");
            sb.append(ind).append("            parse_error(ErrorType, ErrorMap)\n");
            sb.append(ind).append("        catch\n");
            sb.append(ind).append("            _:_ -> {error, {http_error, ErrStatusCode, ErrorBody}}\n");
            sb.append(ind).append("        end;\n");
        } else if (errStrategy == ErrorCodeStrategy.AWS_QUERY) {
            // AWS Query / EC2 Query: error body is XML; two possible formats:
            //   IAM/SNS: <ErrorResponse><Error><Code>…</Code></Error></ErrorResponse>
            //   EC2:     <Response><Errors><Error><Code>…</Code></Error></Errors></Response>
            sb.append(ind).append("    {ok, {{_, _ErrStatusCode, _}, _RespHeaders, ErrorBody}} ->\n");
            sb.append(ind).append("        case smithy_xml:decode(ErrorBody) of\n");
            sb.append(ind).append("            {ok, #{<<\"ErrorResponse\">> := #{<<\"Error\">> := ErrorMap}}} ->\n");
            sb.append(ind).append("                Code = maps:get(<<\"Code\">>, ErrorMap, <<\"Unknown\">>),\n");
            sb.append(ind).append("                parse_error(Code, ErrorMap);\n");
            sb.append(ind).append("            {ok, #{<<\"Response\">> := #{<<\"Errors\">> := #{<<\"Error\">> := ErrorMap}}}} ->\n");
            sb.append(ind).append("                Code = maps:get(<<\"Code\">>, ErrorMap, <<\"Unknown\">>),\n");
            sb.append(ind).append("                parse_error(Code, ErrorMap);\n");
            sb.append(ind).append("            _ ->\n");
            sb.append(ind).append("                {error, {http_error, ErrorBody}}\n");
            sb.append(ind).append("        end;\n");
        } else {
            // REST-JSON: dispatch by HTTP status code integer
            sb.append(ind).append("    {ok, {{_, ErrStatusCode, _}, _RespHeaders, ErrorBody}} ->\n");
            sb.append(ind).append("        parse_error(ErrStatusCode, ErrorBody);\n");
        }

        sb.append(ind).append("    {error, Reason} ->\n");
        sb.append(ind).append("        {error, {http_error, Reason}}\n");
        sb.append(ind).append("end");

        if (hasSigV4) {
            sb.append(";\n");
            sb.append(i1).append("    {error, SignError} ->\n");
            sb.append(i1).append("        {error, {signing_error, SignError}}\n");
            sb.append(i1).append("end");
        }
        sb.append(".\n");
    }

    private void renderUnionDecodeBody(StringBuilder sb, List<FieldSpec> variants, int depth) {
        String indent = "    ".repeat(depth + 1);
        if (variants.isEmpty()) {
            sb.append(indent).append("{unknown, Map}");
            return;
        }
        FieldSpec head = variants.get(0);
        List<FieldSpec> tail = variants.subList(1, variants.size());
        String atomTag = ErlangSymbolProvider.toAtomTag(head.name());
        sb.append(indent).append("case maps:find(<<\"").append(head.name()).append("\">>, Map) of\n");
        sb.append(indent).append("    {ok, Value} -> {").append(atomTag).append(", Value};\n");
        sb.append(indent).append("    error ->\n");
        renderUnionDecodeBody(sb, tail, depth + 1);
        sb.append("\n").append(indent).append("end");
    }

    /**
     * Resolves the Content-Type for a request.
     *
     * <p>When the operation has body members, the encoding of those members determines
     * the type. When there are no body members (body is null or NONE), falls back to
     * {@code fallbackEncoding} — typically the response encoding — so that XML-protocol
     * operations (like S3) correctly advertise {@code application/xml} even on requests
     * that carry no body.
     */
    private static String resolveBodyContentType(BodySpec body, BodyEncoding fallbackEncoding) {
        if (body == null || body.encoding() == BodyEncoding.NONE) {
            return resolveContentType(fallbackEncoding != null ? fallbackEncoding : BodyEncoding.JSON);
        }
        return resolveContentType(body.encoding());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Erlang reserved words that cannot appear as unquoted atoms in pattern position.
     *
     * <p>These must be single-quoted even though they are lowercase.
     */
    private static final Set<String> ERLANG_RESERVED = Set.of(
        "after", "and", "andalso", "band", "begin", "bnot", "bor", "bsl", "bsr", "bxor",
        "case", "catch", "cond", "div", "end", "fun", "if", "let", "not", "of", "or",
        "orelse", "receive", "rem", "try", "when", "xor"
    );

    /**
     * Converts a Smithy enum value string to an Erlang atom literal.
     *
     * <p>Values are always lowercased for idiomatic Erlang style ("Enabled" → {@code enabled}).
     * Atoms that need quoting (start with non-lowercase, contain special chars, or are Erlang
     * reserved words) are wrapped in single quotes.
     */
    static String toErlangAtom(String value) {
        String lower = value.toLowerCase().replace('-', '_');
        // Unquoted atoms: start with lowercase letter, contain only [a-z0-9_@], not reserved
        if (lower.matches("[a-z][a-z0-9_@]*") && !ERLANG_RESERVED.contains(lower)) {
            return lower;
        }
        return "'" + lower + "'";
    }

    /**
     * Converts a {@link TypeRef} to the corresponding Erlang type string.
     *
     * <p>Optional wrappers are stripped — the {@code =>} map operator already implies
     * a field may be absent, so adding {@code | undefined} would be redundant noise.
     * Lists use the {@code [T]} bracket notation instead of {@code list(T)}.
     */
    private String typeRefToErlang(TypeRef ref) {
        if (ref instanceof TypeRef.Primitive p) {
            return primitiveToErlang(p.kind());
        } else if (ref instanceof TypeRef.Named n) {
            return ErlangSymbolProvider.toSafeSnakeCase(n.name()) + "()";
        } else if (ref instanceof TypeRef.ListOf l) {
            return "[" + typeRefToErlang(l.element()) + "]";
        } else if (ref instanceof TypeRef.MapOf) {
            return "map()";
        } else if (ref instanceof TypeRef.Optional o) {
            // Strip the Optional wrapper — => in map types already implies optionality
            return typeRefToErlang(o.inner());
        }
        return "term()";
    }

    private static String primitiveToErlang(PrimitiveKind kind) {
        switch (kind) {
            case STRING:  return "binary()";
            case INTEGER:
            case LONG:    return "integer()";
            case FLOAT:
            case DOUBLE:  return "float()";
            case BOOLEAN: return "boolean()";
            case BLOB:    return "binary()";
            case TIMESTAMP: return "calendar:datetime()";
            default:      return "term()";
        }
    }
}
