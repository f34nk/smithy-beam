package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.Mode;
import io.smithy.beam.erlang.client.ErlangClientSettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.TimestampShape;

class ErlangSymbolProviderTest {

    private static Model model;
    private static ErlangClientSettings settings;

    @BeforeAll
    static void buildModel() {
        model = Model.assembler()
                .addUnparsedModel("test.smithy", String.join("\n",
                        "$version: \"2\"",
                        "namespace com.example",
                        "",
                        "service SimpleService {",
                        "    version: \"2024-01-01\"",
                        "    operations: [GetItem]",
                        "}",
                        "",
                        "operation GetItem {",
                        "    input: GetItemInput",
                        "    output: GetItemOutput",
                        "}",
                        "",
                        "structure GetItemInput {",
                        "    id: String",
                        "}",
                        "",
                        "structure GetItemOutput {",
                        "    name: String",
                        "}",
                        "",
                        "@error(\"server\")",
                        "structure ServiceError {",
                        "    message: String",
                        "}",
                        "",
                        "union MyUnion {",
                        "    strValue: String",
                        "    intValue: Integer",
                        "}",
                        "",
                        "enum Color {",
                        "    RED",
                        "    BLUE",
                        "}",
                        "",
                        "intEnum Status {",
                        "    ZERO = 0",
                        "    ONE = 1",
                        "}"
                ))
                .assemble()
                .unwrap();

        settings = new ErlangClientSettings();
        settings.setService(ShapeId.from("com.example#SimpleService"));
        settings.setModule("simple_service");
        settings.setEdition("2025");
    }

    private ErlangSymbolProvider clientProvider() {
        return new ErlangSymbolProvider(model, settings, Mode.CLIENT);
    }

    private ErlangSymbolProvider serverProvider() {
        return new ErlangSymbolProvider(model, settings, Mode.SERVER);
    }

    // -------------------------------------------------------------------------
    // Service / operation symbols
    // -------------------------------------------------------------------------

    @Test
    void serviceShapeClientSymbolNameHasClientSuffix() {
        ServiceShape service = model.expectShape(
                ShapeId.from("com.example#SimpleService"), ServiceShape.class);
        Symbol sym = clientProvider().toSymbol(service);
        assertThat(sym.getName()).isEqualTo("simple_service_client");
    }

    @Test
    void serviceShapeClientSymbolDefinitionFileIsErl() {
        ServiceShape service = model.expectShape(
                ShapeId.from("com.example#SimpleService"), ServiceShape.class);
        Symbol sym = clientProvider().toSymbol(service);
        assertThat(sym.getDefinitionFile()).endsWith("simple_service_client.erl");
    }

    @Test
    void serviceShapeServerSymbolNameHasServerSuffix() {
        ServiceShape service = model.expectShape(
                ShapeId.from("com.example#SimpleService"), ServiceShape.class);
        Symbol sym = serverProvider().toSymbol(service);
        assertThat(sym.getName()).isEqualTo("simple_service_server");
    }

    @Test
    void serviceShapeServerSymbolDefinitionFileIsErl() {
        ServiceShape service = model.expectShape(
                ShapeId.from("com.example#SimpleService"), ServiceShape.class);
        Symbol sym = serverProvider().toSymbol(service);
        assertThat(sym.getDefinitionFile()).endsWith("simple_service_server.erl");
    }

    @Test
    void operationShapeSharesServiceSymbol() {
        Symbol opSym = clientProvider().toSymbol(
                model.expectShape(ShapeId.from("com.example#GetItem")));
        Symbol svcSym = clientProvider().toSymbol(
                model.expectShape(ShapeId.from("com.example#SimpleService")));
        assertThat(opSym.getName()).isEqualTo(svcSym.getName());
        assertThat(opSym.getDefinitionFile()).isEqualTo(svcSym.getDefinitionFile());
    }

    // -------------------------------------------------------------------------
    // Aggregate shapes
    // -------------------------------------------------------------------------

    @Test
    void structureShapeSymbolIsSnakeCased() {
        Symbol sym = clientProvider().toSymbol(
                model.expectShape(ShapeId.from("com.example#GetItemInput")));
        assertThat(sym.getName()).isEqualTo("get_item_input");
    }

    @Test
    void structureShapeSymbolDefinitionFileIsHrl() {
        Symbol sym = clientProvider().toSymbol(
                model.expectShape(ShapeId.from("com.example#GetItemInput")));
        assertThat(sym.getDefinitionFile()).endsWith("simple_service_client_types.hrl");
    }

    @Test
    void structureShapeSymbolDefinitionFileIsServerHrl() {
        Symbol sym = serverProvider().toSymbol(
                model.expectShape(ShapeId.from("com.example#GetItemInput")));
        assertThat(sym.getDefinitionFile()).endsWith("simple_service_server_types.hrl");
    }

