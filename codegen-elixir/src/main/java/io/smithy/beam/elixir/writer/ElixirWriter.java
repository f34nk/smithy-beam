package io.smithy.beam.elixir.writer;

import io.smithy.beam.elixir.symbol.ElixirReservedWords;
import io.smithy.beam.core.ir.AuthSpec;
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
import java.util.stream.Collectors;
import io.smithy.beam.core.writer.ExportSpec;
import io.smithy.beam.core.writer.LanguageWriter;
import io.smithy.beam.core.writer.MapEntrySpec;
import io.smithy.beam.core.writer.ParamSpec;
import io.smithy.beam.elixir.symbol.ElixirSymbolProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Elixir code emitter implementing {@link LanguageWriter}.
 *
 * <p>All methods return pure Elixir source text that the pipeline assembles
 * into {@code .ex} files.  No mutable state is held — each call is side-effect-free.
 *
 * <p>The critical Elixir design difference from {@code ErlangWriter}: client operations
 * return {@code %SmithyClient.Operation{}} structs rather than making HTTP calls
 * directly.  All HTTP mechanics are delegated to {@code SmithyClient.request/2} at
 * runtime — the generated functions merely construct the operation value.
 */
public final class ElixirWriter implements LanguageWriter {

    // -------------------------------------------------------------------------
    // Step 21.2 — identity, naming, module structure
    // -------------------------------------------------------------------------

    @Override
    public String languageId() {
        return "elixir";
    }

    @Override
    public String fileExtension() {
        return ".ex";
    }

    @Override
    public String moduleName(String smithyName) {
        return ElixirSymbolProvider.toModuleName(smithyName);
    }

    @Override
    public String functionName(String smithyName) {
        return ElixirSymbolProvider.toFunctionName(smithyName);
    }

    @Override
    public String typeName(String smithyName) {
        return ElixirSymbolProvider.toTypeName(smithyName);
    }

    @Override
    public String varName(String smithyName) {
        return ElixirSymbolProvider.toVarName(smithyName);
    }

    /**
     * Returns the Elixir atom key for a Smithy member name.
     *
     * <p>Example: {@code "cityId" → ":city_id"}
     */
    @Override
    public String mapKey(String smithyMemberName) {
        return ElixirSymbolProvider.toAtomTag(smithyMemberName);
    }

    /**
     * Returns the {@code defmodule} header.
     *
     * <p>Example: {@code "defmodule WeatherService do\n"}
     */
    @Override
    public String moduleHeader(String name) {
        return "defmodule " + ElixirSymbolProvider.toModuleName(name) + " do\n";
    }

    /**
     * Returns {@code "end\n"} — the Elixir module footer.
     */
    @Override
    public String moduleFooter() {
        return "end\n";
    }

    /**
     * Returns a {@code @moduledoc} attribute followed by a blank line.
     *
     * <p>Example: {@code "  @moduledoc \"Generated Smithy client for WeatherService.\"\n\n"}
     */
    @Override
    public String renderModuleComment(String text) {
        return "  @moduledoc \"" + text + "\"\n\n";
    }

    /**
     * Elixir has no explicit export mechanism; all {@code def} functions are public.
     * Returns an empty string.
     */
    @Override
    public String exportSection(List<ExportSpec> exports) {
        return "";
    }

    /**
     * Returns a {@code @behaviour} attribute.
     *
     * <p>Example: {@code "  @behaviour WeatherService.Handler\n"}
     */
    @Override
    public String behaviourDeclaration(String behaviourName) {
        return "  @behaviour " + ElixirSymbolProvider.toModuleName(behaviourName) + "\n";
    }

    // -------------------------------------------------------------------------
    // Step 21.2 — type rendering
    // -------------------------------------------------------------------------

    /**
     * Renders an Elixir {@code @type} for a Smithy structure.
     *
     * <p>Optional fields include {@code | nil}; required fields do not.
     *
     * <p>Example output:
     * <pre>
     *   @type get_weather_input :: %{city: String.t(), unit: temperature_unit() | nil}
     * </pre>
     */
    @Override
    public String renderStructType(StructSpec struct) {
        String typeName = ElixirReservedWords.escapeTypeName(ElixirSymbolProvider.toSnakeCase(struct.name()));
        if (struct.fields().isEmpty()) {
            return "  @type " + typeName + " :: %{}\n";
        }
        List<FieldSpec> fields = struct.fields();
        String body = fields.stream()
            .map(f -> {
                String key = ElixirSymbolProvider.toSnakeCase(f.name());
                String typeStr = typeRefToElixir(f.type());
                if (!f.required() && !(f.type() instanceof TypeRef.Optional)) {
                    typeStr = typeStr + " | nil";
                }
                return key + ": " + typeStr;
            })
            .collect(Collectors.joining(", "));
        return "  @type " + typeName + " :: %{" + body + "}\n";
    }

    /**
     * Renders an Elixir {@code @type} for a Smithy enum as a union of atoms.
     *
     * <p>Example output:
     * <pre>
     *   @type temperature_unit :: :celsius | :fahrenheit
     * </pre>
     */
    @Override
    public String renderEnumType(EnumSpec e) {
        String typeName = ElixirReservedWords.escapeTypeName(ElixirSymbolProvider.toSnakeCase(e.name()));
        String variants = e.values().stream()
            .map(ElixirWriter::toElixirAtom)
            .collect(Collectors.joining(" | "));
        return "  @type " + typeName + " :: " + variants + "\n";
    }

    /**
     * Renders an Elixir {@code @type} for a Smithy union as a union of tagged tuples.
     *
     * <p>Example output:
     * <pre>
     *   @type result :: {:ok, success_output()} | {:error, failure_output()}
     * </pre>
     */
    @Override
    public String renderUnionType(UnionSpec u) {
        String typeName = ElixirReservedWords.escapeTypeName(ElixirSymbolProvider.toSnakeCase(u.name()));
        String variants = u.variants().stream()
            .map(f -> "{:" + ElixirSymbolProvider.toSnakeCase(f.name()) + ", " + typeRefToElixir(f.type()) + "}")
            .collect(Collectors.joining(" | "));
        return "  @type " + typeName + " :: " + variants + "\n";
    }

    /**
     * Renders an Elixir {@code @callback} declaration.
     *
     * <p>Example output:
     * <pre>
     *   @callback get_weather(input :: map(), ctx :: map()) :: {:ok, map()} | {:error, term()}
     * </pre>
     */
    @Override
    public String renderCallbackDeclaration(String name, List<ParamSpec> params, TypeRef returnType) {
        String funcName = ElixirSymbolProvider.toFunctionName(name);
        String paramStr = params.stream()
            .map(p -> ElixirSymbolProvider.toVarName(p.name()) + " :: " + typeRefToElixir(p.type()))
            .collect(Collectors.joining(", "));
        return "  @callback " + funcName + "(" + paramStr + ") :: " + typeRefToElixir(returnType) + "\n";
    }

