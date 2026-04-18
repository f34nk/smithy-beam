package io.smithy.beam.core.writer;

import io.smithy.beam.core.ir.EnumSpec;
import io.smithy.beam.core.ir.ErrorBinding;
import io.smithy.beam.core.ir.ErrorCodeStrategy;
import io.smithy.beam.core.ir.ModuleTypeSpec;
import io.smithy.beam.core.ir.OperationSpec;
import io.smithy.beam.core.ir.StructSpec;
import io.smithy.beam.core.ir.UnionSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * The complete public surface of a language-specific code emitter.
 *
 * <p>Implementations return pure source text for one target language.
 * Each method is a pure function of its arguments — no mutable state,
 * no side effects.
 *
 * <p><b>This is the entire public surface; do not add methods without a
 * corresponding pipeline call site.</b>
 *
 * <h2>Method groups</h2>
 * <ul>
 *   <li><b>Identity &amp; naming</b> (7): noun-form primitives — {@code languageId}, {@code fileExtension},
 *       {@code moduleName}, {@code functionName}, {@code typeName}, {@code varName}, {@code mapKey}.</li>
 *   <li><b>File structure</b> (7): {@code moduleHeader}, {@code moduleFooter}, {@code renderModuleComment},
 *       {@code renderExportSection}, {@code renderBehaviourDeclaration}, {@code renderToolingAttributes},
 *       {@code renderExportTypes}.</li>
 *   <li><b>Type &amp; codec</b> (6): {@code renderStructType}, {@code renderEnumType}, {@code renderUnionType},
 *       {@code renderEnumCodec}, {@code renderUnionCodec}, {@code renderValidateHelper}.</li>
 *   <li><b>Operations &amp; module-level helpers</b> (4): {@code renderClientConstructor}, {@code renderClientOperation},
 *       {@code renderSharedHelpers}, {@code renderClientModule}.</li>
 *   <li><b>Error dispatch</b> (1): {@code renderModuleParseError}.</li>
 *   <li><b>Runtime discovery</b> (1): {@code clientRuntimeModules}.</li>
 *   <li><b>Server</b> (4): {@code renderServerModule}, {@code renderServerImplContent},
 *       {@code renderServerImplStub}, {@code serverRuntimeModules}.</li>
 * </ul>
 */
public interface LanguageWriter {

    // ── Identity & naming ─────────────────────────────────────────────────────

    String languageId();

    String fileExtension();

    String moduleName(String smithyName);

    String functionName(String smithyName);

    String typeName(String smithyName);

    String varName(String smithyName);

    String mapKey(String smithyMemberName);

    // ── File structure ────────────────────────────────────────────────────────

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

    String renderExportSection(List<ExportSpec> exports);

    String renderBehaviourDeclaration(String behaviourName);

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
    String renderExportTypes(List<String> typeNames);

    // ── Type & codec ──────────────────────────────────────────────────────────

    String renderStructType(StructSpec struct);

    String renderEnumType(EnumSpec e);

    String renderUnionType(UnionSpec u);

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

    // ── Operations & module-level helpers ─────────────────────────────────────

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

    /**
     * Returns the complete source block for one client operation: all specs,
     * public arities, internal request builder, URL construction, body
     * serialisation, header assembly, auth, HTTP dispatch, and response parsing.
     */
    String renderClientOperation(OperationSpec op);

    /**
     * Returns shared internal helper functions used by the generated module,
     * given the full list of operations so the writer can omit helpers that are
     * not referenced by any operation (avoiding unused function warnings in strict
     * compilers like Erlang).
     *
     * <p>Returns {@code ""} for languages that do not need shared helpers.
     */
    default String renderSharedHelpers(List<OperationSpec> ops) {
        return "";
    }

    // ── Whole-file client emission ────────────────────────────────────────────

