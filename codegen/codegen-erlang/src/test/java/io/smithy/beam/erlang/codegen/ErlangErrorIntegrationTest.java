package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamPreludeIntegration;
import io.smithy.beam.core.Mode;
import io.smithy.beam.erlang.client.ErlangClientSettings;
import io.smithy.beam.erlang.codegen.sections.ServiceErrorHelpersSection;
import io.smithy.beam.erlang.server.ErlangServerSettings;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Unit tests for {@link ErlangErrorIntegration}.
 *
 * <p>Drives the integration against a small fixture and asserts that the
 * service-level error reflection helpers ({@code errors/0},
 * {@code is_error/1}, {@code error_to_atom/1}) are emitted as a coherent
 * triple:
 *
 * <ul>
 *   <li>{@code errors/0} returns the union of operation-level and
 *       service-level error atoms, deduplicated and snake-cased.</li>
 *   <li>{@code is_error/1} and {@code error_to_atom/1} are emitted with
 *       fixed bodies that delegate to {@code errors/0}, so the helper trio
 *       always agrees on the canonical error set.</li>
 *   <li>The integration is a no-op for services with no declared errors —
 *       except that {@code errors/0} still returns {@code []}, so callers
 *       can rely on the helpers' presence regardless of model shape.</li>
 *   <li>The integration is mode-gated: it emits nothing when the
 *       surrounding settings declare {@link Mode#SERVER}.</li>
 * </ul>
 */
class ErlangErrorIntegrationTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.errors",
            "",
            "service Svc {",
            "    version: \"2024\"",
            "    operations: [GetItem, ListItems, Ping]",
            "    errors: [InternalServerError]",
            "}",
            "",
            "@readonly",
            "operation GetItem {",
            "    input: GetItemInput",
            "    output: GetItemOutput",
            "    errors: [NotFound, Throttled]",
            "}",
            "",
            "@readonly",
            "operation ListItems {",
            "    input: ListItemsInput",
            "    output: ListItemsOutput",
            "    errors: [Throttled]",
            "}",
            "",
            "operation Ping {}",
            "",
            "structure GetItemInput  { id: String }",
            "structure GetItemOutput { value: String }",
            "structure ListItemsInput  { token: String }",
            "structure ListItemsOutput { token: String }",
            "",
            "@error(\"client\")",
            "structure NotFound { message: String }",
            "",
            "@error(\"client\")",
            "structure Throttled { message: String }",
            "",
            "@error(\"server\")",
            "structure InternalServerError { message: String }");

    private static final String EMPTY_MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.empty",
            "",
            "service Empty {",
            "    version: \"2024\"",
            "    operations: [Ping]",
            "}",
            "",
            "operation Ping {}");

    private static ErlangContext clientCtx;
    private static ErlangContext emptyClientCtx;
    private static ErlangContext serverCtx;
    private static ServiceShape service;
    private static ServiceShape emptyService;

    @BeforeAll
    static void setUp() {
        clientCtx = clientContext(MODEL, "test.errors#Svc", "svc");
        emptyClientCtx = clientContext(EMPTY_MODEL, "test.empty#Empty", "empty");
        serverCtx = serverContext(MODEL, "test.errors#Svc", "svc");
        service = clientCtx.service();
        emptyService = emptyClientCtx.service();
    }

    @Test
    void emitsErrorsListWithUnionOfOperationAndServiceErrors() {
        String out = drive(clientCtx, service);

        assertThat(out).contains("errors() ->");
        // All three errors must appear in the deterministic snake_case form.
        assertThat(out).contains("not_found");
        assertThat(out).contains("throttled");
        assertThat(out).contains("internal_server_error");
    }

    @Test
    void deduplicatesErrorsSharedAcrossOperations() {
        // Throttled is listed by both GetItem and ListItems but must appear
        // exactly once in the errors/0 result list.
        String out = drive(clientCtx, service);

        int firstIdx = out.indexOf("throttled");
        int lastIdx = out.lastIndexOf("throttled");
        // First and last occurrence inside errors/0 must be the same — any
        // duplication in the list literal would push the indices apart.
        // (error_to_atom/1 / is_error/1 do not mention error names, so any
        // additional occurrence is necessarily a list-literal duplicate.)
        assertThat(firstIdx).isEqualTo(lastIdx);
    }

    @Test
    void emitsIsErrorPredicateThatDelegatesToErrorsList() {
        String out = drive(clientCtx, service);

        assertThat(out)
                .contains("is_error({error, Err})")
                .contains("lists:member(element(1, Err), errors())")
                .contains("is_error(_) ->")
                .contains("    false.");
    }

    @Test
    void emitsErrorToAtomMappingThatFallsBackToUnknownError() {
        String out = drive(clientCtx, service);

        assertThat(out)
                .contains("error_to_atom({error, Err})")
                .contains("case lists:member(element(1, Err), errors()) of")
                .contains("true -> element(1, Err);")
                .contains("false -> unknown_error")
                .contains("error_to_atom(_) ->")
                .contains("    unknown_error.");
    }

    @Test
    void registersHelperExports() {
        ErlangWriter w = drivenWriter(clientCtx, service);

        // The exports list is rendered in the module header on toString().
        String out = w.toString();
        assertThat(out)
                .contains("errors/0")
                .contains("is_error/1")
                .contains("error_to_atom/1");
    }

    @Test
    void emitsEmptyErrorsListForServicesWithNoDeclaredErrors() {
        String out = drive(emptyClientCtx, emptyService);

        // The helper triple is always emitted — empty errors/0 keeps the
        // contract uniform across services.
        assertThat(out)
                .contains("errors() ->")
                .contains("    [].")
                .contains("is_error(")
                .contains("error_to_atom(");
    }

    @Test
    void emitsNothingInServerMode() {
        String out = drive(serverCtx, service);

        // The integration is a no-op for server-mode codegen.
        assertThat(out)
                .doesNotContain("errors() ->")
                .doesNotContain("is_error(")
                .doesNotContain("error_to_atom(");
    }

    /**
     * Renders the {@link ServiceErrorHelpersSection} through the integration
     * into a fresh writer and returns the body content. Mirrors the harness
     * used by {@code ErlangSpecIntegrationTest}.
     */
    private static String drive(ErlangContext ctx, ServiceShape svc) {
        return drivenWriter(ctx, svc).toString();
    }

    private static ErlangWriter drivenWriter(ErlangContext ctx, ServiceShape svc) {
        ErlangWriter w = CodegenTestSupport.writer(
                ctx.settings().getModule() + "_client.erl");
        List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors =
                new ErlangErrorIntegration().interceptors(ctx);
        for (CodeInterceptor<? extends CodeSection, ErlangWriter> i : interceptors) {
            w.onSection(i);
        }
        w.injectSection(new ServiceErrorHelpersSection(svc));
        return w;
    }

    /**
     * Builds a client-mode {@link ErlangContext}, mirroring the prelude
     * preprocessing the codegen director performs at runtime so service-level
     * errors are fanned out onto every operation before the integration runs.
     */
    private static ErlangContext clientContext(String smithy, String serviceId, String module) {
        Model raw = Model.assembler()
                .discoverModels(ErlangErrorIntegrationTest.class.getClassLoader())
                .addUnparsedModel("test.smithy", smithy)
                .assemble()
                .unwrap();

        ErlangClientSettings settings = new ErlangClientSettings();
        settings.setService(ShapeId.from(serviceId));
        settings.setModule(module);
        settings.setEdition("2025");

        Model preprocessed = new BeamPreludeIntegration<
                ErlangSettings, ErlangWriter, ErlangContext>() {}
                .preprocessModel(raw, settings);

        ServiceShape svc = preprocessed.expectShape(
                ShapeId.from(serviceId), ServiceShape.class);
        SymbolProvider symbols = new ErlangSymbolProvider(preprocessed, settings, Mode.CLIENT);

        return new ErlangContext(
                preprocessed,
                ModelTransformer.create(),
                settings,
                symbols,
                null,
                null,
                List.of(),
                svc);
    }

    /**
     * Builds a server-mode {@link ErlangContext} for the mode-gating test.
     * Preprocessing isn't strictly necessary in server mode for these
     * helpers (the integration short-circuits), but mirroring the runtime
     * pipeline keeps the fixture honest.
     */
    private static ErlangContext serverContext(String smithy, String serviceId, String module) {
        Model raw = Model.assembler()
                .discoverModels(ErlangErrorIntegrationTest.class.getClassLoader())
                .addUnparsedModel("test.smithy", smithy)
                .assemble()
                .unwrap();

        ErlangServerSettings settings = new ErlangServerSettings();
        settings.setService(ShapeId.from(serviceId));
        settings.setModule(module);
        settings.setEdition("2025");

        ServiceShape svc = raw.expectShape(ShapeId.from(serviceId), ServiceShape.class);
        SymbolProvider symbols = new ErlangSymbolProvider(raw, settings, Mode.SERVER);

        return new ErlangContext(
                raw,
                ModelTransformer.create(),
                settings,
                symbols,
                null,
                null,
                List.of(),
                svc);
    }
}
