package io.smithy.beam.erlang.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Snapshot-style tests for the Erlang client codegen plugin.
 *
 * <p>Runs the full plugin pipeline on a canonical Weather-like service model
 * and asserts key patterns in the generated output. Not a byte-for-byte
 * snapshot — asserts structural correctness so tests remain stable across
 * minor whitespace or ordering changes.
 */
class ErlangClientCodegenTest {

    private static final String WEATHER_MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace example.weather",
            "",
            "service Weather {",
            "    version: \"2006-03-01\"",
            "    operations: [GetCurrentTime, GetForecast]",
            "}",
            "",
            "operation GetCurrentTime {",
            "    input: GetCurrentTimeInput",
            "    output: GetCurrentTimeOutput",
            "}",
            "",
            "operation GetForecast {",
            "    input: GetForecastInput",
            "    output: GetForecastOutput",
            "    errors: [NoSuchResourceError]",
            "}",
            "",
            "structure GetCurrentTimeInput {}",
            "",
            "structure GetCurrentTimeOutput {",
            "    time: Timestamp",
            "}",
            "",
            "structure GetForecastInput {",
            "    cityId: String",
            "}",
            "",
            "structure GetForecastOutput {",
            "    chanceOfRain: Float",
            "}",
            "",
            "@error(\"client\")",
            "structure NoSuchResourceError {",
            "    resourceType: String",
            "}");

    private static MockManifest manifest;

    @BeforeAll
    static void runCodegen() {
        Model model = Model.assembler()
                .addUnparsedModel("weather.smithy", WEATHER_MODEL)
                .assemble()
                .unwrap();

        ObjectNode settings = Node.objectNodeBuilder()
                .withMember("service", "example.weather#Weather")
                .withMember("module", "weather")
                .withMember("edition", "2025")
                .build();

        manifest = new MockManifest();
        PluginContext ctx = PluginContext.builder()
                .fileManifest(manifest)
                .model(model)
                .settings(settings)
                .build();

        new ErlangClientCodegenPlugin().execute(ctx);
    }

    // -------------------------------------------------------------------------
    // Client module file
    // -------------------------------------------------------------------------

    @Test
    void clientModuleFileIsGenerated() {
        assertThat(manifest.getFileString("src/generated/weather_client.erl")).isPresent();
    }

    @Test
    void clientModuleDeclarationIsCorrect() {
        String content = manifest.expectFileString("src/generated/weather_client.erl");
        assertThat(content).startsWith("-module(weather_client).");
    }

    @Test
    void clientModuleExportsGetCurrentTime() {
        String content = manifest.expectFileString("src/generated/weather_client.erl");
        assertThat(content).contains("get_current_time/2");
    }

    @Test
    void clientModuleExportsGetForecast() {
        String content = manifest.expectFileString("src/generated/weather_client.erl");
        assertThat(content).contains("get_forecast/2");
    }

    @Test
    void clientModuleContainsGetCurrentTimeFunctionClause() {
        String content = manifest.expectFileString("src/generated/weather_client.erl");
        assertThat(content).contains("get_current_time(Config, Input) ->");
    }

    @Test
    void clientModuleContainsGetForecastFunctionClause() {
        String content = manifest.expectFileString("src/generated/weather_client.erl");
        assertThat(content).contains("get_forecast(Config, Input) ->");
    }

    @Test
    void clientModuleHasNotImplementedStub() {
        String content = manifest.expectFileString("src/generated/weather_client.erl");
        assertThat(content).contains("{error, not_implemented}");
    }

    // -------------------------------------------------------------------------
    // Types header file
    // -------------------------------------------------------------------------

    @Test
    void typesFileIsGenerated() {
        assertThat(manifest.getFileString("src/generated/weather_types.hrl")).isPresent();
    }

    @Test
    void typesFileContainsRecordForForecastInput() {
        String content = manifest.expectFileString("src/generated/weather_types.hrl");
        assertThat(content).contains("-record(get_forecast_input,");
    }

    @Test
    void typesFileContainsRecordForForecastOutput() {
        String content = manifest.expectFileString("src/generated/weather_types.hrl");
        assertThat(content).contains("-record(get_forecast_output,");
    }

    @Test
    void typesFileContainsErrorRecord() {
        String content = manifest.expectFileString("src/generated/weather_types.hrl");
        assertThat(content).contains("-record(no_such_resource_error,");
    }
}
