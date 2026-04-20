package io.smithy.beam.erlang.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamPreludeIntegration;
import io.smithy.beam.core.Mode;
import io.smithy.beam.erlang.client.ErlangClientSettings;
import io.smithy.beam.erlang.codegen.CodegenTestSupport;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangSettings;
import io.smithy.beam.erlang.codegen.ErlangSymbolProvider;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import io.smithy.beam.erlang.codegen.sections.ServerHandlerCallbackSection;
import io.smithy.beam.erlang.codegen.sections.ServerImplCallbackSection;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.transform.ModelTransformer;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Unit tests for {@link ErlangServerCallbackIntegration}.
 *
 * <p>Drives the integration against a small fixture and asserts:
 *
 * <ul>
 *   <li>An empty {@link ServerImplCallbackSection} produces a well-formed
 *       {@code -callback <op>/2} declaration with input/output type aliases
 *       and a precise error union.</li>
 *   <li>Service-level errors are merged into every operation's error union by
 *       {@link io.smithy.beam.core.BeamPreludeIntegration#preprocessModel}.</li>
 *   <li>A {@link ServerHandlerCallbackSection} wrapping the existing
 *       {@code handle_<op>(Req, State)} stub is preceded by a
 *       protocol-agnostic {@code -spec handle_<op>/2} line.</li>
 *   <li>Operations whose input or output is the unit shape emit {@code term()}
 *       for the corresponding position in the {@code -callback}.</li>
 *   <li>Client-mode contexts emit no callback or handler spec — that is owned
 *       by the client's {@code -spec} integration.</li>
 * </ul>
 */
class ErlangServerCallbackIntegrationTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.spec",
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

    private static ErlangContext serverCtx;
    private static ErlangContext clientCtx;
    private static Model model;

    @BeforeAll
    static void setUp() {
        // Mirror the prelude integration so service-level errors are fanned out
        // onto every operation before the integration runs — that is what the
        // codegen director does at runtime.
        Model raw = Model.assembler()
                .discoverModels(ErlangServerCallbackIntegrationTest.class.getClassLoader())
                .addUnparsedModel("test.smithy", MODEL)
                .assemble()
                .unwrap();

        ErlangServerSettings serverSettings = new ErlangServerSettings();
        serverSettings.setService(ShapeId.from("test.spec#Svc"));
        serverSettings.setModule("svc");
        serverSettings.setEdition("2025");

        ErlangClientSettings clientSettings = new ErlangClientSettings();
        clientSettings.setService(ShapeId.from("test.spec#Svc"));
        clientSettings.setModule("svc");
        clientSettings.setEdition("2025");

        Model preprocessed = new BeamPreludeIntegration<
                ErlangSettings, ErlangWriter, ErlangContext>() {}
                .preprocessModel(raw, serverSettings);

        ServiceShape service = preprocessed.expectShape(
                ShapeId.from("test.spec#Svc"), ServiceShape.class);

        SymbolProvider serverSymbols =
                new ErlangSymbolProvider(preprocessed, serverSettings, Mode.SERVER);
        SymbolProvider clientSymbols =
                new ErlangSymbolProvider(preprocessed, clientSettings, Mode.CLIENT);

        model = preprocessed;
        serverCtx = new ErlangContext(
                preprocessed,
                ModelTransformer.create(),
                serverSettings,
                serverSymbols,
                null,
                null,
                List.of(),
                service);
        // Use ErlangClientSettings for the client-mode context so the
        // integration's mode gate (settings().mode() != Mode.SERVER) returns
        // true. Mirrors the runtime wiring where each plugin instantiates
        // its own settings subclass.
        clientCtx = new ErlangContext(
                preprocessed,
                ModelTransformer.create(),
                clientSettings,
                clientSymbols,
                null,
                null,
                List.of(),
                service);
    }

    @Test
    void emitsCallbackWithInputAndOutputTypes() {
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#ListItems"), OperationShape.class);

        String out = driveCallback(serverCtx, op).toString();

        assertThat(out)
                .contains("-callback list_items(Input :: list_items_input(), Context :: term()) ->")
                .contains("{ok, list_items_output()} | {error, internal_server_error()}.");
    }

    @Test
    void mergesServiceLevelErrorsIntoCallbackErrorUnion() {
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#ListItems"), OperationShape.class);

        String out = driveCallback(serverCtx, op).toString();

        assertThat(out).contains("{error, internal_server_error()}.");
    }

    @Test
    void unionsOperationAndServiceErrorsForCallbacksWithDeclaredErrors() {
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#GetItem"), OperationShape.class);

        String out = driveCallback(serverCtx, op).toString();

        assertThat(out)
                .contains("-callback get_item(Input :: get_item_input(), Context :: term()) ->")
                .contains("{ok, get_item_output()} | {error,")
                .contains("not_found()")
                .contains("throttled()")
                .contains("internal_server_error()")
                .contains("|");
    }

    @Test
    void emitsTermForUnitInputAndOutputInCallback() {
        // `operation Ping {}` resolves to smithy.api#Unit on both sides.
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#Ping"), OperationShape.class);

        String out = driveCallback(serverCtx, op).toString();

        assertThat(out)
                .contains("-callback ping(Input :: term(), Context :: term()) ->")
                .contains("{ok, term()} | {error, internal_server_error()}.");
    }

    @Test
    void prependsHandlerSpecAboveHandlerStub() {
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#GetItem"), OperationShape.class);

        String out = driveHandler(serverCtx, op).toString();

        assertThat(out)
                .contains("-spec handle_get_item(Req :: term(), State :: term()) -> "
                        + "{ok, term()} | {error, term()}.")
                .contains("handle_get_item(Req, State) ->")
                .contains("{error, not_implemented}.");
        // The -spec must appear before the function clause, otherwise it does
        // not apply to it.
        int specIdx = out.indexOf("-spec handle_get_item");
        int clauseIdx = out.indexOf("handle_get_item(Req, State) ->");
        assertThat(specIdx).isNotNegative().isLessThan(clauseIdx);
    }

    @Test
    void emitsNothingInClientMode() {
        // Both sections are also pushed by ErlangClientCodegen for the client
        // module, but the client's own integrations own the spec — this
        // integration should be a strict no-op in client mode.
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#GetItem"), OperationShape.class);

        String callbackOut = driveCallback(clientCtx, op).toString();
        String handlerOut = driveHandler(clientCtx, op).toString();

        assertThat(callbackOut).doesNotContain("-callback");
        assertThat(handlerOut).doesNotContain("-spec handle_");
    }

    private static ErlangWriter driveCallback(ErlangContext ctx, OperationShape op) {
        ErlangWriter w = CodegenTestSupport.writer("svc_server.erl");
        installInterceptors(w, ctx);
        w.injectSection(new ServerImplCallbackSection(op));
        return w;
    }

    private static ErlangWriter driveHandler(ErlangContext ctx, OperationShape op) {
        ErlangWriter w = CodegenTestSupport.writer("svc_server.erl");
        installInterceptors(w, ctx);
        w.pushState(new ServerHandlerCallbackSection(op));
        // Mimic the body that ErlangServerCodegen.generateOperation emits.
        String fn = "handle_" + software.amazon.smithy.utils.CaseUtils.toSnakeCase(
                op.getId().getName());
        w.write("$L(Req, State) ->", fn);
        w.write("    {error, not_implemented}.");
        w.popState();
        return w;
    }

    private static void installInterceptors(ErlangWriter w, ErlangContext ctx) {
        List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors =
                new ErlangServerCallbackIntegration().interceptors(ctx);
        for (CodeInterceptor<? extends CodeSection, ErlangWriter> i : interceptors) {
            w.onSection(i);
        }
    }
}