    /**
     * Renders an Elixir {@code @spec} declaration.
     *
     * <p>Example output:
     * <pre>
     *   @spec get_weather(map(), map()) :: {:ok, map()} | {:error, term()}
     * </pre>
     */
    @Override
    public String renderFunctionSpec(String name, List<ParamSpec> params, TypeRef returnType) {
        String funcName = ElixirSymbolProvider.toFunctionName(name);
        String paramStr = params.stream()
            .map(p -> typeRefToElixir(p.type()))
            .collect(Collectors.joining(", "));
        return "  @spec " + funcName + "(" + paramStr + ") :: " + typeRefToElixir(returnType) + "\n";
    }

    /**
     * Returns the opening line of a {@code def} function.
     *
     * <p>Example: {@code "  def get_weather(input, ctx) do"}
     */
    @Override
    public String renderFunctionHead(String name, List<String> paramPatterns) {
        String funcName = ElixirSymbolProvider.toFunctionName(name);
        String params = String.join(", ", paramPatterns);
        return "  def " + funcName + "(" + params + ") do";
    }

    /**
     * Returns {@code "  end"} — the Elixir function terminator.
     */
    @Override
    public String renderFunctionEnd() {
        return "  end";
    }

    // -------------------------------------------------------------------------
    // Step 21.3 — Map operations
    // -------------------------------------------------------------------------

    /**
     * Renders a {@code Map.get/3} call with an atom key.
     *
     * <p>Example: {@code Map.get(input, :city, nil)}
     */
    @Override
    public String renderMapGet(String mapVar, String smithyMemberName, String defaultVal) {
        String atomKey = ElixirSymbolProvider.toAtomTag(smithyMemberName);
        return "Map.get(" + mapVar + ", " + atomKey + ", " + defaultVal + ")";
    }

    /**
     * Renders a map literal with atom keys.
     *
     * <p>Example: {@code %{city: city, unit: unit}}
     */
    @Override
    public String renderMapBuild(List<MapEntrySpec> entries) {
        if (entries.isEmpty()) {
            return "%{}";
        }
        String body = entries.stream()
            .map(e -> ElixirSymbolProvider.toSnakeCase(e.key()) + ": " + e.valueExpr())
            .collect(Collectors.joining(", "));
        return "%{" + body + "}";
    }

    // -------------------------------------------------------------------------
    // Step 21.3 — JSON and encoding
    // -------------------------------------------------------------------------

    /** Example: {@code Jason.encode!(map_var)} */
    @Override
    public String renderJsonEncode(String mapVar) {
        return "Jason.encode!(" + mapVar + ")";
    }

    /** Example: {@code Jason.decode!(body_var)} */
    @Override
    public String renderJsonDecode(String bodyVar) {
        return "Jason.decode!(" + bodyVar + ")";
    }

    /** Example: {@code SmithyXml.encode(map_var, "RootElement")} */
    @Override
    public String renderXmlEncode(String mapVar, String rootElement) {
        return "SmithyXml.encode(" + mapVar + ", \"" + rootElement + "\")";
    }

    /** Example: {@code SmithyXml.decode(body_var)} */
    @Override
    public String renderXmlDecode(String bodyVar) {
        return "SmithyXml.decode(" + bodyVar + ")";
    }

    /** Example: {@code SmithyQuery.encode("ListUsers", map_var)} */
    @Override
    public String renderFormEncode(String actionName, String mapVar) {
        return "SmithyQuery.encode(\"" + actionName + "\", " + mapVar + ")";
    }

    // -------------------------------------------------------------------------
    // Step 21.3 — URI and headers
    // -------------------------------------------------------------------------

    /**
     * Renders an Elixir URI string with label substitution.
     *
     * <p>Uses string interpolation with {@code URI.encode_www_form/1} for labels
     * that require encoding.
     *
     * <p>Example output:
     * <pre>
     * "/weather/#{URI.encode_www_form(Map.get(input, :city, ""))}"
     * </pre>
     */
    @Override
    public String renderUriSubstitution(String template, List<LabelBinding> labels, String inputVar) {
        if (labels.isEmpty()) {
            return "\"" + template + "\"";
        }

        String result = template;
        for (LabelBinding label : labels) {
            String atomKey  = ElixirSymbolProvider.toAtomTag(label.smithyMemberName());
            String strKey   = "\"" + label.smithyMemberName() + "\"";
            // Accept both snake_case atom (:bucket) and original string key ("Bucket")
            // so callers can use either key convention.
            String mapGet   = "(Map.get(" + inputVar + ", " + atomKey + ") || Map.get(" + inputVar + ", " + strKey + ") || \"\")";
            String segment;
            if (label.requiresEncoding()) {
                segment = "#{URI.encode_www_form(" + mapGet + ")}";
            } else {
                segment = "#{" + mapGet + "}";
            }
            // Replace both greedy {name+} and plain {name} forms in the URI template.
            result = result.replace("{" + label.uriPlaceholder() + "+}", segment);
            result = result.replace("{" + label.uriPlaceholder() + "}", segment);
        }
        return "\"" + result + "\"";
    }

