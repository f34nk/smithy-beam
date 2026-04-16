package io.smithy.beam.elixir.server;

import io.smithy.beam.core.output.FileOutput;
import io.smithy.beam.core.pipeline.ServerPipeline;
import io.smithy.beam.core.protocol.ProtocolAnalyzerFactory;
import io.smithy.beam.core.settings.CodegenSettings;
import io.smithy.beam.elixir.writer.ElixirWriter;
import io.smithy.beam.protocols.ProtocolRegistrations;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Smithy Build plugin that generates an Elixir server skeleton from a Smithy model.
 *
 * <p>For each service, two files are generated:
 * <ol>
 *   <li>{@code <Module>.Dispatcher.ex} — consolidated module containing type specs,
 *       {@code @callback} declarations, Plug-compatible routing, dispatch, deserialization,
 *       and serialization.</li>
 *   <li>{@code <Module>.Impl.ex} — once-written stub scaffold; never overwritten on subsequent runs.</li>
 * </ol>
 *
 * <p>Plugin name: {@code elixir-server-codegen}.
 */
public final class ElixirServerPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "elixir-server-codegen";
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
        ClassLoader cl = ElixirServerPlugin.class.getClassLoader();
        new ServerPipeline().generate(service, model, protocol, writer, settings, output, cl);
    }
}
