package io.smithy.beam.core;

import io.smithy.beam.core.output.FileOutput;
import io.smithy.beam.core.pipeline.ServerPipeline;
import io.smithy.beam.core.protocol.ProtocolAnalyzer;
import io.smithy.beam.core.settings.CodegenSettings;
import io.smithy.beam.core.writer.LanguageWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.function.Supplier;

/**
 * Convenience base class for plugins that drive the {@link ServerPipeline}.
 *
 * <p>Leaf plugins extend this class and supply only the plugin name, writer
 * factory, and protocol initializer:
 * <pre>{@code
 * public final class ErlangServerPlugin extends ServerPluginRunner {
 *     public ErlangServerPlugin() {
 *         super("erlang-server-codegen", ErlangWriter::new, ProtocolRegistrations::init);
 *     }
 * }
 * }</pre>
 */
public abstract class ServerPluginRunner extends PluginRunner {

    protected ServerPluginRunner(String name, Supplier<LanguageWriter> writerFactory, Runnable initializer) {
        super(name, writerFactory, initializer);
    }

    @Override
    protected final void run(
            ServiceShape service,
            Model model,
            CodegenSettings settings,
            ProtocolAnalyzer protocol,
            LanguageWriter writer,
            FileOutput output,
            ClassLoader resourceLoader) {
        new ServerPipeline().generate(service, model, protocol, writer, settings, output, resourceLoader);
    }
}
