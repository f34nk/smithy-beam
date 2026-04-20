package io.smithy.beam.elixir.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;

class ElixirWriterTest {

    private ElixirWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ElixirWriter("test_module.ex");
    }

    // -------------------------------------------------------------------------
    // $T formatter
    // -------------------------------------------------------------------------

    @Test
    void formatTWithBuiltinSymbolOutputsName() {
        Symbol sym = ElixirSymbol.builtin("String.t()");
        writer.write("$T", sym);
        assertThat(writer.toString()).contains("String.t()");
    }

    @Test
    void formatTWithAtomSymbolOutputsAtomName() {
        Symbol sym = ElixirSymbol.atom("ok");
        writer.write("$T", sym);
        assertThat(writer.toString()).contains(":ok");
    }

    @Test
    void formatTWithModuleSymbolOutputsModuleName() {
        Symbol sym = ElixirSymbol.module("GenServer");
        writer.write("$T", sym);
        assertThat(writer.toString()).contains("GenServer");
    }

    @Test
    void formatTWithExSymbolEmitsModuleTypeRef() {
        Symbol sym = Symbol.builder()
                .name("Weather.Types.GetForecastInput")
                .definitionFile("src/generated/weather_types.ex")
                .build();
        writer.write("$T", sym);
        assertThat(writer.toString()).contains("Weather.Types.GetForecastInput.t()");
    }

    @Test
    void formatTWithExSymbolAddsToAliases() {
        Symbol sym = Symbol.builder()
                .name("Weather.Types.GetForecastInput")
                .definitionFile("src/generated/weather_types.ex")
                .build();
        writer.write("$T", sym);
        assertThat(writer.getImportContainer().getAliases())
                .contains("Weather.Types.GetForecastInput");
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
    void formatMPassesThroughNonReservedModuleName() {
        writer.write("$M", "WeatherService");
        assertThat(writer.toString()).contains("WeatherService");
    }

    @Test
    void formatMEscapesReservedModuleKeyword() {
        writer.write("$M", "do");
        assertThat(writer.toString()).contains("DoEx");
    }

    @Test
    void formatMEscapesReceiveKeyword() {
        writer.write("$M", "receive");
        assertThat(writer.toString()).contains("ReceiveEx");
    }

    // -------------------------------------------------------------------------
    // $F formatter (function name escape)
    // -------------------------------------------------------------------------

    @Test
    void formatFPassesThroughNonReservedFunctionName() {
        writer.write("$F", "my_function");
        assertThat(writer.toString()).contains("my_function");
    }

    @Test
    void formatFEscapesReservedFunctionName() {
        writer.write("$F", "do");
        assertThat(writer.toString()).contains("do_field");
    }

    @Test
    void formatFEscapesReceiveFunctionName() {
        writer.write("$F", "receive");
        assertThat(writer.toString()).contains("receive_field");
    }

    // -------------------------------------------------------------------------
    // $A formatter (atom literal)
    // -------------------------------------------------------------------------

    @Test
    void formatAPrependsColonToPlainName() {
        writer.write("$A", "ok");
        assertThat(writer.toString()).contains(":ok");
    }

    @Test
    void formatAPassesThroughAlreadyColonPrefixedAtom() {
        writer.write("$A", ":error");
        assertThat(writer.toString()).contains(":error");
    }

    @Test
    void formatAWithSnakeCaseName() {
        writer.write("$A", "not_implemented");
        assertThat(writer.toString()).contains(":not_implemented");
    }

    // -------------------------------------------------------------------------
    // $D formatter (doc comment)
    // -------------------------------------------------------------------------

    @Test
    void formatDEmitsDocBlock() {
        writer.write("$D", "Fetches the weather forecast.");
        String output = writer.toString();
        assertThat(output).contains("@doc \"\"\"");
        assertThat(output).contains("Fetches the weather forecast.");
    }

    @Test
    void formatDWithEmptyStringEmitsEmptyDocBlock() {
        writer.write("$D", "");
        assertThat(writer.toString()).contains("@doc \"\"\"");
    }

    // -------------------------------------------------------------------------
    // writeDefModule
    // -------------------------------------------------------------------------

    @Test
    void writeDefModuleEmitsDefmoduleHeader() {
        writer.writeDefModule("Foo.Bar", () -> {});
        assertThat(writer.toString()).contains("defmodule Foo.Bar do");
    }

    @Test
    void writeDefModuleEmitsEndKeyword() {
        writer.writeDefModule("Foo.Bar", () -> {});
        assertThat(writer.toString()).contains("end");
    }

    @Test
    void writeDefModuleBodyIsIndented() {
        writer.writeDefModule("Foo", () -> writer.write("hello()"));
        String output = writer.toString();
        assertThat(output).contains("defmodule Foo do");
        assertThat(output).contains("hello()");
        assertThat(output).contains("end");
    }

    // -------------------------------------------------------------------------
    // writeStructModule
    // -------------------------------------------------------------------------

    @Test
    void writeStructModuleEmitsDefstructForEmptyStructure() {
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
                s -> Symbol.builder().name(s.getId().getName()).build();

        writer.writeStructModule(shape, provider);

        String output = writer.toString();
        assertThat(output).contains("defmodule EmptyStruct do");
        assertThat(output).contains("defstruct []");
    }

    @Test
    void writeStructModuleEmitsTypeSpec() {
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

        writer.writeStructModule(shape, s -> Symbol.builder().name("any()").build());

        assertThat(writer.toString()).contains("@type t() :: %__MODULE__{}");
    }

    @Test
    void writeStructModuleEmitsTypeSpecWithMembers() {
        software.amazon.smithy.model.Model model = software.amazon.smithy.model.Model.assembler()
                .addUnparsedModel("test.smithy", String.join("\n",
                        "$version: \"2\"",
                        "namespace com.example",
                        "structure GetWeatherInput {",
                        "    @required",
                        "    city: String",
                        "    units: String",
                        "}"))
                .assemble()
                .unwrap();
        software.amazon.smithy.model.shapes.StructureShape shape =
                model.expectShape(
                        software.amazon.smithy.model.shapes.ShapeId.from("com.example#GetWeatherInput"),
                        software.amazon.smithy.model.shapes.StructureShape.class);

        SymbolProvider provider = s -> {
            if (s instanceof software.amazon.smithy.model.shapes.MemberShape) {
                return ElixirSymbol.builtin("String.t()");
            }
            return Symbol.builder().name(s.getId().getName()).build();
        };
        writer.writeStructModule(shape, provider);

        String output = writer.toString();
        assertThat(output).contains("defmodule GetWeatherInput do");
        assertThat(output).contains("@type t() :: %__MODULE__{");
        assertThat(output).contains("city: String.t()");
        assertThat(output).contains("units: String.t()");
    }

    @Test
    void writeStructModuleUsesDefexceptionForErrorShape() {
        software.amazon.smithy.model.Model model = software.amazon.smithy.model.Model.assembler()
                .addUnparsedModel("test.smithy", String.join("\n",
                        "$version: \"2\"",
                        "namespace com.example",
                        "@error(\"client\")",
                        "structure MyError {",
                        "    message: String",
                        "}"))
                .assemble()
                .unwrap();
        software.amazon.smithy.model.shapes.StructureShape shape =
                model.expectShape(
                        software.amazon.smithy.model.shapes.ShapeId.from("com.example#MyError"),
                        software.amazon.smithy.model.shapes.StructureShape.class);

        writer.writeStructModule(shape, s -> Symbol.builder().name("String.t()").build());

        assertThat(writer.toString()).contains("defexception");
        assertThat(writer.toString()).doesNotContain("defstruct");
    }

    // -------------------------------------------------------------------------
    // writeUnionModule
    // -------------------------------------------------------------------------

    @Test
    void writeUnionModuleEmitsTypeSpecWithVariants() {
        software.amazon.smithy.model.Model model = software.amazon.smithy.model.Model.assembler()
                .addUnparsedModel("test.smithy", String.join("\n",
                        "$version: \"2\"",
                        "namespace com.example",
                        "union Result {",
                        "    success: String",
                        "    failure: String",
                        "}"))
                .assemble()
                .unwrap();
        software.amazon.smithy.model.shapes.UnionShape shape =
                model.expectShape(
                        software.amazon.smithy.model.shapes.ShapeId.from("com.example#Result"),
                        software.amazon.smithy.model.shapes.UnionShape.class);

        SymbolProvider symbols = s -> {
            if (s instanceof software.amazon.smithy.model.shapes.MemberShape) {
                return ElixirSymbol.builtin("String.t()");
            }
            return Symbol.builder().name(s.getId().getName()).build();
        };
        writer.writeUnionModule(shape, symbols);

        String output = writer.toString();
        assertThat(output).contains("defmodule Result do");
        assertThat(output).contains("@type t() ::");
        assertThat(output).contains("{:success, String.t()}");
        assertThat(output).contains("{:failure, String.t()}");
        assertThat(output).contains(" | ");
    }

    // -------------------------------------------------------------------------
    // writeEnumModule
    // -------------------------------------------------------------------------

    @Test
    void writeEnumModuleEmitsTypeWithAtomVariants() {
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

        SymbolProvider symbols = s -> Symbol.builder().name(s.getId().getName()).build();
        writer.writeEnumModule(shape, symbols);

        String output = writer.toString();
        assertThat(output).contains("defmodule Color do");
        assertThat(output).contains("@type t()");
        assertThat(output).contains(":red");
        assertThat(output).contains(":blue");
    }

    // -------------------------------------------------------------------------
    // writeIntEnumModule
    // -------------------------------------------------------------------------

    @Test
    void writeIntEnumModuleEmitsTypeWithIntegerLiterals() {
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

        SymbolProvider symbols = s -> Symbol.builder().name(s.getId().getName()).build();
        writer.writeIntEnumModule(shape, symbols);

        String output = writer.toString();
        assertThat(output).contains("defmodule Status do");
        assertThat(output).contains("@type t()");
        assertThat(output).contains("0");
        assertThat(output).contains("1");
    }
}
