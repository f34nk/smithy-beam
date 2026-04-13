package io.smithy.beam.erlang.server;

import io.smithy.beam.core.output.FileOutput;
import io.smithy.beam.core.pipeline.ServerPipeline;
import io.smithy.beam.core.protocol.ProtocolAnalyzerFactory;
import io.smithy.beam.core.settings.CodegenSettings;
import io.smithy.beam.erlang.writer.ErlangWriter;
import io.smithy.beam.protocols.ProtocolRegistrations;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Smithy Build plugin that generates an Erlang server skeleton from a Smithy model.
 *
 * <p>For each service, four files are generated:
 * <ol>
 *   <li>{@code <svc>_handler.erl} — {@code -behaviour} definition with one {@code -callback} per operation.</li>
 *   <li>{@code <svc>_router.erl} — {@code route/2} function mapping requests to operation atoms.</li>
 *   <li>{@code <svc>_dispatcher.erl} — {@code handle/3} that routes, deserializes, dispatches, and serializes.</li>
 *   <li>{@code <svc>_impl.erl} — once-written stub scaffold; never overwritten on subsequent runs.</li>
 * </ol>
 *
 * <p>Plugin name: {@code erlang-server-codegen}.
 */
public final class ErlangServerPlugin implements SmithyBuildPlugin {

    @Override
    public String getName() {
        return "erlang-server-codegen";
    }

    @Override
    public void execute(PluginContext context) {
        ProtocolRegistrations.init();
        CodegenSettings settings = CodegenSettings.fromNode(context.getSettings());
        Model         model      = context.getModel();
        ServiceShape  service    = model.expectShape(settings.serviceShapeId(), ServiceShape.class);
        var protocol = ProtocolAnalyzerFactory.forService(service, model);
        var writer   = new ErlangWriter();
        var output   = FileOutput.forPlugin(settings.outputDir());
        ClassLoader cl = ErlangServerPlugin.class.getClassLoader();
        new ServerPipeline().generate(service, model, protocol, writer, settings, output, cl);
    }
}