    /**
     * Renders Elixir query-string builder code.
     *
     * <p>Example output:
     * <pre>
     *     query_params = [{"unit", Map.get(input, :unit, nil)}]
     *     query_string = query_params
     *       |> Enum.reject(fn {_, v} -> is_nil(v) end)
     *       |> URI.encode_query()
     * </pre>
     */
    @Override
    public String renderQueryStringBuilder(List<QueryBinding> queries, String inputVar) {
        if (queries.isEmpty()) {
            return "    query_string = \"\"\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("    query_params = [\n");
        for (int i = 0; i < queries.size(); i++) {
            QueryBinding q = queries.get(i);
            String comma = (i < queries.size() - 1) ? "," : "";
            String atomKey = ElixirSymbolProvider.toAtomTag(q.smithyMemberName());
            sb.append("      {\"").append(q.queryKey()).append("\", Map.get(")
              .append(inputVar).append(", ").append(atomKey).append(", nil)}")
              .append(comma).append("\n");
        }
        sb.append("    ]\n");
        sb.append("    query_string = query_params\n");
        sb.append("      |> Enum.reject(fn {_, v} -> is_nil(v) end)\n");
        sb.append("      |> URI.encode_query()\n");
        return sb.toString();
    }

    /**
     * Renders Elixir header builder code.
     *
     * <p>Example output:
     * <pre>
     *     headers = [{"Content-Type", "application/json"}]
     *     headers = if Map.has_key?(input, :custom), do: [{"X-Custom", ...} | headers], else: headers
     * </pre>
     */
    @Override
    public String renderHeaderBuilder(String contentType, List<HeaderBinding> headers, String inputVar) {
        StringBuilder sb = new StringBuilder();
        sb.append("    headers = [{\"Content-Type\", \"").append(contentType).append("\"}]\n");
        for (HeaderBinding h : headers) {
            String atomKey = ElixirSymbolProvider.toAtomTag(h.smithyMemberName());
            if (h.literalValue() != null) {
                sb.append("    headers = [{\"").append(h.headerName())
                  .append("\", \"").append(h.literalValue()).append("\"} | headers]\n");
            } else if (h.required()) {
                sb.append("    headers = [{\"").append(h.headerName())
                  .append("\", to_string(Map.get(").append(inputVar).append(", ").append(atomKey).append("))} | headers]\n");
            } else {
                sb.append("    headers = if Map.has_key?(").append(inputVar).append(", ").append(atomKey)
                  .append("),\n");
                sb.append("      do: [{\"").append(h.headerName())
                  .append("\", to_string(Map.get(").append(inputVar).append(", ").append(atomKey)
                  .append("))} | headers],\n");
                sb.append("      else: headers\n");
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Step 21.3 — HTTP client block (operations-as-values)
    // -------------------------------------------------------------------------

    /**
     * The critical Elixir difference: instead of making an HTTP call directly,
     * returns a {@code %SmithyClient.Operation{}} struct value.
     *
     * <p>All HTTP mechanics are delegated to {@code SmithyClient.request/2} at
     * runtime — the generated function just builds the operation value.
     *
     * <p>Example output:
     * <pre>
     *     %SmithyClient.Operation{
     *       name: :get_weather,
     *       http: %{method: "GET", uri: "/weather/{city}"},
     *       input: input,
     *       output_shape: :get_weather_output,
     *       auth: :none
     *     }
     * </pre>
     */
    @Override
    public String renderHttpClientBlock(
            OperationSpec spec,
            String urlVar,
            String headersVar,
            String bodyVar,
            String methodVar) {

        String opAtom    = ElixirSymbolProvider.toFunctionName(spec.operationName());
        String method    = spec.http() != null ? spec.http().method() : "POST";
        String uriTemplate = spec.http() != null ? spec.http().uriTemplate() : "/";
        String authAtom  = spec.auth() != null && spec.auth().requiresSigV4() ? ":sigv4" : ":none";
        String outShape  = spec.outputTypeName() != null
                ? ":" + ElixirSymbolProvider.toSnakeCase(spec.outputTypeName())
                : ":unknown";

        return "    %SmithyClient.Operation{\n"
             + "      name: :" + opAtom + ",\n"
             + "      http: %{method: \"" + method + "\", uri: \"" + uriTemplate + "\"},\n"
             + "      input: input,\n"
             + "      output_shape: " + outShape + ",\n"
             + "      auth: " + authAtom + "\n"
             + "    }";
    }

    // -------------------------------------------------------------------------
    // Step 21.3 — Auth and retry wrappers
    // -------------------------------------------------------------------------

    /**
     * Auth is delegated to the {@code SmithyClient} runtime in Elixir; returns the
     * inner block unchanged regardless of the {@code auth} spec.
     */
    @Override
    public String renderAuthWrapper(AuthSpec auth, String innerBlock) {
        return innerBlock;
    }

    /**
     * Wraps the operation struct with a retry call when retry is enabled.
     *
     * <p>Example (enabled): {@code SmithyClient.Retry.with_retry(op_fun, %{max_retries: 3})}
     * <p>Example (disabled): the bare function variable is returned unchanged.
     */
    @Override
    public String renderRetryWrapper(RetrySpec retry, String requestFunVar) {
        if (!retry.enabled()) {
            return requestFunVar + ".()";
        }
        return "SmithyClient.Retry.with_retry(" + requestFunVar + ", %{max_retries: " + retry.maxRetries() + "})";
    }

    // -------------------------------------------------------------------------
    // Step 21.3 — Response handler and error serializer
    // -------------------------------------------------------------------------

    /**
     * Renders the response deserialise helper for an operation.
     *
     * <p>Example output:
     * <pre>
     *   defp deserialize_get_weather(response) do
     *     %{
     *       city: Map.get(response, :city),
     *       temperature: Map.get(response, :temperature)
     *     }
     *   end
     * </pre>
     */
    @Override
    public String renderResponseHandler(OperationSpec spec, String responseVar) {
        String opName = ElixirSymbolProvider.toFunctionName(spec.operationName());
        StringBuilder sb = new StringBuilder();
        sb.append("  defp deserialize_").append(opName).append("(").append(responseVar).append(") do\n");
        if (spec.body() != null && !spec.body().bodyMemberNames().isEmpty()) {
            sb.append("    %{\n");
            List<String> members = spec.body().bodyMemberNames();
            for (int i = 0; i < members.size(); i++) {
                String m = members.get(i);
                String atomKey = ":" + ElixirSymbolProvider.toSnakeCase(m);
                String comma = (i < members.size() - 1) ? "," : "";
                sb.append("      ").append(atomKey).append(": Map.get(")
                  .append(responseVar).append(", \"").append(m).append("\")")
                  .append(comma).append("\n");
            }
            sb.append("    }\n");
        } else {
            sb.append("    %{}\n");
        }
        sb.append("  end\n");
        return sb.toString();
    }

    /**
     * Renders an Elixir {@code parse_error/2} function.
     *
     * <p>Example output:
     * <pre>
     *   defp parse_error("NoSuchKey", body) do
     *     {:error, %{error_type: :no_such_key, message: Map.get(body, "Message", "")}}
     *   end
     *   defp parse_error(_, body), do: {:error, %{error_type: :unknown, body: body}}
     * </pre>
     */
    @Override
    public String renderErrorSerializer(ErrorSpec errors) {
        StringBuilder sb = new StringBuilder();
        for (ErrorBinding eb : errors.errors()) {
            String errorAtom = ":" + ElixirSymbolProvider.toFunctionName(eb.smithyName());
            sb.append("  defp parse_error(\"").append(eb.smithyName()).append("\", body) do\n");
            sb.append("    {:error, %{error_type: ").append(errorAtom)
              .append(", message: Map.get(body, \"Message\", \"\")}}\n");
            sb.append("  end\n");
        }
        sb.append("  defp parse_error(_, body), do: {:error, %{error_type: :unknown, body: body}}\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Step 21.3 — Pagination
    // -------------------------------------------------------------------------

    /**
     * Renders a streaming helper that pages through paginated results using the
     * {@code SmithyClient.stream/2} runtime helper.
     *
     * <p>Example output:
     * <pre>
     *   def get_weather_stream(input, opts \\ []) do
     *     SmithyClient.stream(get_weather(input), opts)
     *   end
     * </pre>
     */
    @Override
    public String renderPaginationHelper(OperationSpec spec, PaginationSpec pagination) {
        String opName     = ElixirSymbolProvider.toFunctionName(spec.operationName());
        String streamName = opName + "_stream";
        return "\n  @spec " + streamName + "(map(), map(), map()) :: Enumerable.t()\n"
             + "  def " + streamName + "(client, input, opts \\\\ %{}) do\n"
             + "    SmithyClient.stream(client, " + opName + "_op(input), opts)\n"
             + "  end\n";
    }

    // -------------------------------------------------------------------------
    // Step 21.3 — Runtime library name methods
    // -------------------------------------------------------------------------

    /** Returns {@code "Jason.encode!(expr)"}. */
    @Override
    public String jsonEncodeCall(String expr) {
        return "Jason.encode!(" + expr + ")";
    }

    /** Returns {@code "Jason.decode!(expr)"}. */
    @Override
    public String jsonDecodeCall(String expr) {
        return "Jason.decode!(" + expr + ")";
    }

    /** Returns the SigV4 signing function reference {@code SmithyClient.Sigv4.sign_request}. */
    @Override
    public String sigv4SignCall() {
        return "SmithyClient.Sigv4.sign_request";
    }

    /** Returns {@code "SmithyClient.Retry.with_retry(fun, opts)"}. */
    @Override
    public String retryCall(String funExpr, String optsExpr) {
        return "SmithyClient.Retry.with_retry(" + funExpr + ", " + optsExpr + ")";
    }

    // -------------------------------------------------------------------------
    // LanguageWriter delegation methods
    // -------------------------------------------------------------------------

    /**
     * Elixir has no Dialyzer suppression attributes at the module level.
     * Returns an empty string.
     */
    @Override
    public String renderToolingAttributes() {
        return "";
    }

    /**
     * Elixir exports types automatically; no explicit export declaration is needed.
     * Returns an empty string.
     */
    @Override
    public String exportTypes(List<String> typeNames) {
        return "";
    }

    /**
     * Returns the Elixir client constructor function.
     *
     * <p>Example output:
     * <pre>
     *   @spec new(map()) :: {:ok, map()}
     *   def new(config), do: {:ok, config}
     * </pre>
     */
    @Override
    public String renderClientConstructor() {
        return "\n  @spec new(map()) :: {:ok, map()}\n"
             + "  def new(config), do: {:ok, config}\n";
    }

    /**
     * Returns the complete source block for one Elixir client operation.
     *
     * <p>Elixir client operations return {@code %SmithyClient.Operation{}} structs
     * rather than making HTTP calls directly.  The struct carries all the protocol
     * context needed by {@code SmithyClient.request/2} to execute the request:
     * HTTP method, resolved URI (labels substituted), body encoding, response
     * decoding, static headers (e.g. {@code X-Amz-Target}), auth mode, and a
     * reference to the generated {@code parse_error/2} function.
     *
     * <p>Example output for an AWS JSON operation:
     * <pre>
     *   @doc "Calls the GetParameter operation"
     *   @spec get_parameter(map(), map(), map()) :: {:ok, map()} | {:error, term()}
     *   def get_parameter(client, input, opts \\ %{}) do
     *     SmithyClient.request(client, get_parameter_op(input), opts)
     *   end
     *
     *   defp get_parameter_op(input) do
     *     %SmithyClient.Operation{
     *       name: :get_parameter,
     *       action: "GetParameter",
     *       http: %{method: "POST", uri: "/"},
     *       input: input,
     *       output_shape: :get_parameter_result,
     *       auth: :sigv4,
     *       static_headers: [{"X-Amz-Target", "AmazonSSM.GetParameter"}],
     *       content_type: "application/x-amz-json-1.1",
     *       encoding: :json,
     *       decoding: :json,
     *       api_version: nil,
     *       parse_error_fn: &parse_error/2
     *     }
     *   end
     * </pre>
     */
    @Override
    public String renderClientOperation(OperationSpec op) {
        String opName    = ElixirSymbolProvider.toFunctionName(op.operationName());
        String method    = op.http() != null ? op.http().method() : "POST";
        String rawUri    = op.http() != null ? op.http().uriTemplate() : "/";
        String authAtom  = op.auth() != null && op.auth().requiresSigV4() ? ":sigv4" : ":none";
        String outShape  = op.outputTypeName() != null
                ? ":" + ElixirSymbolProvider.toSnakeCase(op.outputTypeName())
                : ":unknown";

        String contentType  = op.protocolContentType() != null ? op.protocolContentType() : "application/json";
        String encodingAtom = bodyEncodingAtom(op);
        String decodingAtom = responseDecodingAtom(op);
        String staticHdrs   = buildStaticHeaders(op);

        List<LabelBinding> labels = op.labels() != null ? op.labels() : List.of();

        StringBuilder sb = new StringBuilder();
        sb.append("\n");

        // Public 3-arg function: (client, input, opts \\ %{}) -> {:ok, map()} | {:error, term()}
        sb.append("  @doc \"Calls the ").append(op.operationName()).append(" operation\"\n");
        sb.append("  @spec ").append(opName).append("(map(), map(), map()) :: {:ok, map()} | {:error, term()}\n");
        sb.append("  def ").append(opName).append("(client, input, opts \\\\ %{}) do\n");
        sb.append("    SmithyClient.request(client, ").append(opName).append("_op(input), opts)\n");
        sb.append("  end\n");

        // Private operation struct builder
        sb.append("\n");
        sb.append("  defp ").append(opName).append("_op(input) do\n");

        // URI label substitution — compute the resolved URI inside the function body
        // so that path parameters from `input` are interpolated at call time.
        String uriExpr;
        if (!labels.isEmpty()) {
            String substituted = renderUriSubstitution(rawUri, labels, "input");
            sb.append("    uri = ").append(substituted).append("\n");
            uriExpr = "uri";
        } else {
            uriExpr = "\"" + rawUri + "\"";
        }

        sb.append("    %SmithyClient.Operation{\n");
        sb.append("      name: :").append(opName).append(",\n");
        sb.append("      action: \"").append(op.operationName()).append("\",\n");
        sb.append("      http: %{method: \"").append(method).append("\", uri: ").append(uriExpr).append("},\n");
        sb.append("      input: input,\n");
        sb.append("      output_shape: ").append(outShape).append(",\n");
        sb.append("      auth: ").append(authAtom).append(",\n");
        sb.append("      static_headers: ").append(staticHdrs).append(",\n");
        sb.append("      content_type: \"").append(contentType).append("\",\n");
        sb.append("      encoding: :").append(encodingAtom).append(",\n");
        sb.append("      decoding: :").append(decodingAtom).append(",\n");
        if (op.apiVersion() != null) {
            sb.append("      api_version: \"").append(op.apiVersion()).append("\",\n");
        }

        // EC2 Query protocol: emit wire-name rename maps derived from @xmlName / @ec2QueryName traits.
        // Non-EC2 operations have empty maps and use the defaults on the Operation struct.
        java.util.Map<String, String> wireOverrides =
                (op.body() != null && op.body().wireNameOverrides() != null)
                ? op.body().wireNameOverrides() : java.util.Map.of();
        java.util.Map<String, java.util.Map<String, String>> nestedOverrides =
                (op.body() != null && op.body().nestedWireNameOverrides() != null)
                ? op.body().nestedWireNameOverrides() : java.util.Map.of();
        if (!wireOverrides.isEmpty()) {
            String entries = wireOverrides.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .map(e -> "\"" + e.getKey() + "\" => \"" + e.getValue() + "\"")
                    .collect(Collectors.joining(", "));
            sb.append("      rename_map: %{").append(entries).append("},\n");
        }
        if (!nestedOverrides.isEmpty()) {
            String entries = nestedOverrides.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .map(outer -> "\"" + outer.getKey() + "\" => %{" +
                            outer.getValue().entrySet().stream()
                                    .sorted(java.util.Map.Entry.comparingByKey())
                                    .map(e -> "\"" + e.getKey() + "\" => \"" + e.getValue() + "\"")
                                    .collect(Collectors.joining(", ")) +
                            "}")
                    .collect(Collectors.joining(", "));
            sb.append("      nested_rename_map: %{").append(entries).append("},\n");
        }

        sb.append("      parse_error_fn: &parse_error/2\n");
        sb.append("    }\n");
        sb.append("  end\n");

        if (op.pagination() != null) {
            sb.append(renderPaginationHelper(op, op.pagination()));
        }

        return sb.toString();
    }

    /** Maps an operation's body encoding to the Elixir atom used by SmithyClient. */
    private static String bodyEncodingAtom(OperationSpec op) {
        if (op.body() == null) return "none";
        // @httpPayload: the designated member IS the raw request body.
        // Send it as a blob rather than wrapping it in XML/JSON.
        if (op.body().payloadMember() != null) return "blob";
        switch (op.body().encoding()) {
            case JSON:           return "json";
            case FORM_URLENCODED: return "form";
            case XML:            return "xml";
            default:             return "none";
        }
    }

    /** Maps an operation's response encoding to the Elixir atom used by SmithyClient. */
    private static String responseDecodingAtom(OperationSpec op) {
        // @httpPayload operations (e.g. S3 GetObject) return a raw binary blob.
        // Skip all parsing and return the body as-is.
        if (op.responsePayloadMember() != null) return "raw";
        if (op.responseEncoding() == null) return "json";
        switch (op.responseEncoding()) {
            case XML:
                // awsQuery and ec2Query use form-urlencoded request bodies but XML responses
                // that are wrapped in an operation-specific response envelope.
                // REST-XML (S3, CloudFront) returns plain XML with no envelope.
                boolean isQueryProtocol = op.body() != null
                        && op.body().encoding() == io.smithy.beam.core.ir.BodyEncoding.FORM_URLENCODED;
                return isQueryProtocol ? "query_xml" : "xml";
            default:
                return "json";
        }
    }

    /**
     * Builds an Elixir list literal of {@code {"HeaderName", "value"}} tuples for headers
     * that carry a hard-coded literal value (e.g. {@code X-Amz-Target}).
     */
    private static String buildStaticHeaders(OperationSpec op) {
        if (op.headers() == null || op.headers().isEmpty()) return "[]";
        List<String> pairs = new ArrayList<>();
        for (var h : op.headers()) {
            if (h.literalValue() != null) {
                pairs.add("{\"" + h.headerName() + "\", \"" + h.literalValue() + "\"}");
            }
        }
        return pairs.isEmpty() ? "[]" : "[" + String.join(", ", pairs) + "]";
    }

    /**
     * Renders Elixir {@code encode_<enum>/1} and {@code decode_<enum>/1} functions.
     *
     * <p>Example output:
     * <pre>
     *   def encode_temperature_unit(:celsius), do: "Celsius"
     *   def encode_temperature_unit(:fahrenheit), do: "Fahrenheit"
     *
     *   def decode_temperature_unit("Celsius"), do: {:ok, :celsius}
     *   def decode_temperature_unit("Fahrenheit"), do: {:ok, :fahrenheit}
     *   def decode_temperature_unit(other), do: {:error, {:invalid_enum_value, other}}
     * </pre>
     */
    @Override
    public String renderEnumCodec(EnumSpec e) {
        String baseName = ElixirSymbolProvider.toFunctionName(e.name());
        StringBuilder sb = new StringBuilder();

        for (String v : e.values()) {
            String atom = toElixirAtom(v);
            sb.append("  def encode_").append(baseName).append("(").append(atom)
              .append("), do: \"").append(v).append("\"\n");
        }
        sb.append("\n");

        for (String v : e.values()) {
            String atom = toElixirAtom(v);
            sb.append("  def decode_").append(baseName).append("(\"").append(v)
              .append("\"), do: {:ok, ").append(atom).append("}\n");
        }
        sb.append("  def decode_").append(baseName)
          .append("(other), do: {:error, {:invalid_enum_value, other}}\n\n");

        return sb.toString();
    }

    /**
     * Renders Elixir {@code encode_<union>/1} and {@code decode_<union>/1} functions.
     *
     * <p>Example output:
     * <pre>
     *   def encode_result({:success, value}), do: %{"success" => value}
     *   def encode_result({:unknown, value}), do: %{"unknown" => value}
     *
     *   def decode_result(map) do
     *     cond do
     *       Map.has_key?(map, "success") -> {:success, map["success"]}
     *       true -> {:unknown, map}
     *     end
     *   end
     * </pre>
     */
    @Override
    public String renderUnionCodec(UnionSpec u) {
        String baseName = ElixirSymbolProvider.toFunctionName(u.name());
        StringBuilder sb = new StringBuilder();

        for (FieldSpec variant : u.variants()) {
            String tag = ":" + ElixirSymbolProvider.toSnakeCase(variant.name());
            sb.append("  def encode_").append(baseName).append("({").append(tag).append(", value}), do: ")
              .append("%{\"").append(variant.name()).append("\" => value}\n");
        }
        sb.append("  def encode_").append(baseName).append("({:unknown, value}), do: %{\"unknown\" => value}\n\n");

        sb.append("  def decode_").append(baseName).append("(map) do\n");
        sb.append("    cond do\n");
        for (FieldSpec variant : u.variants()) {
            String tag = ":" + ElixirSymbolProvider.toSnakeCase(variant.name());
            sb.append("      Map.has_key?(map, \"").append(variant.name()).append("\") -> {")
              .append(tag).append(", map[\"").append(variant.name()).append("\"]}\n");
        }
        sb.append("      true -> {:unknown, map}\n");
        sb.append("    end\n");
        sb.append("  end\n\n");

        return sb.toString();
    }

    /**
     * Renders an Elixir {@code validate_<struct>/1} function that checks required fields.
     * Returns {@code ""} if the struct has no required fields.
     *
     * <p>Example output:
     * <pre>
     *   def validate_get_weather_input(input) do
     *     required = [:city]
     *     missing = Enum.reject(required, &Map.has_key?(input, &1))
     *     case missing do
     *       [] -> :ok
     *       _ -> {:error, {:missing_required_fields, missing}}
     *     end
     *   end
     * </pre>
     */
    @Override
    public String renderValidateHelper(StructSpec s) {
        List<String> required = s.fields().stream()
                .filter(FieldSpec::required)
                .map(f -> ":" + ElixirSymbolProvider.toSnakeCase(f.name()))
                .collect(Collectors.toList());
        if (required.isEmpty()) return "";

        String funcName = "validate_" + ElixirSymbolProvider.toFunctionName(s.name());
        String requiredList = "[" + String.join(", ", required) + "]";
        return "  def " + funcName + "(input) do\n"
             + "    required = " + requiredList + "\n"
             + "    missing = Enum.reject(required, &Map.has_key?(input, &1))\n"
             + "    case missing do\n"
             + "      [] -> :ok\n"
             + "      _ -> {:error, {:missing_required_fields, missing}}\n"
             + "    end\n"
             + "  end\n\n";
    }

    /**
     * Returns shared Elixir helper functions.
     *
     * <p>Returns an empty string — the Elixir client delegates all HTTP mechanics to
     * the {@code SmithyClient} runtime, so no shared helpers are needed in the
     * generated module itself.
     */
    @Override
    public String renderSharedHelpers() {
        return "";
    }

    /**
     * Returns an Elixir {@code parse_error/2} function that dispatches on HTTP status codes.
     *
     * <p>Example output:
     * <pre>
     *   defp parse_error(404, body), do: {:error, {:not_found_error, body}}
     *   defp parse_error(status_code, body), do: {:error, {:http_error, status_code, body}}
     * </pre>
     */
    @Override
    public String renderModuleParseError(List<ErrorBinding> errors) {
        StringBuilder sb = new StringBuilder();
        if (errors.isEmpty()) {
            sb.append("  defp parse_error(status_code, body),\n");
            sb.append("    do: {:error, {:http_error, status_code, body}}\n");
        } else {
            LinkedHashMap<Integer, ErrorBinding> byCode = new LinkedHashMap<>();
            for (ErrorBinding eb : errors) {
                byCode.putIfAbsent(eb.httpCode(), eb);
            }
            for (ErrorBinding eb : byCode.values()) {
                String atom = ":" + ElixirSymbolProvider.toFunctionName(eb.smithyName());
                sb.append("  defp parse_error(").append(eb.httpCode()).append(", body),\n");
                sb.append("    do: {:error, {").append(atom).append(", body}}\n");
            }
            sb.append("  defp parse_error(status_code, body),\n");
            sb.append("    do: {:error, {:http_error, status_code, body}}\n");
        }
        return sb.toString();
    }

    /**
     * Protocol-aware overload: for XML/AWS-JSON protocols dispatches on the error-code
     * string; for REST-JSON/AWS-QUERY dispatches on the HTTP status code.
     */
    @Override
    public String renderModuleParseError(List<ErrorBinding> errors, ErrorCodeStrategy strategy) {
        boolean useStringDispatch = strategy == ErrorCodeStrategy.REST_XML
                                 || strategy == ErrorCodeStrategy.AWS_JSON;
        if (!useStringDispatch) {
            return renderModuleParseError(errors);
        }
        StringBuilder sb = new StringBuilder();
        for (ErrorBinding eb : errors) {
            String atom = ":" + ElixirSymbolProvider.toFunctionName(eb.smithyName());
            sb.append("  defp parse_error(\"").append(eb.smithyName()).append("\", body),\n");
            sb.append("    do: {:error, %{error_type: ").append(atom)
              .append(", message: Map.get(body, \"Message\", \"\")}}\n");
        }
        sb.append("  defp parse_error(_, body),\n");
        sb.append("    do: {:error, %{error_type: :unknown, body: body}}\n");
        return sb.toString();
    }

    /**
     * Returns the Elixir client runtime module paths to copy alongside generated files.
     *
     * <p>Elixir runtime modules use the {@code .ex} extension and live in the
     * {@code client/} resource directory.
     */
    @Override
    public List<String> clientRuntimeModules(
            boolean needsSigV4,
            boolean needsXml,
            boolean needsQuery,
            boolean needsS3) {
        List<String> modules = new ArrayList<>();
        if (needsSigV4) {
            modules.add("client/smithy_sigv4.ex");
            modules.add("client/smithy_credentials.ex");
        }
        modules.add("client/smithy_client.ex");
        modules.add("client/smithy_retry.ex");
        if (needsXml)   modules.add("client/smithy_xml.ex");
        if (needsQuery) modules.add("client/smithy_query.ex");
        if (needsS3)    modules.add("client/smithy_s3.ex");
        return modules;
    }

    // -------------------------------------------------------------------------
    // Step 21.4 — Server-side rendering
    // -------------------------------------------------------------------------

    /**
     * Renders an Elixir {@code @callback} for one server operation.
     *
     * <p>Example output:
     * <pre>
     *   @callback get_weather(input :: map(), ctx :: map()) :: {:ok, map()} | {:error, term()}
     * </pre>
     */
    @Override
    public String renderServerCallbackDeclaration(OperationSpec op) {
        String opName = ElixirSymbolProvider.toFunctionName(op.operationName());
        return "  @callback " + opName + "(input :: map(), ctx :: map()) :: {:ok, map()} | {:error, term()}\n";
    }

    /**
     * Renders a Plug.Router route block for one server operation.
     *
     * <p>Generates a complete route including input deserialisation, validation,
     * dispatch to the handler, and serialisation of the response.
     *
     * <p>Example output:
     * <pre>
     *   get "/weather/:city" do
     *     input = deserialize_get_weather(conn)
     *     with :ok &lt;- SmithyValidator.validate(input, [:city]),
     *          {:ok, output} &lt;- handler().get_weather(input, %{}) do
     *       SmithyServer.response(conn, 200, serialize_get_weather(output))
     *     else
     *       {:error, {:missing_required_fields, _} = reason} ->
     *         SmithyServer.validation_error(conn, reason)
     *       {:error, err} ->
     *         SmithyServer.error_response(conn, err)
     *     end
     *   end
     * </pre>
     */
    @Override
    public String renderServerRouteClause(OperationSpec op) {
        String opName   = ElixirSymbolProvider.toFunctionName(op.operationName());
        String method   = op.http() != null ? op.http().method().toLowerCase() : "post";
        String uri      = op.http() != null ? toPlugUri(op.http().uriTemplate(), op.labels()) : "/";
        int successCode = op.http() != null ? op.http().successCode() : 200;

        List<String> requiredFields = new ArrayList<>();
        if (op.labels() != null) {
            for (LabelBinding lb : op.labels()) {
                requiredFields.add(":" + ElixirSymbolProvider.toSnakeCase(lb.smithyMemberName()));
            }
        }
        String requiredList = "[" + String.join(", ", requiredFields) + "]";

        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(method).append(" \"").append(uri).append("\" do\n");
        sb.append("    input = deserialize_").append(opName).append("(conn)\n");
        sb.append("    with :ok <- SmithyValidator.validate(input, ").append(requiredList).append("),\n");
        sb.append("         {:ok, output} <- handler().").append(opName).append("(input, %{}) do\n");
        sb.append("      SmithyServer.response(conn, ").append(successCode)
          .append(", serialize_").append(opName).append("(output))\n");
        sb.append("    else\n");
        sb.append("      {:error, {:missing_required_fields, _} = reason} ->\n");
        sb.append("        SmithyServer.validation_error(conn, reason)\n");
        sb.append("      {:error, err} ->\n");
        sb.append("        SmithyServer.error_response(conn, err)\n");
        sb.append("    end\n");
        sb.append("  end\n\n");
        return sb.toString();
    }

    /**
     * Returns the Plug.Router catch-all route that returns a 404 response.
     *
     * <p>Example output:
     * <pre>
     *   match _ do
     *     SmithyServer.not_found(conn)
     *   end
     * </pre>
     */
    @Override
    public String renderServerRouteFallback() {
        return "  match _ do\n"
             + "    SmithyServer.not_found(conn)\n"
             + "  end\n";
    }

    /**
     * Elixir uses Plug.Router's built-in dispatch mechanism; no separate handle
     * function is needed.  Returns an empty string.
     */
    @Override
    public String renderServerHandleFunction(List<OperationSpec> ops, String svcModuleName) {
        return "";
    }

    /**
     * Renders the Elixir dispatch private function for one server operation.
     *
     * <p>This is folded into the route block in {@link #renderServerRouteClause};
     * the standalone dispatch function is emitted here for use when the pipeline
     * calls the method directly (e.g. multi-file generation).
     *
     * <p>Example output:
     * <pre>
     *   defp dispatch_get_weather(conn) do
     *     input = deserialize_get_weather(conn)
     *     case handler().get_weather(input, %{}) do
     *       {:ok, output} -> SmithyServer.response(conn, 200, serialize_get_weather(output))
     *       {:error, err} -> SmithyServer.error_response(conn, err)
     *     end
     *   end
     * </pre>
     */
    @Override
    public String renderServerDispatchClause(OperationSpec op) {
        String opName      = ElixirSymbolProvider.toFunctionName(op.operationName());
        int successCode    = op.http() != null ? op.http().successCode() : 200;
        return "  defp dispatch_" + opName + "(conn) do\n"
             + "    input = deserialize_" + opName + "(conn)\n"
             + "    case handler()." + opName + "(input, %{}) do\n"
             + "      {:ok, output} -> SmithyServer.response(conn, " + successCode
             +            ", serialize_" + opName + "(output))\n"
             + "      {:error, err} -> SmithyServer.error_response(conn, err)\n"
             + "    end\n"
             + "  end\n";
    }

    /**
     * Renders the Elixir {@code deserialize_<op>/1} private function.
     *
     * <p>Extracts path parameters and body members from the {@code conn} object.
     *
     * <p>Example output:
     * <pre>
     *   defp deserialize_get_weather(conn) do
     *     %{city: conn.path_params["city"]}
     *   end
     * </pre>
     */
    @Override
    public String renderServerDeserialize(OperationSpec op) {
        String opName       = ElixirSymbolProvider.toFunctionName(op.operationName());
        List<LabelBinding>  labels      = op.labels() != null ? op.labels() : List.of();
        List<String>        bodyMembers = op.body() != null && !op.body().bodyMemberNames().isEmpty()
                                        ? op.body().bodyMemberNames() : List.of();

        StringBuilder sb = new StringBuilder();
        sb.append("  defp deserialize_").append(opName).append("(conn) do\n");

        List<String> entries = new ArrayList<>();
        for (LabelBinding lb : labels) {
            String key    = ":" + ElixirSymbolProvider.toSnakeCase(lb.smithyMemberName());
            String access = "conn.path_params[\"" + lb.smithyMemberName() + "\"]";
            entries.add(key + " => " + access);
        }
        if (!bodyMembers.isEmpty()) {
            sb.append("    {:ok, body_raw, _conn} = Plug.Conn.read_body(conn)\n");
            sb.append("    {:ok, body} = Jason.decode(body_raw)\n");
            for (String m : bodyMembers) {
                String key    = ":" + ElixirSymbolProvider.toSnakeCase(m);
                String access = "Map.get(body, \"" + m + "\")";
                entries.add(key + " => " + access);
            }
        }

        if (entries.isEmpty()) {
            sb.append("    %{}\n");
        } else {
            sb.append("    %{").append(String.join(", ", entries)).append("}\n");
        }
        sb.append("  end\n");
        return sb.toString();
    }

    /**
     * Renders the Elixir {@code serialize_<op>/1} private function.
     *
     * <p>Example output:
     * <pre>
     *   defp serialize_get_weather(output), do: Jason.encode!(output)
     * </pre>
     */
    @Override
    public String renderServerSerialize(OperationSpec op) {
        String opName = ElixirSymbolProvider.toFunctionName(op.operationName());
        return "  defp serialize_" + opName + "(output), do: Jason.encode!(output)\n";
    }

    /**
     * Renders the Elixir impl scaffold stub for one server operation.
     *
     * <p>Example output:
     * <pre>
     *   def get_weather(_input, _ctx), do: {:error, :not_implemented}
     * </pre>
     */
    @Override
    public String renderServerImplStub(OperationSpec op, String handlerModuleName) {
        String opName = ElixirSymbolProvider.toFunctionName(op.operationName());
        return "  def " + opName + "(_input, _ctx), do: {:error, :not_implemented}\n";
    }

    /**
     * Returns the server runtime module paths to copy alongside generated server files.
     */
    @Override
    public List<String> serverRuntimeModules() {
        return List.of(
                "server/smithy_server.ex",
                "server/smithy_validator.ex",
                "server/smithy_error_map.ex"
        );
    }

    /**
     * Returns the complete source of a single consolidated Elixir server module.
     *
     * <p>The generated module uses {@code Plug.Router} for routing and dispatching.
     * It contains:
     * <ol>
     *   <li>{@code defmodule <BaseName>.Dispatcher do}</li>
     *   <li>{@code use Plug.Router} + plug directives</li>
     *   <li>Type definitions (@type)</li>
     *   <li>Route blocks (one per operation + catch-all)</li>
     *   <li>Private deserialise/serialise helpers</li>
     *   <li>Handler accessor</li>
     *   <li>end</li>
     * </ol>
     *
     * <p>A companion {@code <BaseName>.Handler} behaviour module and
     * {@code <BaseName>.Impl} scaffold are also emitted as separate sections
     * separated by {@code "---"} comments that the pipeline splits into files.
     */
    @Override
    public String renderServerModule(String baseName, List<OperationSpec> ops, ModuleTypeSpec types) {
        String moduleName  = ElixirSymbolProvider.toModuleName(baseName);
        String implModule  = moduleName + ".Impl";
        String handlerModule    = moduleName + ".Handler";
        String dispatcherModule = moduleName + ".Dispatcher";

        StringBuilder sb = new StringBuilder();

        // ── Dispatcher (Plug.Router) ──────────────────────────────────────────
        sb.append("defmodule ").append(dispatcherModule).append(" do\n");
        sb.append("  @moduledoc \"Generated Smithy server dispatcher for ").append(moduleName).append(". Do not edit.\"\n\n");
        sb.append("  use Plug.Router\n");
        sb.append("  plug :match\n");
        sb.append("  plug :dispatch\n\n");

        // Type definitions
        for (StructSpec s : types.structures()) sb.append(renderStructType(s));
        for (EnumSpec   e : types.enums())      sb.append(renderEnumType(e));
        for (UnionSpec  u : types.unions())     sb.append(renderUnionType(u));
        for (StructSpec e : types.errors())     sb.append(renderStructType(e));
        if (!types.structures().isEmpty() || !types.enums().isEmpty()
                || !types.unions().isEmpty() || !types.errors().isEmpty()) {
            sb.append("\n");
        }

        // Route blocks
        for (OperationSpec op : ops) {
            sb.append(renderServerRouteClause(op));
        }
        sb.append(renderServerRouteFallback());
        sb.append("\n");

        // Private helpers
        for (OperationSpec op : ops) {
            sb.append(renderServerDeserialize(op));
            sb.append("\n");
            sb.append(renderServerSerialize(op));
            sb.append("\n");
        }

        // Handler accessor — returns the implementation module atom
        sb.append("  defp handler, do: ").append(implModule).append("\n");
        sb.append("end\n");

        // ── Handler behaviour module ──────────────────────────────────────────
        sb.append("\n");
        sb.append("defmodule ").append(handlerModule).append(" do\n");
        sb.append("  @moduledoc \"Behaviour for the ").append(moduleName).append(" server handler.\"\n\n");
        for (OperationSpec op : ops) {
            sb.append(renderServerCallbackDeclaration(op));
        }
        sb.append("end\n");

        return sb.toString();
    }

    /**
     * Returns the complete source of the once-written Elixir impl scaffold.
     *
     * <p>Uses Elixir dot-notation module names ({@code WeatherService.Impl},
     * {@code WeatherService.Handler}) that the generic pipeline fallback cannot
     * produce from the snake_case base name.
     *
     * <p>Example output:
     * <pre>
     * defmodule WeatherService.Impl do
     *   @moduledoc false
     *   @behaviour WeatherService.Handler
     *
     *   # This file will NOT be overwritten. Add your business logic here.
     *
     *   def get_weather(_input, _ctx), do: {:error, :not_implemented}
     * end
     * </pre>
     */
    @Override
    public String renderServerImplContent(String baseName, List<OperationSpec> ops) {
        String moduleName    = ElixirSymbolProvider.toModuleName(baseName);
        String implModule    = moduleName + ".Impl";
        String handlerModule = moduleName + ".Handler";

        StringBuilder sb = new StringBuilder();
        sb.append("defmodule ").append(implModule).append(" do\n");
        sb.append("  @moduledoc false\n");
        sb.append("  @behaviour ").append(handlerModule).append("\n\n");
        sb.append("  # This file will NOT be overwritten. Add your business logic here.\n\n");
        for (OperationSpec op : ops) {
            sb.append(renderServerImplStub(op, handlerModule));
        }
        sb.append("end\n");
        return sb.toString();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Converts a Smithy URI template to a Plug.Router path pattern.
     *
     * <p>Smithy: {@code /weather/{city}} → Plug: {@code /weather/:city}
     */
    private static String toPlugUri(String template, List<LabelBinding> labels) {
        if (labels == null || labels.isEmpty()) {
            return template;
        }
        String result = template;
        for (LabelBinding lb : labels) {
            String snakeName = ElixirSymbolProvider.toSnakeCase(lb.smithyMemberName());
            result = result.replace("{" + lb.uriPlaceholder() + "}", ":" + snakeName);
        }
        return result;
    }

    /**
     * Converts a Smithy enum value to a full Elixir atom literal (with colon prefix).
     *
     * <p>Simple atoms (only {@code [a-z0-9_]}, starting with a letter) are rendered as
     * {@code :name}. Atoms that contain dots or other special characters are quoted:
     * {@code :"automation.changetemplate"}.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "CELSIUS"} → {@code :celsius}</li>
     *   <li>{@code "US-EAST-1"} → {@code :us_east_1}</li>
     *   <li>{@code "Automation.ChangeTemplate"} → {@code :"automation.changetemplate"}</li>
     * </ul>
     */
    private static String toElixirAtom(String smithyValue) {
        String lower = smithyValue.toLowerCase().replace('-', '_');
        if (lower.matches("[a-z][a-z0-9_]*[?!]?")) {
            return ":" + lower;
        }
        // Atom contains special chars (e.g. dots); use quoted-atom syntax :"..."
        return ":\"" + lower + "\"";
    }

    /** @deprecated Use {@link #toElixirAtom(String)} which handles quoting. */
    private static String toElixirAtomValue(String value) {
        return value.toLowerCase().replace('-', '_');
    }

    /**
     * Converts a {@link TypeRef} to the corresponding Elixir type annotation string.
     *
     * <p>Named types within the same module use the {@code snake_case()} convention.
     * Optional types include {@code | nil}.
     */
    private String typeRefToElixir(TypeRef ref) {
        if (ref instanceof TypeRef.Primitive p) {
            return primitiveToElixir(p.kind());
        } else if (ref instanceof TypeRef.Named n) {
            return ElixirSymbolProvider.toInlineTypeName(n.name());
        } else if (ref instanceof TypeRef.ListOf l) {
            return "[" + typeRefToElixir(l.element()) + "]";
        } else if (ref instanceof TypeRef.MapOf) {
            return "map()";
        } else if (ref instanceof TypeRef.Optional o) {
            return typeRefToElixir(o.inner()) + " | nil";
        }
        return "term()";
    }

    private static String primitiveToElixir(PrimitiveKind kind) {
        switch (kind) {
            case STRING:    return "String.t()";
            case INTEGER:
            case LONG:      return "integer()";
            case FLOAT:
            case DOUBLE:    return "float()";
            case BOOLEAN:   return "boolean()";
            case BLOB:      return "binary()";
            case TIMESTAMP: return "DateTime.t()";
            default:        return "term()";
        }
    }
}
