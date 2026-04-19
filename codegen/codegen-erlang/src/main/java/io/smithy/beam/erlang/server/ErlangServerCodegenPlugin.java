package io.smithy.beam.erlang.server;

import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangIntegration;
import io.smithy.beam.erlang.codegen.ErlangSettings;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;
import software.amazon.smithy.model.node.NodeMapper;

/**
 * Smithy build plugin that drives Erlang server code generation.
 *
 * <p>Plugin name: {@code "erlang-server-codegen"}.
 */
public final class ErlangServerCodegenPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "erlang-server-codegen";
    }

    @Override
    public void execute(PluginContext ctx) {
        CodegenDirector<ErlangWriter, ErlangIntegration, ErlangContext, ErlangSettings> runner =
                new CodegenDirector<>();

        // Deserialize as ErlangServerSettings (subtype of ErlangSettings) so that
        // server-specific fields (e.g. behaviourSuffix) are populated, while still
        // satisfying the S=ErlangSettings constraint imposed by ErlangContext.
        ErlangServerSettings settings =
                new NodeMapper().deserialize(ctx.getSettings(), ErlangServerSettings.class);

        runner.directedCodegen(new ErlangServerCodegen());
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
