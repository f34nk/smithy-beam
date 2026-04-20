package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamPreludeIntegration;
import io.smithy.beam.erlang.client.ErlangClientSettings;
import io.smithy.beam.erlang.codegen.sections.OperationSpecSection;
import io.smithy.beam.core.Mode;
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
 * Unit tests for {@link ErlangSpecIntegration}.
 *
 * <p>Drives the integration against a small fixture and asserts that each
 * operation's {@code -spec} line is well-formed and dialyzer-friendly:
 *
 * <ul>
 *   <li>Operations without declared errors fall back to {@code {error, term()}}.</li>
 *   <li>Operations with declared errors emit a precise union of the generated
 *       error type aliases.</li>
 *   <li>Service-level errors are merged into every operation's error union by
 *       {@link io.smithy.beam.core.BeamPreludeIntegration#preprocessModel}, so
 *       this integration sees them automatically when interceptors fire.</li>
 *   <li>Operations whose input or output is the unit shape emit {@code term()}
 *       for the corresponding position.</li>
 * </ul>
 */
class ErlangSpecIntegrationTest {

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

    private static ErlangContext ctx;
    private static Model model;

    @BeforeAll
    static void setUp() {
        // Mirror the prelude integration so service-level errors are fanned out
        // onto every operation before the integration runs — that is what the
        // codegen director does at runtime.
        Model raw = Model.assembler()
                .discoverModels(ErlangSpecIntegrationTest.class.getClassLoader())
                .addUnparsedModel("test.smithy", MODEL)
                .assemble()
                .unwrap();

        ErlangClientSettings settings = new ErlangClientSettings();
        settings.setService(ShapeId.from("test.spec#Svc"));
        settings.setModule("svc");
        settings.setEdition("2025");

        Model preprocessed = new BeamPreludeIntegration<
                ErlangSettings, ErlangWriter, ErlangContext>() {}
                .preprocessModel(raw, settings);

        ServiceShape service = preprocessed.expectShape(
                ShapeId.from("test.spec#Svc"), ServiceShape.class);
        SymbolProvider symbols = new ErlangSymbolProvider(preprocessed, settings, Mode.CLIENT);

        model = preprocessed;
        ctx = new ErlangContext(
                preprocessed,
                ModelTransformer.create(),
                settings,
                symbols,
                null,
                null,
                List.of(),
                service);
    }

    @Test
    void emitsSpecLineWithInputAndOutputTypes() {
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#ListItems"), OperationShape.class);

        String out = drive(op).toString();

        assertThat(out)
                .contains("-spec list_items(Client :: map(), Input :: list_items_input()) ->")
                .contains("{ok, list_items_output()} | {error, internal_server_error()}.");
    }

    @Test
    void mergesServiceLevelErrorsIntoOperationsWithoutOwnErrors() {
        // ListItems declares no errors of its own, but the service declares
        // InternalServerError, so the prelude transform wires it onto the op.
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#ListItems"), OperationShape.class);

        String out = drive(op).toString();

        assertThat(out).contains("{error, internal_server_error()}.");
    }

    @Test
    void unionsOperationAndServiceErrorsForOperationsWithDeclaredErrors() {
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#GetItem"), OperationShape.class);

        String out = drive(op).toString();

        assertThat(out)
                .contains("-spec get_item(Client :: map(), Input :: get_item_input()) ->")
                .contains("{ok, get_item_output()} | {error,");
        // All three error type aliases must appear in the union, irrespective
        // of declaration order on the op vs. the service.
        assertThat(out).contains("not_found()");
        assertThat(out).contains("throttled()");
        assertThat(out).contains("internal_server_error()");
        assertThat(out).contains("|");
    }

    @Test
    void emitsTermForUnitInputAndOutput() {
        // `operation Ping {}` resolves to smithy.api#Unit on both sides.
        OperationShape op = model.expectShape(
                ShapeId.from("test.spec#Ping"), OperationShape.class);

        String out = drive(op).toString();

        assertThat(out)
                .contains("-spec ping(Client :: map(), Input :: term()) ->")
                .contains("{ok, term()} | {error, internal_server_error()}.");
    }

    private static ErlangWriter drive(OperationShape op) {
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");
        List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors =
                new ErlangSpecIntegration().interceptors(ctx);
        for (CodeInterceptor<? extends CodeSection, ErlangWriter> i : interceptors) {
            w.onSection(i);
        }
        w.injectSection(new OperationSpecSection(op));
        return w;
    }
}
