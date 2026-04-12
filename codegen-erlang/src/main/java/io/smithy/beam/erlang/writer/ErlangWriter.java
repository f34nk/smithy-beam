package io.smithy.beam.erlang.writer;

import io.smithy.beam.core.ir.AuthSpec;
import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.EnumSpec;
import io.smithy.beam.core.ir.ErrorBinding;
import io.smithy.beam.core.ir.ErrorSpec;
import io.smithy.beam.core.ir.FieldSpec;
import io.smithy.beam.core.ir.HeaderBinding;
import io.smithy.beam.core.ir.LabelBinding;
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

import java.util.List;
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
     * Generates an {@code -export([...])} attribute.
     *
     * <p>Example output:
     * <pre>
     * -export([get_weather/2, new/1]).
     * </pre>
     */
    @Override
    public String exportSection(List<ExportSpec> exports) {
        if (exports.isEmpty()) {
            return "-export([]).\n";
        }
        String entries = exports.stream()
            .map(e -> e.functionName() + "/" + e.arity())
            .collect(Collectors.joining(", "));
        return "-export([" + entries + "]).\n";
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
     * <p>Example output:
     * <pre>
     * -type get_weather_input() :: #{
     *     city := binary(),
     *     unit => temperature_unit()
     * }.
     * </pre>
     */
    @Override
    public String renderStructType(StructSpec struct) {
        String typeName = ErlangSymbolProvider.toSnakeCase(struct.name());
        if (struct.fields().isEmpty()) {
            return "-type " + typeName + "() :: #{}.\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("-type ").append(typeName).append("() :: #{\n");
        List<FieldSpec> fields = struct.fields();
        for (int i = 0; i < fields.size(); i++) {
            FieldSpec f = fields.get(i);
            String separator = f.required() ? " :=" : " =>";
            String comma = (i < fields.size() - 1) ? "," : "";
            sb.append("    ").append(f.name()).append(separator)
              .append(" ").append(typeRefToErlang(f.type()))
              .append(comma).append("\n");
        }
        sb.append("}.\n");
        return sb.toString();
    }

    /**
     * Renders an Erlang union type for a Smithy enum.
     *
     * <p>Example output:
     * <pre>
     * -type temperature_unit() :: 'Celsius' | 'Fahrenheit'.
     * </pre>
     */
    @Override
    public String renderEnumType(EnumSpec e) {
        String typeName = ErlangSymbolProvider.toSnakeCase(e.name());
        String variants = e.values().stream()
            .map(v -> "'" + v + "'")
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
        String typeName = ErlangSymbolProvider.toSnakeCase(u.name());
        String variants = u.variants().stream()
            .map(f -> "{" + f.name() + ", " + typeRefToErlang(f.type()) + "}")
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
     * Renders an Erlang {@code -spec} declaration.
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

    /** Example: {@code aws_xml:encode(Map, <<"RootElement">>)} */
    @Override
    public String renderXmlEncode(String mapVar, String rootElement) {
        return "aws_xml:encode(" + mapVar + ", <<\"" + rootElement + "\">>)";
    }

    /** Example: {@code aws_xml:decode(Body)} */
    @Override
    public String renderXmlDecode(String bodyVar) {
        return "aws_xml:decode(" + bodyVar + ")";
    }

    /** Example: {@code aws_query:encode(Map)} */
    @Override
    public String renderFormEncode(String mapVar) {
        return "aws_query:encode(" + mapVar + ")";
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

        // Build a list of binary segments from the template
        // Replace each {label} placeholder with the appropriate Erlang expression
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
     * SignedHeaders = aws_sigv4:sign_request(Method, Url, Headers, Body, Config),
     * &lt;innerBlock&gt;
     * </pre>
     * If no auth is required, returns {@code innerBlock} unchanged.
     */
    @Override
    public String renderAuthWrapper(AuthSpec auth, String innerBlock) {
        if (!auth.requiresSigV4()) {
            return innerBlock;
        }
        return "SignedHeaders = aws_sigv4:sign_request(Method, Url, Headers, Body, Config),\n"
             + innerBlock;
    }

    /**
     * Wraps a request function with retry logic.
     *
     * <p>Example output:
     * <pre>
     * aws_retry:with_retry(RequestFun, #{max_retries => 3})
     * </pre>
     * If retry is disabled, returns the bare function variable.
     */
    @Override
    public String renderRetryWrapper(RetrySpec retry, String requestFunVar) {
        if (!retry.enabled()) {
            return requestFunVar + "()";
        }
        return "aws_retry:with_retry(" + requestFunVar + ", #{max_retries => " + retry.maxRetries() + "})";
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
     * Renders a {@code parse_error/2} function that dispatches HTTP status codes
     * to modeled error atoms.
     *
     * <p>Example output:
     * <pre>
     * parse_error(StatusCode, Body) ->
     *     case StatusCode of
     *         404 -> {error, {not_found_error, Body}};
     *         _ -> {error, {unknown_error, StatusCode, Body}}
     *     end.
     * </pre>
     */
    @Override
    public String renderErrorSerializer(ErrorSpec errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("parse_error(StatusCode, Body) ->\n");
        sb.append("    case StatusCode of\n");
        for (ErrorBinding eb : errors.errors()) {
            String errorAtom = ErlangSymbolProvider.toFunctionName(eb.smithyName());
            sb.append("        ").append(eb.httpCode())
              .append(" -> {error, {").append(errorAtom).append(", Body}};\n");
        }
        sb.append("        _ -> {error, {unknown_error, StatusCode, Body}}\n");
        sb.append("    end.\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Step 10.7 — Pagination
    // -------------------------------------------------------------------------

    /**
     * Renders a streaming helper function that loops through paginated results.
     *
     * <p>Generates an {@code op_name_stream/2} function that:
     * <ol>
     *   <li>Calls the operation</li>
     *   <li>Extracts the output token from the response</li>
     *   <li>Accumulates items</li>
     *   <li>Recurses until no more pages</li>
     * </ol>
     *
     * <p>Example output:
     * <pre>
     * list_items_stream(Input, Config) ->
     *     list_items_stream(Input, Config, []).
     * list_items_stream(Input, Config, Acc) ->
     *     case list_items(Input, Config) of
     *         {ok, Response} ->
     *             Items = maps:get(<<"items">>, Response, []),
     *             NewAcc = Acc ++ Items,
     *             case maps:get(<<"nextToken">>, Response, undefined) of
     *                 undefined -> {ok, NewAcc};
     *                 NextToken ->
     *                     NextInput = Input#{<<"pageToken">> => NextToken},
     *                     list_items_stream(NextInput, Config, NewAcc)
     *             end;
     *         Error -> Error
     *     end.
     * </pre>
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

    /** Returns the SigV4 signing function reference {@code aws_sigv4:sign_request}. */
    @Override
    public String sigv4SignCall() {
        return "aws_sigv4:sign_request";
    }

    /** Returns {@code aws_retry:with_retry(<fun>, <opts>)}. */
    @Override
    public String retryCall(String funExpr, String optsExpr) {
        return "aws_retry:with_retry(" + funExpr + ", " + optsExpr + ")";
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a {@link TypeRef} to the corresponding Erlang type string.
     */
    private String typeRefToErlang(TypeRef ref) {
        if (ref instanceof TypeRef.Primitive p) {
            return primitiveToErlang(p.kind());
        } else if (ref instanceof TypeRef.Named n) {
            return ErlangSymbolProvider.toSnakeCase(n.name()) + "()";
        } else if (ref instanceof TypeRef.ListOf l) {
            return "list(" + typeRefToErlang(l.element()) + ")";
        } else if (ref instanceof TypeRef.MapOf) {
            return "map()";
        } else if (ref instanceof TypeRef.Optional o) {
            return typeRefToErlang(o.inner()) + " | undefined";
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
