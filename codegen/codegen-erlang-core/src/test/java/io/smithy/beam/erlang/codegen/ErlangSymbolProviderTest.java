package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.Mode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

class ErlangSymbolProviderTest {

    private static final String MODEL_SOURCE = "$version: \"2\"\n"
            + "namespace example\n"
            + "\n"
            + "service Weather {\n"
            + "    operations: [GetCity]\n"
            + "}\n"
            + "\n"
            + "operation GetCity {\n"
            + "    input := {\n"
            + "        city: String\n"
            + "    }\n"
            + "    output := {\n"
            + "        name: String\n"
            + "    }\n"
            + "}\n"
            + "\n"
            + "@error(\"client\")\n"
            + "structure WeatherError {\n"
            + "    message: String\n"
            + "}\n"
            + "\n"
            + "union WeatherResult {\n"
            + "    ok: String\n"
            + "    err: WeatherError\n"
            + "}\n"
            + "\n"
            + "enum Season {\n"
            + "    SPRING\n"
            + "    SUMMER\n"
            + "    AUTUMN\n"
            + "    WINTER\n"
            + "}\n"
            + "\n"
            + "intEnum Priority {\n"
            + "    HIGH = 1\n"
            + "    LOW = 2\n"
            + "}\n";

    private Model model;
    private ErlangSettings settings;
    private ErlangSymbolProvider clientProvider;
    private ErlangSymbolProvider serverProvider;

    @BeforeEach
    void setUp() {
        model = Model.assembler()
                .addUnparsedModel("weather.smithy", MODEL_SOURCE)
                .assemble()
                .unwrap();
        settings = new ErlangSettings() {
            @Override
            public Mode mode() {
                return Mode.CLIENT;
            }
        };
        settings.setModule("weather");
        settings.setService(ShapeId.from("example#Weather"));
        settings.setEdition("2025");

        clientProvider = new ErlangSymbolProvider(model, settings, Mode.CLIENT);
        serverProvider = new ErlangSymbolProvider(model, settings, Mode.SERVER);
    }

    // ------------------------------------------------------------------
    // Service shape
    // ------------------------------------------------------------------

    @Test
    void serviceShape_client_producesClientModuleName() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("example#Weather")));
        assertThat(sym.getName()).isEqualTo("weather_client");
        assertThat(sym.getDefinitionFile()).endsWith("weather_client.erl");
    }

    @Test
    void serviceShape_server_producesServerModuleName() {
        Symbol sym = serverProvider.toSymbol(
                model.expectShape(ShapeId.from("example#Weather")));
        assertThat(sym.getName()).isEqualTo("weather_server");
        assertThat(sym.getDefinitionFile()).endsWith("weather_server.erl");
    }

    // ------------------------------------------------------------------
    // Operation shape
    // ------------------------------------------------------------------

    @Test
    void operationShape_routesToSameFileAsService() {
        Symbol serviceSymbol = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("example#Weather")));
        Symbol opSymbol = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("example#GetCity")));
        assertThat(opSymbol.getDefinitionFile()).isEqualTo(serviceSymbol.getDefinitionFile());
    }

    // ------------------------------------------------------------------
    // Structure shape
    // ------------------------------------------------------------------

    @Test
    void structureShape_producesSnakeCaseName() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("example#GetCityInput")));
        assertThat(sym.getName()).isEqualTo("get_city_input");
    }

    @Test
    void structureShape_definitionFileIsTypesHrl() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("example#GetCityInput")));
        assertThat(sym.getDefinitionFile()).endsWith("_types.hrl");
    }

    @Test
    void errorShape_hasIsErrorProperty() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("example#WeatherError")));
        assertThat(sym.getProperty("erlang.isError", Boolean.class)).hasValue(true);
    }

    // ------------------------------------------------------------------
    // Union shape
    // ------------------------------------------------------------------

    @Test
    void unionShape_routesToTypesHrl() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("example#WeatherResult")));
        assertThat(sym.getDefinitionFile()).endsWith("_types.hrl");
        assertThat(sym.getName()).isEqualTo("weather_result");
    }

    // ------------------------------------------------------------------
    // Enum / intEnum shapes
    // ------------------------------------------------------------------

    @Test
    void enumShape_routesToTypesHrl() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("example#Season")));
        assertThat(sym.getDefinitionFile()).endsWith("_types.hrl");
    }

    @Test
    void intEnumShape_routesToTypesHrl() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("example#Priority")));
        assertThat(sym.getDefinitionFile()).endsWith("_types.hrl");
    }

    // ------------------------------------------------------------------
    // Scalar shapes — builtin kind, no definitionFile
    // ------------------------------------------------------------------

    @Test
    void stringShape_producesBuiltinBinary() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("smithy.api#String")));
        assertThat(sym.getName()).isEqualTo("binary()");
        assertThat(sym.getProperty(ErlangSymbol.PROPERTY_KIND, String.class))
                .hasValue(ErlangSymbol.KIND_BUILTIN);
        assertThat(sym.getDefinitionFile()).isNullOrEmpty();
    }

    @Test
    void booleanShape_producesBuiltinBoolean() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("smithy.api#Boolean")));
        assertThat(sym.getName()).isEqualTo("boolean()");
    }

    @Test
    void integerShape_producesBuiltinInteger() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("smithy.api#Integer")));
        assertThat(sym.getName()).isEqualTo("integer()");
    }

    @Test
    void timestampShape_producesBuiltinInteger() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("smithy.api#Timestamp")));
        assertThat(sym.getName()).isEqualTo("integer()");
    }

    @Test
    void documentShape_producesBuiltinTerm() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("smithy.api#Document")));
        assertThat(sym.getName()).isEqualTo("term()");
    }

    @Test
    void blobShape_producesBuiltinBinary() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("smithy.api#Blob")));
        assertThat(sym.getName()).isEqualTo("binary()");
    }

    // ------------------------------------------------------------------
    // smithy.shape property on all symbols
    // ------------------------------------------------------------------

    @Test
    void serviceShape_carriesSmithyShapeProperty() {
        Symbol sym = clientProvider.toSymbol(
                model.expectShape(ShapeId.from("example#Weather")));
        assertThat(sym.getProperty(ErlangSymbolProvider.PROPERTY_SHAPE)).isPresent();
    }

    // ------------------------------------------------------------------
    // Member naming
    // ------------------------------------------------------------------

    @Test
    void toMemberName_convertsCamelCaseToSnakeCase() {
        var memberShape = model.expectShape(ShapeId.from("example#GetCityInput$city"))
                .asMemberShape()
                .orElseThrow();
        assertThat(clientProvider.toMemberName(memberShape)).isEqualTo("city");
    }
}