    @Test
    void errorStructureSymbolHasIsErrorProperty() {
        Symbol sym = clientProvider().toSymbol(
                model.expectShape(ShapeId.from("com.example#ServiceError")));
        assertThat(sym.getProperty(ErlangSymbolProvider.PROP_IS_ERROR)).isPresent();
        assertThat(sym.getProperty(ErlangSymbolProvider.PROP_IS_ERROR, Boolean.class))
                .hasValue(Boolean.TRUE);
    }

    @Test
    void unionShapeSymbolIsSnakeCasedAndInHrl() {
        Symbol sym = clientProvider().toSymbol(
                model.expectShape(ShapeId.from("com.example#MyUnion")));
        assertThat(sym.getName()).isEqualTo("my_union");
        assertThat(sym.getDefinitionFile()).endsWith(".hrl");
    }

    @Test
    void enumShapeSymbolIsSnakeCasedAndInHrl() {
        Symbol sym = clientProvider().toSymbol(
                model.expectShape(ShapeId.from("com.example#Color")));
        assertThat(sym.getName()).isEqualTo("color");
        assertThat(sym.getDefinitionFile()).endsWith(".hrl");
    }

    @Test
    void intEnumShapeSymbolIsSnakeCasedAndInHrl() {
        Symbol sym = clientProvider().toSymbol(
                model.expectShape(ShapeId.from("com.example#Status")));
        assertThat(sym.getName()).isEqualTo("status");
        assertThat(sym.getDefinitionFile()).endsWith(".hrl");
    }

    // -------------------------------------------------------------------------
    // Scalar shapes — built-in type expressions, no definitionFile
    // -------------------------------------------------------------------------

    @Test
    void stringShapeIsBinary() {
        Symbol sym = clientProvider().toSymbol(StringShape.builder().id("smithy.api#String").build());
        assertThat(sym.getName()).isEqualTo("binary()");
        assertThat(sym.getDefinitionFile()).isNullOrEmpty();
    }

    @Test
    void booleanShapeIsBoolean() {
        Symbol sym = clientProvider().toSymbol(BooleanShape.builder().id("smithy.api#Boolean").build());
        assertThat(sym.getName()).isEqualTo("boolean()");
    }

    @Test
    void integerShapeIsInteger() {
        Symbol sym = clientProvider().toSymbol(IntegerShape.builder().id("smithy.api#Integer").build());
        assertThat(sym.getName()).isEqualTo("integer()");
    }

    @Test
    void longShapeIsInteger() {
        Symbol sym = clientProvider().toSymbol(LongShape.builder().id("smithy.api#Long").build());
        assertThat(sym.getName()).isEqualTo("integer()");
    }

    @Test
    void bigIntegerShapeIsInteger() {
        Symbol sym = clientProvider().toSymbol(BigIntegerShape.builder().id("smithy.api#BigInteger").build());
        assertThat(sym.getName()).isEqualTo("integer()");
    }

    @Test
    void floatShapeIsFloat() {
        Symbol sym = clientProvider().toSymbol(FloatShape.builder().id("smithy.api#Float").build());
        assertThat(sym.getName()).isEqualTo("float()");
    }

    @Test
    void doubleShapeIsFloat() {
        Symbol sym = clientProvider().toSymbol(DoubleShape.builder().id("smithy.api#Double").build());
        assertThat(sym.getName()).isEqualTo("float()");
    }

    @Test
    void bigDecimalShapeIsTuple() {
        Symbol sym = clientProvider().toSymbol(BigDecimalShape.builder().id("smithy.api#BigDecimal").build());
        assertThat(sym.getName()).isEqualTo("{integer(), integer()}");
    }

    @Test
    void blobShapeIsBinary() {
        Symbol sym = clientProvider().toSymbol(BlobShape.builder().id("smithy.api#Blob").build());
        assertThat(sym.getName()).isEqualTo("binary()");
    }

    @Test
    void timestampShapeIsInteger() {
        Symbol sym = clientProvider().toSymbol(TimestampShape.builder().id("smithy.api#Timestamp").build());
        assertThat(sym.getName()).isEqualTo("integer()");
    }

    @Test
    void documentShapeIsTerm() {
        Symbol sym = clientProvider().toSymbol(DocumentShape.builder().id("smithy.api#Document").build());
        assertThat(sym.getName()).isEqualTo("term()");
    }

    // -------------------------------------------------------------------------
    // Every symbol carries the originating shape property
    // -------------------------------------------------------------------------

    @Test
    void allSymbolsCarryShapeProperty() {
        model.shapes().forEach(shape -> {
            Symbol sym = clientProvider().toSymbol(shape);
            assertThat(sym.getProperty(ErlangSymbolProvider.PROP_SHAPE))
                    .as("PROP_SHAPE missing for shape %s", shape.getId())
                    .isPresent();
        });
    }

    // -------------------------------------------------------------------------
    // Member name conversion
    // -------------------------------------------------------------------------

    @Test
    void memberNameIsSnakeCased() {
        software.amazon.smithy.model.shapes.MemberShape member =
                model.expectShape(ShapeId.from("com.example#GetItemInput$id"),
                        software.amazon.smithy.model.shapes.MemberShape.class);
        assertThat(clientProvider().toMemberName(member)).isEqualTo("id");
    }
}
