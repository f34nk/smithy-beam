package io.smithy.beam.elixir.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamPreludeIntegration;
import io.smithy.beam.core.Mode;
import io.smithy.beam.elixir.client.ElixirClientSettings;
import io.smithy.beam.elixir.codegen.CodegenTestSupport;
import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirSettings;
import io.smithy.beam.elixir.codegen.ElixirSymbolProvider;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import io.smithy.beam.elixir.codegen.sections.ServerHandlerCallbackSection;
import io.smithy.beam.elixir.codegen.sections.ServerImplCallbackSection;
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
 * Unit tests for {@link ElixirServerCallbackIntegration}.
 *
 * <p>Drives the integration against a small fixture and asserts:
 *
 * <ul>
 *   <li>An empty {@link ServerImplCallbackSection} produces a well-formed
 *       {@code @callback <op>/2} declaration with input/output type aliases
 *       and a precise error union.</li>
 *   <li>Service-level errors are merged into every operation's error union by
 *       {@link io.smithy.beam.core.BeamPreludeIntegration#preprocessModel}.</li>
 *   <li>A {@link ServerHandlerCallbackSection} wrapping the existing
 *       {@code def handle_<op>(request, state)} stub is preceded by a
 *       protocol-agnostic {@code @spec handle_<op>/2} line.</li>
 *   <li>Operations whose input or output is the unit shape emit {@code term()}
 *       for the corresponding position in the {@code @callback}.</li>
 *   <li>Client-mode contexts emit no callback or handler spec — that is owned
 *       by the client's {@code @spec} integration.</li>
 * </ul>
 */
class ElixirServerCallbackIntegrationTest {

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

    private static ElixirContext serverCtx;
    private static ElixirContext clientCtx;
    private static Model model;

    @BeforeAll
    static void setUp() {
        // Mirror the prelude integration so service-level errors are fanned out
        // onto every operation before the integration runs — that is what the
        // codegen director does at runtime.
        Model raw = Model.assembler()
                .discoverModels(ElixirServerCallbackIntegrationTest.class.getClassLoader())
                .addUnparsedModel("test.smithy", MODEL)
                .assemble()
                .unwrap();

        ElixirServerSettings serverSettings = new ElixirServerSettings();
        serverSettings.setService(ShapeId.from("test.spec#Svc"));
        serverSettings.setNamespace("Svc");
        serverSettings.setEdition("2025");

        ElixirClientSettings clientSettings = new ElixirClientSettings();
        clientSettings.setService(ShapeId.from("test.spec#Svc"));
        clientSettings.setNamespace("Svc");
        clientSettings.setEdition("2025");

        Model preprocessed = new BeamPreludeIntegration<
                ElixirSettings, ElixirWriter, ElixirContext>() {}
                .preprocessModel(raw, serverSettings);

        ServiceShape service = preprocessed.expectShape(
                ShapeId.from("test.spec#Svc"), ServiceShape.class);

        SymbolProvider serverSymbols =
                new ElixirSymbolProvider(preprocessed, serverSettings, Mode.SERVER);
        SymbolProvider clientSymbols =
                new ElixirSymbolProvider(preprocessed, clientSettings, Mode.CLIENT);

        model = preprocessed;
        serverCtx = new ElixirContext(
                preprocessed,
                ModelTransformer.create(),
                serverSettings,
                serverSymbols,
                null,
                null,
                List.of(),
                service);
        // Use ElixirClientSettings for the client-mode context so the
        // integration's mode gate (settings().mode() != Mode.SERVER) returns
        // true. Mirrors the runtime wiring where each plugin instantiates
        // its own settings subclass.
        clientCtx = new ElixirContext(
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
                .contains("@callback list_items(input :: ListItemsInput.t(), context :: term()) ::")
                .contains("{:ok, ListItemsOutput.t()} | {:error, InternalServerError.t()}");
    }

    @Test
    void mergesServiceLevelErrorsIntoCallbackErrorUnion() {
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#ListItems"), OperationShape.class);

        String out = driveCallback(serverCtx, op).toString();

        assertThat(out).contains("{:error, InternalServerError.t()}");
    }

    @Test
    void unionsOperationAndServiceErrorsForCallbacksWithDeclaredErrors() {
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#GetItem"), OperationShape.class);

        String out = driveCallback(serverCtx, op).toString();

        assertThat(out)
                .contains("@callback get_item(input :: GetItemInput.t(), context :: term()) ::")
                .contains("{:ok, GetItemOutput.t()} | {:error,")
                .contains("NotFound.t()")
                .contains("Throttled.t()")
                .contains("InternalServerError.t()")
                .contains(" | ");
    }

    @Test
    void emitsTermForUnitInputAndOutputInCallback() {
        // `operation Ping {}` resolves to smithy.api#Unit on both sides.
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#Ping"), OperationShape.class);

        String out = driveCallback(serverCtx, op).toString();

        assertThat(out)
                .contains("@callback ping(input :: term(), context :: term()) ::")
                .contains("{:ok, term()} | {:error, InternalServerError.t()}");
    }

    @Test
    void prependsHandlerSpecAboveHandlerStub() {
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#GetItem"), OperationShape.class);

        String out = driveHandler(serverCtx, op).toString();

        assertThat(out)
                .contains("@spec handle_get_item(request :: term(), state :: term()) :: "
                        + "{:ok, term()} | {:error, term()}")
                .contains("def handle_get_item(request, state) do")
                .contains("{:error, :not_implemented}");
        // The @spec must appear before the function clause, otherwise it does
        // not apply to it.
        int specIdx = out.indexOf("@spec handle_get_item");
        int clauseIdx = out.indexOf("def handle_get_item(request, state) do");
        assertThat(specIdx).isNotNegative().isLessThan(clauseIdx);
    }

    @Test
    void emitsNothingInClientMode() {
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#GetItem"), OperationShape.class);

        String callbackOut = driveCallback(clientCtx, op).toString();
        String handlerOut = driveHandler(clientCtx, op).toString();

        assertThat(callbackOut).doesNotContain("@callback");
        assertThat(handlerOut).doesNotContain("@spec handle_");
    }

    private static ElixirWriter driveCallback(ElixirContext ctx, OperationShape op) {
        ElixirWriter w = CodegenTestSupport.writer("svc_server.ex");
        installInterceptors(w, ctx);
        w.injectSection(new ServerImplCallbackSection(op));
        return w;
    }

    private static ElixirWriter driveHandler(ElixirContext ctx, OperationShape op) {
        ElixirWriter w = CodegenTestSupport.writer("svc_server.ex");
        installInterceptors(w, ctx);
        w.pushState(new ServerHandlerCallbackSection(op));
        // Mimic the body that ElixirServerCodegen.generateService emits inside
        // the defmodule block — minus the surrounding indent (immaterial to
        // the assertions below).
        String fn = "handle_" + software.amazon.smithy.utils.CaseUtils.toSnakeCase(
                op.getId().getName());
        w.write("def $L(request, state) do", fn);
        w.indent();
        w.write("{:error, :not_implemented}");
        w.dedent();
        w.write("end");
        w.popState();
        return w;
    }

    private static void installInterceptors(ElixirWriter w, ElixirContext ctx) {
        List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors =
                new ElixirServerCallbackIntegration().interceptors(ctx);
        for (CodeInterceptor<? extends CodeSection, ElixirWriter> i : interceptors) {
            w.onSection(i);
        }
    }
}
