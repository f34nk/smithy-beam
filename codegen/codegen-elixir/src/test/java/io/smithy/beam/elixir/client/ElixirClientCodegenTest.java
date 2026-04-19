package io.smithy.beam.elixir.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Snapshot-style tests for the Elixir client codegen plugin.
 *
 * <p>Runs the full plugin pipeline on a canonical Weather-like service model
 * and asserts key patterns in the generated output. Not a byte-for-byte
 * snapshot — asserts structural correctness so tests remain stable across
 * minor whitespace or ordering changes.
 */
class ElixirClientCodegenTest {

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
                .withMember("namespace", "Weather")
                .withMember("edition", "2025")
                .build();

        manifest = new MockManifest();
        PluginContext ctx = PluginContext.builder()
                .fileManifest(manifest)
                .model(model)
                .settings(settings)
                .build();

        new ElixirClientCodegenPlugin().execute(ctx);
    }

    // -------------------------------------------------------------------------
    // Client module file
    // -------------------------------------------------------------------------

    @Test
    void clientModuleFileIsGenerated() {
        assertThat(manifest.getFileString("src/generated/weather_client.ex")).isPresent();
    }

    @Test
    void clientModuleDeclarationIsCorrect() {
        String content = manifest.expectFileString("src/generated/weather_client.ex");
        assertThat(content).contains("defmodule Weather.Client do");
    }

    @Test
    void clientModuleContainsGetCurrentTimeFunctionClause() {
        String content = manifest.expectFileString("src/generated/weather_client.ex");
        assertThat(content).contains("def get_current_time(config, input) do");
    }

    @Test
    void clientModuleContainsGetForecastFunctionClause() {
        String content = manifest.expectFileString("src/generated/weather_client.ex");
        assertThat(content).contains("def get_forecast(config, input) do");
    }

    @Test
    void clientModuleHasNotImplementedStub() {
        String content = manifest.expectFileString("src/generated/weather_client.ex");
        assertThat(content).contains("{:error, :not_implemented}");
    }

    @Test
    void clientModuleDoesNotContainServerStyleHandlerCallbacks() {
        String content = manifest.expectFileString("src/generated/weather_client.ex");
        assertThat(content).doesNotContain("def handle_get_current_time");
        assertThat(content).doesNotContain("def handle_get_forecast");
    }

    // -------------------------------------------------------------------------
    // Types file
    // -------------------------------------------------------------------------

    @Test
    void typesFileIsGenerated() {
        assertThat(manifest.getFileString("src/generated/weather_client_types.ex")).isPresent();
    }

    @Test
    void typesFileContainsDefmoduleForForecastInput() {
        String content = manifest.expectFileString("src/generated/weather_client_types.ex");
        assertThat(content).contains("defmodule Weather.Client.Types.GetForecastInput");
    }

    @Test
    void typesFileContainsDefmoduleForForecastOutput() {
        String content = manifest.expectFileString("src/generated/weather_client_types.ex");
        assertThat(content).contains("defmodule Weather.Client.Types.GetForecastOutput");
    }

    @Test
    void typesFileContainsDefmoduleForCurrentTimeOutput() {
        String content = manifest.expectFileString("src/generated/weather_client_types.ex");
        assertThat(content).contains("defmodule Weather.Client.Types.GetCurrentTimeOutput");
    }

    // -------------------------------------------------------------------------
    // Errors file
    // -------------------------------------------------------------------------

    @Test
    void errorsFileIsGenerated() {
        assertThat(manifest.getFileString("src/generated/weather_client_errors.ex")).isPresent();
    }

    @Test
    void errorsFileContainsDefmoduleForNoSuchResourceError() {
        String content = manifest.expectFileString("src/generated/weather_client_errors.ex");
        assertThat(content).contains("defmodule Weather.Client.Errors.NoSuchResourceError");
    }

    @Test
    void errorsFileContainsDefexceptionForErrorShape() {
        String content = manifest.expectFileString("src/generated/weather_client_errors.ex");
        assertThat(content).contains("defexception");
    }
}
