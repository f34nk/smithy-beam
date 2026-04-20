package io.smithy.beam.elixir.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamPreludeIntegration;
import io.smithy.beam.core.Mode;
import io.smithy.beam.elixir.client.ElixirClientSettings;
import io.smithy.beam.elixir.codegen.sections.OperationSpecSection;
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
 * Unit tests for {@link ElixirSpecIntegration}.
 *
 * <p>Drives the integration against a small fixture and asserts that each
 * operation's {@code @spec} line is well-formed and dialyzer-friendly:
 *
 * <ul>
 *   <li>Operations without declared errors fall back to {@code {:error, term()}}.</li>
 *   <li>Operations with declared errors emit a precise union of the generated
 *       error type aliases.</li>
 *   <li>Service-level errors are merged into every operation's error union by
 *       {@link io.smithy.beam.core.BeamPreludeIntegration#preprocessModel}, so
 *       this integration sees them automatically when interceptors fire.</li>
 *   <li>Operations whose input or output is the unit shape emit {@code term()}
 *       for the corresponding position.</li>
 *   <li>Server-mode contexts emit no spec — that is owned by the server's
 *       {@code @callback} integration.</li>
 * </ul>
 */
class ElixirSpecIntegrationTest {

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

    private static ElixirContext clientCtx;
    private static ElixirContext serverCtx;
    private static Model model;

    @BeforeAll
    static void setUp() {
        // Mirror the prelude integration so service-level errors are fanned out
        // onto every operation before the integration runs — that is what the
        // codegen director does at runtime.
        Model raw = Model.assembler()
                .discoverModels(ElixirSpecIntegrationTest.class.getClassLoader())
                .addUnparsedModel("test.smithy", MODEL)
                .assemble()
                .unwrap();

        ElixirClientSettings settings = new ElixirClientSettings();
        settings.setService(ShapeId.from("test.spec#Svc"));
        settings.setNamespace("Svc");
        settings.setEdition("2025");

        Model preprocessed = new BeamPreludeIntegration<
                ElixirSettings, ElixirWriter, ElixirContext>() {}
                .preprocessModel(raw, settings);

        ServiceShape service = preprocessed.expectShape(
                ShapeId.from("test.spec#Svc"), ServiceShape.class);

        SymbolProvider clientSymbols = new ElixirSymbolProvider(preprocessed, settings, Mode.CLIENT);
        SymbolProvider serverSymbols = new ElixirSymbolProvider(preprocessed, settings, Mode.SERVER);

        model = preprocessed;
        clientCtx = new ElixirContext(
                preprocessed,
                ModelTransformer.create(),
                settings,
                clientSymbols,
                null,
                null,
                List.of(),
                service);
        serverCtx = new ElixirContext(
                preprocessed,
                ModelTransformer.create(),
                settings,
                serverSymbols,
                null,
                null,
                List.of(),
                service);
    }

    @Test
    void emitsSpecLineWithInputAndOutputTypes() {
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#ListItems"), OperationShape.class);

        String out = drive(clientCtx, op).toString();

        assertThat(out)
                .contains("@spec list_items(map(), ListItemsInput.t()) ::")
                .contains("{:ok, ListItemsOutput.t()} | {:error, InternalServerError.t()}");
    }

    @Test
    void mergesServiceLevelErrorsIntoOperationsWithoutOwnErrors() {
        // ListItems declares no errors of its own, but the service declares
        // InternalServerError, so the prelude transform wires it onto the op.
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#ListItems"), OperationShape.class);

        String out = drive(clientCtx, op).toString();

        assertThat(out).contains("{:error, InternalServerError.t()}");
    }

    @Test
    void unionsOperationAndServiceErrorsForOperationsWithDeclaredErrors() {
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#GetItem"), OperationShape.class);

        String out = drive(clientCtx, op).toString();

        assertThat(out)
                .contains("@spec get_item(map(), GetItemInput.t()) ::")
                .contains("{:ok, GetItemOutput.t()} | {:error,");
        // All three error type aliases must appear in the union, irrespective
        // of declaration order on the op vs. the service.
        assertThat(out).contains("NotFound.t()");
        assertThat(out).contains("Throttled.t()");
        assertThat(out).contains("InternalServerError.t()");
        assertThat(out).contains(" | ");
    }

    @Test
    void emitsTermForUnitInputAndOutput() {
        // `operation Ping {}` resolves to smithy.api#Unit on both sides.
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#Ping"), OperationShape.class);

        String out = drive(clientCtx, op).toString();

        assertThat(out)
                .contains("@spec ping(map(), term()) ::")
                .contains("{:ok, term()} | {:error, InternalServerError.t()}");
    }

    @Test
    void emitsNothingInServerMode() {
        // The same OperationSpecSection is also pushed by ElixirServerCodegen,
        // but the server's @callback integration owns the server-side spec.
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#GetItem"), OperationShape.class);

        String out = drive(serverCtx, op).toString();

        assertThat(out).doesNotContain("@spec");
        assertThat(out).doesNotContain("get_item");
    }

    private static ElixirWriter drive(ElixirContext ctx, OperationShape op) {
        ElixirWriter w = CodegenTestSupport.writer(
                ctx.symbolProvider().toSymbol(ctx.service()).getName().endsWith(".Client")
                        ? "svc_client.ex"
                        : "svc_server.ex");
        List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors =
                new ElixirSpecIntegration().interceptors(ctx);
        for (CodeInterceptor<? extends CodeSection, ElixirWriter> i : interceptors) {
            w.onSection(i);
        }
        w.injectSection(new OperationSpecSection(op));
        return w;
    }
}
