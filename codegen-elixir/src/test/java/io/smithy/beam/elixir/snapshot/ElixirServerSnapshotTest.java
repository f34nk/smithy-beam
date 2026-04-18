package io.smithy.beam.elixir.snapshot;

import io.smithy.beam.core.output.FileOutput;
import io.smithy.beam.core.pipeline.ServerPipeline;
import io.smithy.beam.core.protocol.ProtocolAnalyzerFactory;
import io.smithy.beam.core.settings.CodegenSettings;
import io.smithy.beam.elixir.writer.ElixirWriter;
import io.smithy.beam.protocols.ProtocolRegistrations;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pipeline-output snapshot tests for the Elixir server generator.
 *
 * <p>Each test drives the full {@link ServerPipeline} against a representative Smithy model
 * and asserts the generated {@code .ex} files match checked-in golden files under
 * {@code src/test/resources/golden/}.
 *
 * <p>To regenerate golden files after an intentional output change, run:
 * <pre>
 *   ./gradlew :codegen-elixir:test -Dbless=true
 * </pre>
 */
class ElixirServerSnapshotTest {

    private static final String SNAPSHOT_PKG = "io/smithy/beam/elixir/snapshot/";
    private static final Path GOLDEN_DIR = Path.of("src/test/resources/golden");

    // ── restJson1 — weather ───────────────────────────────────────────────────

    @Test
    void restJson1WeatherServerModuleSnapshot() throws Exception {
        MockManifest manifest = runPipeline(
                SNAPSHOT_PKG + "weather.smithy",
                "example.weather#WeatherService",
                "weather_server"
        );
        String actual = new String(manifest.expectFileBytes("src/generated/weather_server_server.ex"));
        assertGolden("restjson/weather_server_server.ex", actual);
    }

    @Test
    void restJson1WeatherImplScaffoldSnapshot() throws Exception {
        MockManifest manifest = runPipeline(
                SNAPSHOT_PKG + "weather.smithy",
                "example.weather#WeatherService",
                "weather_server"
        );
        String actual = new String(manifest.expectFileBytes("src/generated/weather_server_impl.ex"));
        assertGolden("restjson/weather_server_impl.ex", actual);
    }

    // ── pipeline helper ───────────────────────────────────────────────────────

    private static MockManifest runPipeline(String smithyResource, String serviceShapeId, String moduleName) {
        ProtocolRegistrations.init();
        ClassLoader cl = ElixirServerSnapshotTest.class.getClassLoader();
        Model model = Model.assembler(cl)
                .discoverModels(cl)
                .addImport(cl.getResource(smithyResource))
                .assemble()
                .unwrap();
        ServiceShape service = model.expectShape(ShapeId.from(serviceShapeId), ServiceShape.class);
        var protocol = ProtocolAnalyzerFactory.forService(service, model);
        CodegenSettings settings = CodegenSettings.builder()
                .serviceShapeId(service.getId())
                .moduleName(moduleName)
                .build();
        MockManifest manifest = new MockManifest();
        var output = new FileOutput(manifest);
        var writer = new ElixirWriter();
        new ServerPipeline().generate(service, model, protocol, writer, settings, output, cl);
        return manifest;
    }

    // ── snapshot assertion ────────────────────────────────────────────────────

    /**
     * Compares {@code actual} against the golden file at {@code GOLDEN_DIR/goldenName}.
     *
     * <ul>
     *   <li>If the system property {@code bless=true} is set, the golden file is written
     *       (created or overwritten) and the test passes unconditionally.</li>
     *   <li>If the golden file does not yet exist, it is created automatically so the test
     *       can be bootstrapped without a separate bless run.</li>
     *   <li>Otherwise the normalised content is compared and a diff is printed on mismatch.</li>
     * </ul>
     */
    private void assertGolden(String goldenName, String actual) throws IOException {
        boolean bless = "true".equals(System.getProperty("bless"));
        Path goldenFile = GOLDEN_DIR.resolve(goldenName);
        String normalizedActual = normalize(actual);

        if (bless || !Files.exists(goldenFile)) {
            Files.createDirectories(goldenFile.getParent());
            Files.writeString(goldenFile, normalizedActual);
            System.out.printf("[snapshot] %s golden: %s%n", bless ? "Blessed" : "Created", goldenFile);
            return;
        }

        String normalizedExpected = normalize(Files.readString(goldenFile));
        if (!normalizedActual.equals(normalizedExpected)) {
            String diff = unifiedDiff(normalizedExpected, normalizedActual);
            fail("Snapshot mismatch for: " + goldenName + "\n"
                    + "Re-bless with: ./gradlew :codegen-elixir:test -Dbless=true\n\n"
                    + diff);
        }
    }

    private static String normalize(String s) {
        return s.lines()
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"))
                .stripTrailing() + "\n";
    }

    private static String unifiedDiff(String expected, String actual) {
        List<String> expLines = expected.lines().collect(Collectors.toList());
        List<String> actLines = actual.lines().collect(Collectors.toList());
        int max = Math.max(expLines.size(), actLines.size());
        List<String> diff = new ArrayList<>();
        diff.add("--- expected");
        diff.add("+++ actual");
        for (int i = 0; i < max; i++) {
            String exp = i < expLines.size() ? expLines.get(i) : null;
            String act = i < actLines.size() ? actLines.get(i) : null;
            if (exp == null) {
                diff.add("+" + act);
            } else if (act == null) {
                diff.add("-" + exp);
            } else if (!exp.equals(act)) {
                diff.add("-" + exp);
                diff.add("+" + act);
            }
        }
        return String.join("\n", diff);
    }
}
