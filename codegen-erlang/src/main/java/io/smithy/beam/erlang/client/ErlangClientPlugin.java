package io.smithy.beam.erlang.client;

import io.smithy.beam.core.output.FileOutput;
import io.smithy.beam.core.pipeline.ClientPipeline;
import io.smithy.beam.core.protocol.ProtocolAnalyzerFactory;
import io.smithy.beam.core.settings.CodegenSettings;
import io.smithy.beam.erlang.writer.ErlangWriter;
import io.smithy.beam.protocols.ProtocolRegistrations;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

public final class ErlangClientPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "erlang-client-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        ProtocolRegistrations.init();
        CodegenSettings settings = CodegenSettings.fromNode(context.getSettings());
        Model model = context.getModel();
        ServiceShape service = model.expectShape(settings.serviceShapeId(), ServiceShape.class);
        var protocol = ProtocolAnalyzerFactory.forService(service, model);
        var writer = new ErlangWriter();
        var output = new FileOutput(context.getFileManifest(), writer.fileExtension());
        ClassLoader cl = ErlangClientPlugin.class.getClassLoader();
        new ClientPipeline().generate(service, model, protocol, writer, settings, output, cl);
    }
}
