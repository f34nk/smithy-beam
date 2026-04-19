package io.smithy.beam.elixir.client;

import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirIntegration;
import io.smithy.beam.elixir.codegen.ElixirSettings;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.node.NodeMapper;

/**
 * Smithy build plugin that drives Elixir client code generation.
 *
 * <p>Plugin name: {@code "elixir-client-codegen"}.
 */
public final class ElixirClientCodegenPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "elixir-client-codegen";
    }

    @Override
    public void execute(PluginContext ctx) {
        CodegenDirector<ElixirWriter, ElixirIntegration, ElixirContext, ElixirSettings> runner =
                new CodegenDirector<>();

        // Deserialize as ElixirClientSettings (subtype of ElixirSettings) so that
        // client-specific fields (e.g. endpointResolver) are populated, while still
        // satisfying the S=ElixirSettings constraint imposed by ElixirContext.
        ElixirClientSettings settings =
                new NodeMapper().deserialize(ctx.getSettings(), ElixirClientSettings.class);

        runner.directedCodegen(new ElixirClientCodegen());
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
