package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.Symbol;

class ErlangWriterTest {

    private ErlangWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ErlangWriter("test_module.erl");
    }

    // -------------------------------------------------------------------------
    // Module header
    // -------------------------------------------------------------------------

    @Test
    void moduleNameIsDerivedFromFilename() {
        assertThat(writer.toString()).startsWith("-module(test_module).\n");
    }

    @Test
    void moduleNameIsDerivedFromFilenameWithPath() {
        ErlangWriter w = new ErlangWriter("src/generated/weather_client.erl");
        assertThat(w.toString()).startsWith("-module(weather_client).\n");
    }

    @Test
    void writeModuleHeaderOverridesDefaultModuleName() {
        writer.writeModuleHeader("custom_name");
        assertThat(writer.toString()).startsWith("-module(custom_name).\n");
    }

    @Test
    void noExportLineWhenNoExportsRegistered() {
        assertThat(writer.toString()).doesNotContain("-export(");
    }

    @Test
    void singleExportAppearsInHeader() {
        writer.addExport("my_function", 2);
        assertThat(writer.toString()).contains("-export([my_function/2]).");
    }

    @Test
    void multipleExportsAppearInSingleExportDirective() {
        writer.addExport("foo", 1);
        writer.addExport("bar", 2);
        assertThat(writer.toString()).contains("-export([foo/1, bar/2]).");
    }

    @Test
    void exportsAppearAfterModuleLine() {
        writer.addExport("go", 0);
        String output = writer.toString();
        int moduleIdx = output.indexOf("-module(");
        int exportIdx = output.indexOf("-export(");
        assertThat(exportIdx).isGreaterThan(moduleIdx);
    }

    // -------------------------------------------------------------------------
    // $T formatter
    // -------------------------------------------------------------------------

    @Test
    void formatTWithBuiltinSymbol() {
        Symbol sym = ErlangSymbol.builtin("binary()");
        writer.write("$T", sym);
        assertThat(writer.toString()).contains("binary()");
    }

    @Test
    void formatTWithAtomSymbol() {
        Symbol sym = ErlangSymbol.atom("ok");
        writer.write("$T", sym);
        assertThat(writer.toString()).contains("ok");
    }

    @Test
    void formatTWithModuleRefSymbol() {
        Symbol sym = ErlangSymbol.moduleRef("my_module", "my_fun");
        writer.write("$T", sym);
        assertThat(writer.toString()).contains("my_module:my_fun");
    }

    @Test
    void formatTWithHrlSymbolEmitsRecordRef() {
        Symbol sym = Symbol.builder()
                .name("my_record")
                .definitionFile("src/generated/my_types.hrl")
                .build();
        writer.write("$T", sym);
        assertThat(writer.toString()).contains("#my_record{}");
    }

    @Test
    void formatTWithHrlSymbolAddsIncludeLib() {
        Symbol sym = Symbol.builder()
                .name("my_record")
                .definitionFile("src/generated/my_types.hrl")
                .build();
        writer.write("$T", sym);
        assertThat(writer.toString()).contains("-include_lib(\"src/generated/my_types.hrl\").");
    }

    @Test
    void formatTWithUnknownSymbolFallsBackToName() {
        Symbol sym = Symbol.builder().name("some_type").build();
        writer.write("$T", sym);
        assertThat(writer.toString()).contains("some_type");
    }

    // -------------------------------------------------------------------------
    // $M formatter (module name escape)
    // -------------------------------------------------------------------------

    @Test
    void formatMPassesThroughNormalModuleName() {
        writer.write("$M", "my_module");
        assertThat(writer.toString()).contains("my_module");
    }

    @Test
    void formatMEscapesReservedModuleName() {
        writer.write("$M", "receive");
        assertThat(writer.toString()).contains("receive_");
    }

    // -------------------------------------------------------------------------
    // $F formatter (function name escape)
    // -------------------------------------------------------------------------

    @Test
    void formatFPassesThroughNormalFunctionName() {
        writer.write("$F", "my_function");
        assertThat(writer.toString()).contains("my_function");
    }

    @Test
    void formatFEscapesReservedFunctionName() {
        writer.write("$F", "receive");
        assertThat(writer.toString()).contains("receive_");
    }

    // -------------------------------------------------------------------------
    // $A formatter (atom literal)
    // -------------------------------------------------------------------------

    @Test
    void formatAOutputsAtomName() {
        writer.write("$A", "ok");
        assertThat(writer.toString()).contains("ok");
    }

    @Test
    void formatAOutputsComplexAtomName() {
        writer.write("$A", "my_atom");
        assertThat(writer.toString()).contains("my_atom");
    }

    // -------------------------------------------------------------------------
    // $D formatter (doc comment)
    // -------------------------------------------------------------------------

    @Test
    void formatDEmitsDoublePercentPrefix() {
        writer.write("$D", "This is a doc comment");
        assertThat(writer.toString()).contains("%% This is a doc comment");
    }

    @Test
    void formatDWithEmptyStringEmitsPercentComment() {
        writer.write("$D", "");
        // trimTrailingSpaces() strips the trailing space, so the result is "%%"
        assertThat(writer.toString()).contains("%%");
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    @Test
    void writeRecordEmitsRecordAndTypeForEmptyStructure() {
        software.amazon.smithy.model.Model model = software.amazon.smithy.model.Model.assembler()
                .addUnparsedModel("test.smithy", String.join("\n",
                        "$version: \"2\"",
                        "namespace com.example",
                        "structure EmptyStruct {}"))
                .assemble()
                .unwrap();
        software.amazon.smithy.model.shapes.StructureShape shape =
                model.expectShape(
                        software.amazon.smithy.model.shapes.ShapeId.from("com.example#EmptyStruct"),
                        software.amazon.smithy.model.shapes.StructureShape.class);
        software.amazon.smithy.codegen.core.SymbolProvider provider =
                s -> Symbol.builder().name("term()").build();

        writer.writeRecord(shape, provider);

        String output = writer.toString();
        assertThat(output).contains("-record(empty_struct, {}).");
        assertThat(output).contains("-type empty_struct() :: #empty_struct{}.");
    }

    @Test
    void writeEnumTypeEmitsAtomVariants() {
        software.amazon.smithy.model.Model model = software.amazon.smithy.model.Model.assembler()
                .addUnparsedModel("test.smithy", String.join("\n",
                        "$version: \"2\"",
                        "namespace com.example",
                        "enum Color {",
                        "    RED",
                        "    BLUE",
                        "}"))
                .assemble()
                .unwrap();
        software.amazon.smithy.model.shapes.EnumShape shape =
                model.expectShape(
                        software.amazon.smithy.model.shapes.ShapeId.from("com.example#Color"),
                        software.amazon.smithy.model.shapes.EnumShape.class);

        writer.writeEnumType(shape);

        String output = writer.toString();
        assertThat(output).contains("-type color() ::");
        assertThat(output).contains("red");
        assertThat(output).contains("blue");
    }

    @Test
    void writeIntEnumTypeEmitsIntegerLiterals() {
        software.amazon.smithy.model.Model model = software.amazon.smithy.model.Model.assembler()
                .addUnparsedModel("test.smithy", String.join("\n",
                        "$version: \"2\"",
                        "namespace com.example",
                        "intEnum Status {",
                        "    ZERO = 0",
                        "    ONE = 1",
                        "}"))
                .assemble()
                .unwrap();
        software.amazon.smithy.model.shapes.IntEnumShape shape =
                model.expectShape(
                        software.amazon.smithy.model.shapes.ShapeId.from("com.example#Status"),
                        software.amazon.smithy.model.shapes.IntEnumShape.class);

        writer.writeIntEnumType(shape);

        String output = writer.toString();
        assertThat(output).contains("-type status() :: 0 | 1.");
    }
}
