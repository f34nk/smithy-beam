package io.smithy.beam.codegen.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.smithy.beam.elixir.client.ElixirClientCodegenPlugin;
import io.smithy.beam.elixir.server.ElixirServerCodegenPlugin;
import io.smithy.beam.erlang.client.ErlangClientCodegenPlugin;
import io.smithy.beam.erlang.server.ErlangServerCodegenPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Snapshot tests that compare each plugin's output against golden files stored in
 * {@code src/test/resources/snapshots/}.
 *
 * <p>Golden files are generated (or regenerated) by running:
 * <pre>{@code
 *   ./gradlew :codegen:codegen-test:test -PupdateSnapshots
 * }</pre>
 *
 * <p>When {@code updateSnapshots} is {@code true} (passed as a system property by the
 * Gradle task above) this test writes the current output to the snapshot directory and
 * always passes. On a normal test run the generated content is compared byte-for-byte
 * against the stored golden file. If no golden file exists yet the individual test is
 * skipped rather than failed, so a clean checkout always compiles green — just run the
 * update command once to seed the goldens.
 */
class SnapshotTest {

    private static final boolean UPDATE_SNAPSHOTS =
            Boolean.parseBoolean(System.getProperty("updateSnapshots", "false"));

    /**
     * Resolved at test time from the Gradle project directory (which Gradle sets as
     * {@code user.dir} when running tests). Points to
     * {@code codegen/codegen-test/src/test/resources/snapshots}.
     */
    private static final Path SNAPSHOT_DIR =
            Paths.get(System.getProperty("user.dir"), "src/test/resources/snapshots");

    // -------------------------------------------------------------------------
    // Model + plugin helpers
    // -------------------------------------------------------------------------

    private static Model weatherModel() {
        URL resource = SnapshotTest.class.getResource("/model/weather.smithy");
        if (resource == null) {
            throw new IllegalStateException("weather.smithy not found on test classpath");
        }
        return Model.assembler()
                .addImport(resource)
                .assemble()
                .unwrap();
    }

    private static MockManifest runErlangClient(Model model) {
        MockManifest manifest = new MockManifest();
        new ErlangClientCodegenPlugin().execute(erlangContext(model, manifest));
        return manifest;
    }

    private static MockManifest runErlangServer(Model model) {
        MockManifest manifest = new MockManifest();
        new ErlangServerCodegenPlugin().execute(erlangContext(model, manifest));
        return manifest;
    }

    private static MockManifest runElixirClient(Model model) {
        MockManifest manifest = new MockManifest();
        new ElixirClientCodegenPlugin().execute(elixirContext(model, manifest));
        return manifest;
    }

    private static MockManifest runElixirServer(Model model) {
        MockManifest manifest = new MockManifest();
        new ElixirServerCodegenPlugin().execute(elixirContext(model, manifest));
        return manifest;
    }

    private static PluginContext erlangContext(Model model, MockManifest manifest) {
        ObjectNode settings = Node.objectNodeBuilder()
                .withMember("service", "example.weather#Weather")
                .withMember("module", "weather")
                .withMember("edition", "2025")
                .build();
        return PluginContext.builder()
                .fileManifest(manifest)
                .model(model)
                .settings(settings)
                .build();
    }

    private static PluginContext elixirContext(Model model, MockManifest manifest) {
        ObjectNode settings = Node.objectNodeBuilder()
                .withMember("service", "example.weather#Weather")
                .withMember("namespace", "Weather")
                .withMember("edition", "2025")
                .build();
        return PluginContext.builder()
                .fileManifest(manifest)
                .model(model)
                .settings(settings)
                .build();
    }

    // -------------------------------------------------------------------------
    // Erlang client snapshots
    // -------------------------------------------------------------------------

    @Test
    void erlangClientWeatherClientSnapshot() throws IOException {
        MockManifest manifest = runErlangClient(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_client.erl");
        assertOrUpdateSnapshot("erlang/client/weather_client.erl", generated);
    }

    @Test
    void erlangClientWeatherTypesSnapshot() throws IOException {
        MockManifest manifest = runErlangClient(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_client_types.hrl");
        assertOrUpdateSnapshot("erlang/client/weather_client_types.hrl", generated);
    }

    // -------------------------------------------------------------------------
    // Erlang server snapshots
    // -------------------------------------------------------------------------

    @Test
    void erlangServerWeatherServerSnapshot() throws IOException {
        MockManifest manifest = runErlangServer(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_server.erl");
        assertOrUpdateSnapshot("erlang/server/weather_server.erl", generated);
    }

    @Test
    void erlangServerWeatherTypesSnapshot() throws IOException {
        MockManifest manifest = runErlangServer(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_server_types.hrl");
        assertOrUpdateSnapshot("erlang/server/weather_server_types.hrl", generated);
    }

    // -------------------------------------------------------------------------
    // Elixir client snapshots
    // -------------------------------------------------------------------------

    @Test
    void elixirClientWeatherClientSnapshot() throws IOException {
        MockManifest manifest = runElixirClient(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_client.ex");
        assertOrUpdateSnapshot("elixir/client/weather_client.ex", generated);
    }

    @Test
    void elixirClientWeatherTypesSnapshot() throws IOException {
        MockManifest manifest = runElixirClient(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_client_types.ex");
        assertOrUpdateSnapshot("elixir/client/weather_client_types.ex", generated);
    }

    @Test
    void elixirClientWeatherErrorsSnapshot() throws IOException {
        MockManifest manifest = runElixirClient(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_client_errors.ex");
        assertOrUpdateSnapshot("elixir/client/weather_client_errors.ex", generated);
    }

    // -------------------------------------------------------------------------
    // Elixir server snapshots
    // -------------------------------------------------------------------------

    @Test
    void elixirServerWeatherServerSnapshot() throws IOException {
        MockManifest manifest = runElixirServer(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_server.ex");
        assertOrUpdateSnapshot("elixir/server/weather_server.ex", generated);
    }

    @Test
    void elixirServerWeatherTypesSnapshot() throws IOException {
        MockManifest manifest = runElixirServer(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_server_types.ex");
        assertOrUpdateSnapshot("elixir/server/weather_server_types.ex", generated);
    }

    @Test
    void elixirServerWeatherErrorsSnapshot() throws IOException {
        MockManifest manifest = runElixirServer(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_server_errors.ex");
        assertOrUpdateSnapshot("elixir/server/weather_server_errors.ex", generated);
    }

    // -------------------------------------------------------------------------
    // Core helper
    // -------------------------------------------------------------------------

    /**
     * In update mode: writes {@code actual} to the golden file and returns.
     * In compare mode: skips (via JUnit 5 assumption) if no golden exists; otherwise
     * asserts byte-for-byte equality.
     */
    private void assertOrUpdateSnapshot(String relativePath, String actual) throws IOException {
        if (UPDATE_SNAPSHOTS) {
            Path target = SNAPSHOT_DIR.resolve(relativePath);
            Files.createDirectories(target.getParent());
            Files.writeString(target, actual, StandardCharsets.UTF_8);
            return;
        }

        InputStream is = getClass().getResourceAsStream("/snapshots/" + relativePath);
        assumeThat(is)
                .as("Snapshot not found: %s — run './gradlew :codegen:codegen-test:test"
                        + " -PupdateSnapshots' to generate golden files",
                        relativePath)
                .isNotNull();

        String expected = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        assertThat(actual)
                .as("Snapshot mismatch for %s", relativePath)
                .isEqualTo(expected);
    }
}
