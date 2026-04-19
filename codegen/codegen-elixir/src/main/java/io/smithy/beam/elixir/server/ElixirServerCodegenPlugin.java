package io.smithy.beam.elixir.server;

import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirIntegration;
import io.smithy.beam.elixir.codegen.ElixirSettings;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.node.NodeMapper;

/**
 * Smithy build plugin that drives Elixir server code generation.
 *
 * <p>Plugin name: {@code "elixir-server-codegen"}.
 */
public final class ElixirServerCodegenPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "elixir-server-codegen";
    }

    @Override
    public void execute(PluginContext ctx) {
        CodegenDirector<ElixirWriter, ElixirIntegration, ElixirContext, ElixirSettings> runner =
                new CodegenDirector<>();

        // Deserialize as ElixirServerSettings (subtype of ElixirSettings) so that
        // server-specific fields (e.g. behaviourSuffix) are populated, while still
        // satisfying the S=ElixirSettings constraint imposed by ElixirContext.
        ElixirServerSettings settings =
                new NodeMapper().deserialize(ctx.getSettings(), ElixirServerSettings.class);

        runner.directedCodegen(new ElixirServerCodegen());
        runner.integrationClass(ElixirIntegration.class);
        runner.fileManifest(ctx.getFileManifest());
        runner.model(ctx.getModel());
        runner.settings(settings);
        runner.service(settings.getService());
        runner.performDefaultCodegenTransforms();
        runner.createDedicatedInputsAndOutputs();
        runner.run();

        copyToWorkingDirectory(ctx);
    }

    /**
     * Copies files written to the Smithy staging directory into the working
     * directory (i.e. next to {@code smithy-build.json}), so that
     * {@code outputDir} in the plugin settings is resolved relative to the
     * project root rather than relative to the internal build staging area.
     *
     * <p>The copy is best-effort: if a staged file cannot be read (e.g. when
     * running under a {@code MockManifest} in tests), the error is silently
     * ignored so that unit tests remain unaffected.
     */
    private static void copyToWorkingDirectory(PluginContext ctx) {
        Path stagingBase = ctx.getFileManifest().getBaseDir();
        Path workingDir = Path.of("").toAbsolutePath();
        for (Path file : ctx.getFileManifest().getFiles()) {
            // Skip files that don't exist on disk (e.g. MockManifest in tests).
            // Checking up-front avoids creating empty target directories.
            if (!Files.exists(file)) {
                continue;
            }
            try {
                Path relative = stagingBase.relativize(file);
                Path target = workingDir.resolve(relative);
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Best-effort copy; ignore I/O errors.
            }
        }
    }
}
