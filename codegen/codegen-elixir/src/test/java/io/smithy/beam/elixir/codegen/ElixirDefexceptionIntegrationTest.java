package io.smithy.beam.elixir.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Unit tests for {@link ElixirDefexceptionIntegration}.
 *
 * <p>Drives the integration against a small fixture and asserts that the
 * generated error modules satisfy Elixir's {@code Exception} behaviour by
 * carrying both a {@code defexception} declaration and a matching
 * {@code def message/1} clause:
 *
 * <ul>
 *   <li>Error shapes with a {@code message} member emit a clause that
 *       extracts the field via pattern match.</li>
 *   <li>Error shapes without a {@code message} member emit a fallback clause
 *       returning the shape's local name as a static string.</li>
 *   <li>Non-error structures are left untouched — the integration must not
 *       inject {@code message/1} into regular {@code defstruct} modules.</li>
 * </ul>
 */
class ElixirDefexceptionIntegrationTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.errors",
            "",
            "service Svc {",
            "    version: \"2024\"",
            "    operations: [Op]",
            "}",
            "",
            "operation Op {",
            "    errors: [WithMessage, WithoutMessage]",
            "}",
            "",
            "@error(\"client\")",
            "structure WithMessage {",
            "    message: String",
            "    code: String",
            "}",
            "",
            "@error(\"server\")",
            "structure WithoutMessage {",
            "    code: String",
            "}",
            "",
            "structure NotAnError {",
            "    name: String",
            "}");

    private static CodegenTestSupport.Fixture fixture;

    @BeforeAll
    static void setUp() {
        fixture = CodegenTestSupport.fixture(MODEL, "test.errors#Svc", "Svc");
    }

    @Test
    void errorShapeWithMessageMemberEmitsPatternMatchedClause() {
        StructureShape shape = fixture.model().expectShape(
                ShapeId.from("test.errors#WithMessage"), StructureShape.class);

        String out = drive(shape);

        assertThat(out).contains("defexception");
        assertThat(out).contains("def message(%__MODULE__{message: msg}), do: msg");
    }

    @Test
    void errorShapeWithoutMessageMemberEmitsFallbackClause() {
        StructureShape shape = fixture.model().expectShape(
                ShapeId.from("test.errors#WithoutMessage"), StructureShape.class);

        String out = drive(shape);

        assertThat(out).contains("defexception");
        assertThat(out).contains("def message(%__MODULE__{}), do: \"WithoutMessage\"");
    }

    @Test
    void messageClauseLandsInsideTheDefmoduleBlock() {
        // Order check: the `def message/1` clause is appended to the
        // StructTypeSection pushed *inside* writeDefModule, so it must appear
        // before the closing `end` of the module — otherwise the generated
        // file would be syntactically invalid Elixir.
        StructureShape shape = fixture.model().expectShape(
                ShapeId.from("test.errors#WithMessage"), StructureShape.class);

        String out = drive(shape);

        int defModuleIdx = out.indexOf("defmodule");
        int messageIdx = out.indexOf("def message(");
        int endIdx = out.lastIndexOf("end");

        assertThat(defModuleIdx).isNotNegative();
        assertThat(messageIdx).isGreaterThan(defModuleIdx);
        assertThat(messageIdx).isLessThan(endIdx);
    }

    @Test
    void nonErrorStructuresAreUntouched() {
        StructureShape shape = fixture.model().expectShape(
                ShapeId.from("test.errors#NotAnError"), StructureShape.class);

        String out = drive(shape);

        assertThat(out).contains("defstruct");
        assertThat(out).doesNotContain("defexception");
        assertThat(out).doesNotContain("def message(");
    }

    /**
     * Renders a single shape via {@link ElixirWriter#writeStructModule} with
     * the integration's interceptors registered, and returns the captured
     * source. Mirrors the harness used by other Elixir integration tests so
     * the integration is exercised through its real interception path.
     */
    private static String drive(StructureShape shape) {
        ElixirWriter writer = CodegenTestSupport.writer("svc_errors.ex");
        SymbolProvider symbols = fixture.ctx().symbolProvider();

        List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors =
                new ElixirDefexceptionIntegration().interceptors(fixture.ctx());
        for (CodeInterceptor<? extends CodeSection, ElixirWriter> i : interceptors) {
            writer.onSection(i);
        }

        writer.writeStructModule(shape, symbols);
        return writer.toString();
    }
}
