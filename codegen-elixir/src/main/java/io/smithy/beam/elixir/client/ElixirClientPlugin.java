package io.smithy.beam.elixir.client;

import io.smithy.beam.core.output.FileOutput;
import io.smithy.beam.core.pipeline.ClientPipeline;
import io.smithy.beam.core.protocol.ProtocolAnalyzerFactory;
import io.smithy.beam.core.settings.CodegenSettings;
import io.smithy.beam.elixir.writer.ElixirWriter;
import io.smithy.beam.protocols.ProtocolRegistrations;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Smithy Build plugin that generates an Elixir client from a Smithy model.
 *
 * <p>For each service, a single {@code <module>.ex} file is generated containing
 * type specs, codec helpers, and one public function per operation that returns
 * a {@code %SmithyClient.Operation{}} value ready for execution by the
 * {@code SmithyClient} runtime.
 *
 * <p>Plugin name: {@code elixir-client-codegen}.
 */
public final class ElixirClientPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "elixir-client-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        ProtocolRegistrations.init();
        CodegenSettings settings = CodegenSettings.fromNode(context.getSettings());
        Model model = context.getModel();
        ServiceShape service = model.expectShape(settings.serviceShapeId(), ServiceShape.class);
        var protocol = ProtocolAnalyzerFactory.forService(service, model);
        var writer = new ElixirWriter();
        var output = FileOutput.forPlugin(settings.outputDir());
        ClassLoader cl = ElixirClientPlugin.class.getClassLoader();
        new ClientPipeline().generate(service, model, protocol, writer, settings, output, cl);
    }
}
