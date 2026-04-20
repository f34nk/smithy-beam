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
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;

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
 * Gradle task above) {@code updateAllModelSnapshots()} iterates every {@code .smithy}
 * file under {@code src/test/resources/model/}, runs all four plugins for each model,
 * and writes the output to {@code snapshots/<modelname>/<lang>/<role>/} — the same
 * directory layout produced by {@code generate_snapshots.sh}.
 *
 * <p>On a normal test run the generated content is compared byte-for-byte against the
 * stored golden file. If no golden file exists yet the individual test is skipped rather
 * than failed, so a clean checkout always compiles green.
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

    private static final Path MODEL_DIR =
            Paths.get(System.getProperty("user.dir"), "src/test/resources/model");

    // -------------------------------------------------------------------------
    // Update all models (runs only when -PupdateSnapshots=true)
    // -------------------------------------------------------------------------

    /**
     * Iterates every {@code .smithy} file under {@code model/}, runs all four plugins,
     * and writes snapshots to {@code snapshots/<modelname>/<lang>/<role>/}.
     * Mirrors the behaviour of {@code generate_snapshots.sh}.
     * This test is skipped on normal runs; it only executes when
     * {@code -PupdateSnapshots=true} is passed to Gradle.
     */
    @Test
    void updateAllModelSnapshots() throws IOException {
        assumeThat(UPDATE_SNAPSHOTS)
                .as("updateAllModelSnapshots only runs when -PupdateSnapshots=true")
                .isTrue();

        List<Path> modelFiles;
        try (var stream = Files.list(MODEL_DIR)) {
            modelFiles = stream
                    .filter(p -> p.toString().endsWith(".smithy"))
                    .sorted()
                    .collect(Collectors.toList());
        }

        for (Path modelFile : modelFiles) {
            String modelName = modelFile.getFileName().toString().replace(".smithy", "");

            Model model;
            try {
                // discoverModels() scans JAR-bundled Smithy models (e.g. aws.protocols traits
                // from smithy-aws-traits) so that protocol-specific models assemble cleanly.
                model = Model.assembler()
                        .discoverModels(SnapshotTest.class.getClassLoader())
                        .addImport(modelFile.toUri().toURL())
                        .assemble()
                        .unwrap();
            } catch (Exception e) {
                System.out.println("Skipping " + modelName + ": model assembly failed: " + e.getMessage());
                continue;
            }

            var services = model.getServiceShapes();
            if (services.isEmpty()) {
                System.out.println("Skipping " + modelName + ": no service shape found");
                continue;
            }
            ServiceShape service = services.iterator().next();
            String serviceId = service.getId().toString();
            String serviceName = service.getId().getName();

            System.out.println("Generating snapshots for: " + modelName + " (" + serviceId + ")");

            tryWritePlugin(modelName, "erlang", "client", () -> {
                MockManifest m = new MockManifest();
                new ErlangClientCodegenPlugin().execute(erlangContext(model, m, serviceId, modelName));
                return m;
            });
            tryWritePlugin(modelName, "erlang", "server", () -> {
                MockManifest m = new MockManifest();
                new ErlangServerCodegenPlugin().execute(erlangContext(model, m, serviceId, modelName));
                return m;
            });
            tryWritePlugin(modelName, "elixir", "client", () -> {
                MockManifest m = new MockManifest();
                new ElixirClientCodegenPlugin().execute(elixirContext(model, m, serviceId, serviceName));
                return m;
            });
            tryWritePlugin(modelName, "elixir", "server", () -> {
                MockManifest m = new MockManifest();
                new ElixirServerCodegenPlugin().execute(elixirContext(model, m, serviceId, serviceName));
                return m;
            });
        }
    }

    @FunctionalInterface
    private interface PluginRunner {
        MockManifest run() throws Exception;
    }

    /**
     * Runs a plugin and writes every generated file into
     * {@code snapshots/<modelName>/<lang>/<role>/<filename>}.
     * Logs and skips if the plugin throws (mirrors the shell script's best-effort approach).
     */
    private void tryWritePlugin(String modelName, String lang, String role, PluginRunner runner)
            throws IOException {
        try {
            MockManifest manifest = runner.run();
            for (Path path : manifest.getFiles()) {
                String filename = path.getFileName().toString();
                String content = manifest.expectFileString(path);
                writeSnapshot(modelName + "/" + lang + "/" + role + "/" + filename, content);
            }
        } catch (Exception e) {
            System.out.println(
                    "Skipping " + modelName + "/" + lang + "/" + role + ": " + e.getMessage());
        }
    }

    private void writeSnapshot(String relativePath, String content) throws IOException {
        Path target = SNAPSHOT_DIR.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

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
        return erlangContext(model, manifest, "example.weather#Weather", "weather");
    }

    private static PluginContext erlangContext(
            Model model, MockManifest manifest, String serviceId, String module) {
        ObjectNode settings = Node.objectNodeBuilder()
                .withMember("service", serviceId)
                .withMember("module", module)
                .withMember("edition", "2025")
                .build();
        return PluginContext.builder()
                .fileManifest(manifest)
                .model(model)
                .settings(settings)
                .build();
    }

    private static PluginContext elixirContext(Model model, MockManifest manifest) {
        return elixirContext(model, manifest, "example.weather#Weather", "Weather");
    }

    private static PluginContext elixirContext(
            Model model, MockManifest manifest, String serviceId, String namespace) {
        ObjectNode settings = Node.objectNodeBuilder()
                .withMember("service", serviceId)
                .withMember("namespace", namespace)
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
        assertOrUpdateSnapshot("weather/erlang/client/weather_client.erl", generated);
    }

    @Test
    void erlangClientWeatherTypesSnapshot() throws IOException {
        MockManifest manifest = runErlangClient(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_client_types.hrl");
        assertOrUpdateSnapshot("weather/erlang/client/weather_client_types.hrl", generated);
    }

    // -------------------------------------------------------------------------
    // Erlang server snapshots
    // -------------------------------------------------------------------------

    @Test
    void erlangServerWeatherServerSnapshot() throws IOException {
        MockManifest manifest = runErlangServer(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_server.erl");
        assertOrUpdateSnapshot("weather/erlang/server/weather_server.erl", generated);
    }

    @Test
    void erlangServerWeatherTypesSnapshot() throws IOException {
        MockManifest manifest = runErlangServer(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_server_types.hrl");
        assertOrUpdateSnapshot("weather/erlang/server/weather_server_types.hrl", generated);
    }

    // -------------------------------------------------------------------------
    // Elixir client snapshots
    // -------------------------------------------------------------------------

    @Test
    void elixirClientWeatherClientSnapshot() throws IOException {
        MockManifest manifest = runElixirClient(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_client.ex");
        assertOrUpdateSnapshot("weather/elixir/client/weather_client.ex", generated);
    }

    @Test
    void elixirClientWeatherTypesSnapshot() throws IOException {
        MockManifest manifest = runElixirClient(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_client_types.ex");
        assertOrUpdateSnapshot("weather/elixir/client/weather_client_types.ex", generated);
    }

    @Test
    void elixirClientWeatherErrorsSnapshot() throws IOException {
        MockManifest manifest = runElixirClient(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_client_errors.ex");
        assertOrUpdateSnapshot("weather/elixir/client/weather_client_errors.ex", generated);
    }

    // -------------------------------------------------------------------------
    // Elixir server snapshots
    // -------------------------------------------------------------------------

    @Test
    void elixirServerWeatherServerSnapshot() throws IOException {
        MockManifest manifest = runElixirServer(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_server.ex");
        assertOrUpdateSnapshot("weather/elixir/server/weather_server.ex", generated);
    }

    @Test
    void elixirServerWeatherTypesSnapshot() throws IOException {
        MockManifest manifest = runElixirServer(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_server_types.ex");
        assertOrUpdateSnapshot("weather/elixir/server/weather_server_types.ex", generated);
    }

    @Test
    void elixirServerWeatherErrorsSnapshot() throws IOException {
        MockManifest manifest = runElixirServer(weatherModel());
        String generated = manifest.expectFileString("src/generated/weather_server_errors.ex");
        assertOrUpdateSnapshot("weather/elixir/server/weather_server_errors.ex", generated);
    }

    // -------------------------------------------------------------------------
    // Core helper
    // -------------------------------------------------------------------------

    /**
     * In update mode: writes {@code actual} to the golden file and returns.
     * In compare mode: skips (via JUnit 5 assumption) if no golden exists; otherwise
     * asserts byte-for-byte equality.
     *
     * <p>{@code relativePath} must follow the same convention used by {@code generate_snapshots.sh}:
     * {@code <modelname>/<lang>/<role>/<filename>} (e.g. {@code weather/erlang/client/weather_client.erl}).
     */
    private void assertOrUpdateSnapshot(String relativePath, String actual) throws IOException {
        if (UPDATE_SNAPSHOTS) {
            writeSnapshot(relativePath, actual);
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
