package io.smithy.beam.erlang.client;

import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangIntegration;
import io.smithy.beam.erlang.codegen.ErlangSettings;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.node.NodeMapper;

/**
 * Smithy build plugin that drives Erlang client code generation.
 *
 * <p>Plugin name: {@code "erlang-client-codegen"}.
 */
public final class ErlangClientCodegenPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "erlang-client-codegen";
    }

    @Override
    public void execute(PluginContext ctx) {
        CodegenDirector<ErlangWriter, ErlangIntegration, ErlangContext, ErlangSettings> runner =
                new CodegenDirector<>();

        // Deserialize as ErlangClientSettings (subtype of ErlangSettings) so that
        // client-specific fields (e.g. endpointResolver) are populated, while still
        // satisfying the S=ErlangSettings constraint imposed by ErlangContext.
        ErlangClientSettings settings =
                new NodeMapper().deserialize(ctx.getSettings(), ErlangClientSettings.class);

        runner.directedCodegen(new ErlangClientCodegen());
        runner.integrationClass(ErlangIntegration.class);
        runner.fileManifest(ctx.getFileManifest());
        runner.model(ctx.getModel());
        runner.settings(settings);
        runner.service(settings.getService());
        runner.performDefaultCodegenTransforms();
        runner.createDedicatedInputsAndOutputs();
        runner.run();
    }
}
