package io.smithy.beam.elixir.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Snapshot-style tests for the Elixir server codegen plugin.
 *
 * <p>Runs the full plugin pipeline on a canonical Weather-like service model
 * and asserts key structural patterns in the generated output.
 */
class ElixirServerCodegenTest {

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

        new ElixirServerCodegenPlugin().execute(ctx);
    }

    // -------------------------------------------------------------------------
    // Server module file
    // -------------------------------------------------------------------------

    @Test
    void serverModuleFileIsGenerated() {
        assertThat(manifest.getFileString("src/generated/weather_server.ex")).isPresent();
    }

    @Test
    void serverModuleDeclarationIsCorrect() {
        String content = manifest.expectFileString("src/generated/weather_server.ex");
        assertThat(content).contains("defmodule Weather.Server do");
    }

    @Test
    void serverModuleContainsHandleGetCurrentTimeFunctionClause() {
        String content = manifest.expectFileString("src/generated/weather_server.ex");
        assertThat(content).contains("def handle_get_current_time(request, state) do");
    }

    @Test
    void serverModuleContainsHandleGetForecastFunctionClause() {
        String content = manifest.expectFileString("src/generated/weather_server.ex");
        assertThat(content).contains("def handle_get_forecast(request, state) do");
    }

    @Test
    void serverModuleHasNotImplementedStub() {
        String content = manifest.expectFileString("src/generated/weather_server.ex");
        assertThat(content).contains("{:error, :not_implemented}");
    }

    // -------------------------------------------------------------------------
    // Types file
    // -------------------------------------------------------------------------

    @Test
    void typesFileIsGenerated() {
        assertThat(manifest.getFileString("src/generated/weather_types.ex")).isPresent();
    }

    @Test
    void typesFileContainsDefmoduleForForecastInput() {
        String content = manifest.expectFileString("src/generated/weather_types.ex");
        assertThat(content).contains("defmodule GetForecastInput");
    }

    @Test
    void typesFileContainsDefmoduleForForecastOutput() {
        String content = manifest.expectFileString("src/generated/weather_types.ex");
        assertThat(content).contains("defmodule GetForecastOutput");
    }

    // -------------------------------------------------------------------------
    // Errors file
    // -------------------------------------------------------------------------

    @Test
    void errorsFileIsGenerated() {
        assertThat(manifest.getFileString("src/generated/weather_errors.ex")).isPresent();
    }

    @Test
    void errorsFileContainsDefmoduleForNoSuchResourceError() {
        String content = manifest.expectFileString("src/generated/weather_errors.ex");
        assertThat(content).contains("defmodule NoSuchResourceError");
    }

    // -------------------------------------------------------------------------
    // Server module does NOT contain client-style operation function clauses
    // -------------------------------------------------------------------------

    @Test
    void serverModuleDoesNotContainClientStyleOperationSignature() {
        String content = manifest.expectFileString("src/generated/weather_server.ex");
        assertThat(content).doesNotContain("def get_forecast(config, input)");
        assertThat(content).doesNotContain("def get_current_time(config, input)");
    }
}
