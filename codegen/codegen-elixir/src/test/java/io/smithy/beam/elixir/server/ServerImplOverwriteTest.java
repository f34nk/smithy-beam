package io.smithy.beam.elixir.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Covers the "WILL NOT BE OVERWRITTEN" guarantee for {@code *_server_impl.ex}.
 *
 * <p>Uses a {@link MockManifest} so the rest of the codegen output stays
 * in memory; the existence check in {@link io.smithy.beam.core.ImplFileGuard}
 * keys off the {@code projectRoot} setting, which is wired to a temp dir
 * for both scenarios.
 */
class ServerImplOverwriteTest {

    private static final String IMPL_PATH = "src/generated/weather_server_impl.ex";

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace example.weather",
            "",
            "service Weather { version: \"2024-01-01\", operations: [Op] }",
            "operation Op { input: OpInput, output: OpOutput }",
            "structure OpInput {}",
            "structure OpOutput {}");

    @Test
    void implFileIsEnqueuedWhenAbsentFromProjectRoot(@TempDir Path projectRoot) {
        MockManifest manifest = runCodegen(projectRoot);

        assertThat(manifest.getFileString(IMPL_PATH))
                .isPresent()
                .hasValueSatisfying(c -> assertThat(c).contains("This file will NOT be overwritten"));
    }

    @Test
    void implFileIsSkippedWhenAlreadyPresentInProjectRoot(@TempDir Path projectRoot) throws IOException {
        Path impl = projectRoot.resolve(IMPL_PATH);
        Files.createDirectories(impl.getParent());
        Files.writeString(impl, "# hand-edited by the user — do not touch\n");

        MockManifest manifest = runCodegen(projectRoot);

        assertThat(manifest.getFileString(IMPL_PATH))
                .as("guard must not enqueue the impl file when one already exists on disk")
                .isEmpty();
    }

    private static MockManifest runCodegen(Path projectRoot) {
        Model model = Model.assembler()
                .addUnparsedModel("weather.smithy", MODEL)
                .assemble()
                .unwrap();

        ObjectNode settings = Node.objectNodeBuilder()
                .withMember("service", "example.weather#Weather")
                .withMember("namespace", "Weather")
                .withMember("edition", "2025")
                .withMember("projectRoot", projectRoot.toAbsolutePath().toString())
                .build();

        MockManifest manifest = new MockManifest();
        PluginContext ctx = PluginContext.builder()
                .fileManifest(manifest)
                .model(model)
                .settings(settings)
                .build();

        new ElixirServerCodegenPlugin().execute(ctx);
        return manifest;
    }
}
