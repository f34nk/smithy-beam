package io.smithy.beam.elixir.server;

import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirIntegration;
import io.smithy.beam.elixir.codegen.ElixirSettings;
import io.smithy.beam.elixir.codegen.ElixirWriter;
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
    }
}