    /**
     * Returns the complete source text of a client module.
     *
     * <p>Assembles all sections — module header, exports, type definitions, codec
     * functions, client constructor, shared helpers, per-operation functions,
     * validation helpers, error dispatch, and module footer — into one string.
     *
     * <p>The default returns {@code ""} to preserve backward compatibility during
     * the pipeline unification transition. Writers that have not yet overridden
     * this method cause {@link io.smithy.beam.core.pipeline.ClientPipeline} to
     * fall back to its own assembly logic. Language-specific writers should override
     * this method to centralise all text-assembly in the writer.
     *
     * @param moduleName    the base name before writer-specific transformation
     *                      (e.g. {@code "WeatherService"})
     * @param ops           analysed client operations
     * @param types         reachable type shapes
     * @param errorStrategy protocol error-dispatch strategy
     */
    default String renderClientModule(String moduleName,
                                      List<OperationSpec> ops,
                                      ModuleTypeSpec types,
                                      ErrorCodeStrategy errorStrategy) {
        return "";
    }

    // ── Error dispatch ────────────────────────────────────────────────────────

    /**
     * Returns the error dispatch function that maps errors to modelled error terms.
     *
     * <ul>
     *   <li>{@code REST_XML} — dispatches on XML {@code <Code>} binary string; returns structured maps.</li>
     *   <li>{@code AWS_JSON} — dispatches on JSON {@code __type} binary string; returns structured maps.</li>
     *   <li>{@code REST_JSON} / {@code AWS_QUERY} — dispatches on HTTP status code integer; returns tuples.</li>
     * </ul>
     */
    default String renderModuleParseError(List<ErrorBinding> errors, ErrorCodeStrategy strategy) {
        return "";
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

    // ── Server-side rendering ─────────────────────────────────────────────────

    /**
     * Returns a stub function body for the impl scaffold (written once, never overwritten).
     *
     * <p>{@code handlerModuleName} is the fully-qualified name of the companion behaviour module
     * (e.g. {@code weather_service_handler}). Implementations that emit typed specs should use
     * remote type references ({@code handlerModuleName:type_name()}) so the impl module compiles
     * without needing to re-declare or import types from the handler.
     *
     * <p>Erlang example:
     * <pre>
     * -spec get_weather(weather_service_handler:get_weather_input(), map()) ->
     *     {ok, weather_service_handler:get_weather_output()} | {error, term()}.
     * get_weather(_Input, _Context) ->
     *     {error, not_implemented}.
     * </pre>
     */
    default String renderServerImplStub(OperationSpec op, String handlerModuleName) {
        return "";
    }

    /**
     * Returns the list of server runtime resource paths that must be copied into
     * the output directory alongside the generated server files.
     */
    default List<String> serverRuntimeModules() {
        return List.of();
    }

    /**
     * Returns the complete source of a single consolidated server module that
     * combines type definitions, behaviour callbacks, routing, dispatch, and the
     * framework HTTP entry point into one file.
     *
     * <p>The default returns an empty string; override in language-specific writers.
     *
     * @param baseName the module base name (e.g. {@code "weather_service"})
     * @param ops      the list of analysed server operations
     * @param types    the reachable type shapes
     */
    default String renderServerModule(String baseName, List<OperationSpec> ops, ModuleTypeSpec types) {
        return "";
    }

    /**
     * Returns the complete source of the once-written impl scaffold file.
     *
     * <p>The default implementation produces a language-agnostic scaffold that
     * declares the behaviour and provides one stub function per operation via
     * {@link #renderServerImplStub}. Override when the language's module-naming
     * conventions differ from the default snake_case derivation (e.g. Elixir uses
     * {@code WeatherService.Impl} rather than {@code weather_service_impl}).
     *
     * @param baseName the module base name (e.g. {@code "weather_service"})
     * @param ops      the list of analysed server operations
     */
    default String renderServerImplContent(String baseName, List<OperationSpec> ops) {
        StringBuilder buf = new StringBuilder();
        buf.append(moduleHeader(baseName + "_impl"));
        buf.append(renderModuleComment(
                "This file will NOT be overwritten. Add your business logic here."));
        buf.append(renderBehaviourDeclaration(baseName + "_server"));
        buf.append("\n");

        List<ExportSpec> exports = new ArrayList<>();
        for (OperationSpec op : ops) {
            exports.add(new ExportSpec(functionName(op.operationName()), 2));
        }
        buf.append(renderExportSection(exports));
        buf.append("\n");

        String handlerModuleName = baseName + "_server";
        for (OperationSpec op : ops) {
            buf.append(renderServerImplStub(op, handlerModuleName));
            buf.append("\n");
        }
        buf.append(moduleFooter());
        return buf.toString();
    }
}
