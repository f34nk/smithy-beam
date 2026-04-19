package io.smithy.beam.erlang.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Snapshot-style tests for the Erlang server codegen plugin.
 *
 * <p>Runs the full plugin pipeline on a canonical Weather-like service model
 * and asserts key structural patterns in the generated output.
 */
class ErlangServerCodegenTest {

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

        new ErlangServerCodegenPlugin().execute(ctx);
    }

    // -------------------------------------------------------------------------
    // Server module file
    // -------------------------------------------------------------------------

    @Test
    void serverModuleFileIsGenerated() {
        assertThat(manifest.getFileString("src/generated/weather_server.erl")).isPresent();
    }

    @Test
    void serverModuleDeclarationIsCorrect() {
        String content = manifest.expectFileString("src/generated/weather_server.erl");
        assertThat(content).startsWith("-module(weather_server).");
    }

    @Test
    void serverModuleContainsBehaviourAttribute() {
        String content = manifest.expectFileString("src/generated/weather_server.erl");
        assertThat(content).contains("-behaviour(smithy_handler).");
    }

    @Test
    void serverModuleExportsHandleGetCurrentTime() {
        String content = manifest.expectFileString("src/generated/weather_server.erl");
        assertThat(content).contains("handle_get_current_time/2");
    }

    @Test
    void serverModuleExportsHandleGetForecast() {
        String content = manifest.expectFileString("src/generated/weather_server.erl");
        assertThat(content).contains("handle_get_forecast/2");
    }

    @Test
    void serverModuleContainsHandleGetCurrentTimeFunctionClause() {
        String content = manifest.expectFileString("src/generated/weather_server.erl");
        assertThat(content).contains("handle_get_current_time(Req, State) ->");
    }

    @Test
    void serverModuleContainsHandleGetForecastFunctionClause() {
        String content = manifest.expectFileString("src/generated/weather_server.erl");
        assertThat(content).contains("handle_get_forecast(Req, State) ->");
    }

    @Test
    void serverModuleHasNotImplementedStub() {
        String content = manifest.expectFileString("src/generated/weather_server.erl");
        assertThat(content).contains("{error, not_implemented}");
    }

    // -------------------------------------------------------------------------
    // Types header file
    // -------------------------------------------------------------------------

    @Test
    void typesFileIsGenerated() {
        assertThat(manifest.getFileString("src/generated/weather_server_types.hrl")).isPresent();
    }

    @Test
    void typesFileHasModeSuffixedModuleAttribute() {
        String content = manifest.expectFileString("src/generated/weather_server_types.hrl");
        assertThat(content).startsWith("-module(weather_server_types).");
    }

    @Test
    void typesFileContainsRecordForForecastInput() {
        String content = manifest.expectFileString("src/generated/weather_server_types.hrl");
        assertThat(content).contains("-record(get_forecast_input,");
    }

    @Test
    void typesFileContainsRecordForForecastOutput() {
        String content = manifest.expectFileString("src/generated/weather_server_types.hrl");
        assertThat(content).contains("-record(get_forecast_output,");
    }

    @Test
    void typesFileContainsErrorRecord() {
        String content = manifest.expectFileString("src/generated/weather_server_types.hrl");
        assertThat(content).contains("-record(no_such_resource_error,");
    }

    // -------------------------------------------------------------------------
    // Server module does NOT contain client-style operation function clauses
    // -------------------------------------------------------------------------

    @Test
    void serverModuleDoesNotContainClientStyleOperationSignature() {
        String content = manifest.expectFileString("src/generated/weather_server.erl");
        assertThat(content).doesNotContain("get_forecast(Config, Input)");
        assertThat(content).doesNotContain("get_current_time(Config, Input)");
    }
}
