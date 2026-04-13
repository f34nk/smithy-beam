package io.smithy.beam.core.writer;

import io.smithy.beam.core.ir.AuthSpec;
import io.smithy.beam.core.ir.EnumSpec;
import io.smithy.beam.core.ir.ErrorBinding;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.ErrorSpec;
import io.smithy.beam.core.ir.HeaderBinding;
import io.smithy.beam.core.ir.LabelBinding;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.PaginationSpec;
import io.smithy.beam.core.ir.QueryBinding;
import io.smithy.beam.core.ir.RetrySpec;
import io.smithy.beam.core.ir.StructSpec;
import io.smithy.beam.core.ir.TypeRef;
import io.smithy.beam.core.ir.UnionSpec;

import java.util.List;

public interface LanguageWriter {

    String languageId();

    String fileExtension();

    String moduleName(String smithyName);

    String functionName(String smithyName);

    String typeName(String smithyName);

    String varName(String smithyName);

    String mapKey(String smithyMemberName);

    String moduleHeader(String name);

    String moduleFooter();

    /**
     * Returns a module-level comment placed immediately after the module header.
     *
     * <p>Erlang: {@code "%% <text>\n"}
     * <p>Other languages that do not need module comments may return {@code ""}.
     */
    default String renderModuleComment(String text) {
        return "";
    }

    String exportSection(List<ExportSpec> exports);

    String behaviourDeclaration(String behaviourName);

    String renderStructType(StructSpec struct);

    String renderEnumType(EnumSpec e);

    String renderUnionType(UnionSpec u);

    String renderCallbackDeclaration(String name, List<ParamSpec> params, TypeRef returnType);

    String renderFunctionSpec(String name, List<ParamSpec> params, TypeRef returnType);

    String renderFunctionHead(String name, List<String> paramPatterns);

    String renderFunctionEnd();

    String renderMapGet(String mapVar, String smithyMemberName, String defaultVal);

    String renderMapBuild(List<MapEntrySpec> entries);

    String renderJsonEncode(String mapVar);

    String renderJsonDecode(String bodyVar);

    String renderXmlEncode(String mapVar, String rootElement);

    String renderXmlDecode(String bodyVar);

    String renderFormEncode(String mapVar);

    String renderUriSubstitution(String template, List<LabelBinding> labels, String inputVar);

    String renderQueryStringBuilder(List<QueryBinding> queries, String inputVar);

    String renderHeaderBuilder(String contentType, List<HeaderBinding> headers, String inputVar);

    String renderHttpClientBlock(
            OperationSpec spec,
            String urlVar,
            String headersVar,
            String bodyVar,
            String methodVar);

    String renderAuthWrapper(AuthSpec auth, String innerBlock);

    String renderRetryWrapper(RetrySpec retry, String requestFunVar);

    String renderResponseHandler(OperationSpec spec, String responseVar);

    String renderErrorSerializer(ErrorSpec errors);

    String renderPaginationHelper(OperationSpec spec, PaginationSpec pagination);

    String jsonEncodeCall(String expr);

    String jsonDecodeCall(String expr);

    String sigv4SignCall();

    String retryCall(String funExpr, String optsExpr);

    // ── Module-level attributes ───────────────────────────────────────────────

    /**
     * Returns language-specific tooling suppression attributes placed once
     * after the module header.
     *
     * <p>Erlang: {@code -dialyzer([no_contracts, no_match]).\n\n}
     * <p>Elixir: {@code ""}
     */
    String renderToolingAttributes();

    /**
     * Returns a type-export declaration for the given type names (arity 0).
     *
     * <p>Erlang: {@code -export_type([t1/0, t2/0]).\n}
     * <p>Elixir: {@code ""} (types are exported automatically)
     */
    String exportTypes(List<String> typeNames);

    /**
     * Returns the client constructor function that wraps a config map.
     *
     * <p>Erlang:
     * <pre>
     * -spec new(map()) -> {ok, map()}.
     * new(Config) -> {ok, Config}.
     * </pre>
     */
    String renderClientConstructor();

    // ── Operation rendering ───────────────────────────────────────────────────

    /**
     * Returns the complete source block for one client operation: all specs,
     * public arities, internal request builder, URL construction, body
     * serialisation, header assembly, auth, HTTP dispatch, and response parsing.
     */
    String renderClientOperation(OperationSpec op);

    // ── Codec helpers ─────────────────────────────────────────────────────────

    /**
     * Returns {@code encode_<enum>/1} and {@code decode_<enum>/1} functions
     * for the given enum.
     */
    String renderEnumCodec(EnumSpec e);

    /**
     * Returns {@code encode_<union>/1} and {@code decode_<union>/1} functions
     * for the given union.
     */
    String renderUnionCodec(UnionSpec u);

    /**
     * Returns a {@code validate_<struct>/1} function that checks required fields.
     * Returns {@code ""} if the struct has no required fields.
     */
    String renderValidateHelper(StructSpec s);

    /**
     * Returns shared internal helper functions used by the generated module
     * (e.g. {@code url_encode/1} and {@code ensure_binary/1} in Erlang).
     *
     * <p>Returns {@code ""} for languages that do not need them.
     */
    String renderSharedHelpers();

    // ── Error dispatch ────────────────────────────────────────────────────────

    /**
     * Returns the error dispatch function that maps errors to modelled error terms.
     *
     * <p>Receives a deduplicated, ordered list of error bindings aggregated
     * across all operations in the module.
     */
    String renderModuleParseError(List<ErrorBinding> errors);

    /**
     * Protocol-aware overload.
     *
     * <ul>
     *   <li>{@code REST_XML} — dispatches on XML {@code <Code>} binary string; returns structured maps.</li>
     *   <li>{@code AWS_JSON} — dispatches on JSON {@code __type} binary string; returns structured maps.</li>
     *   <li>{@code REST_JSON} / {@code AWS_QUERY} — dispatches on HTTP status code integer; returns tuples.</li>
     * </ul>
     */
    default String renderModuleParseError(List<ErrorBinding> errors, ErrorCodeStrategy strategy) {
        return renderModuleParseError(errors);
    }

    // ── Runtime module discovery ──────────────────────────────────────────────

    /**
     * Returns the list of runtime resource paths that must be copied into the
     * output directory for a client module.
     *
     * @param needsSigV4  true if any operation requires SigV4 signing
     * @param needsXml    true if the protocol uses XML body encoding
     * @param needsQuery  true if the protocol uses form-urlencoded encoding
     * @param needsS3     true if the service is an S3-family service
     */
    List<String> clientRuntimeModules(
            boolean needsSigV4,
            boolean needsXml,
            boolean needsQuery,
            boolean needsS3);
}
