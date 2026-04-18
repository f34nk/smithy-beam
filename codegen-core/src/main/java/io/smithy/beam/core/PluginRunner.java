package io.smithy.beam.core;

import io.smithy.beam.core.output.FileOutput;
import io.smithy.beam.core.protocol.ProtocolAnalyzer;
import io.smithy.beam.core.protocol.ProtocolAnalyzerFactory;
import io.smithy.beam.core.settings.CodegenSettings;
import io.smithy.beam.core.writer.LanguageWriter;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.build.SmithyBuildPlugin;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Abstract base for all smithy-beam Smithy build plugins.
 *
 * <p>Captures the repetitive orchestration shared by every plugin — settings parsing,
 * service-shape lookup, protocol resolution, writer construction, and output setup —
 * behind a single {@code final execute()} implementation. Concrete subclasses only
 * need to supply a name, a writer factory, and a protocol initializer via
 * {@link #PluginRunner(String, Supplier, Runnable)}.
 *
 * <p>The actual pipeline invocation (client vs. server) is delegated to
 * {@link #run(ServiceShape, Model, CodegenSettings, ProtocolAnalyzer, LanguageWriter, FileOutput, ClassLoader)},
 * which is implemented by {@link ClientPluginRunner} and {@link ServerPluginRunner}.
 */
public abstract class PluginRunner implements SmithyBuildPlugin {

    private final String pluginName;
    private final Supplier<LanguageWriter> writerFactory;
    private final Runnable initializer;

    /**
     * @param name          the plugin name returned by {@link #getName()}
     * @param writerFactory produces a fresh {@link LanguageWriter}; also used to derive
     *                      the {@link ClassLoader} for runtime-resource loading
     * @param initializer   called once per {@link #execute} invocation before any
     *                      protocol resolution (use to register protocol analyzers)
     */
    protected PluginRunner(String name, Supplier<LanguageWriter> writerFactory, Runnable initializer) {
        this.pluginName    = Objects.requireNonNull(name, "name");
        this.writerFactory = Objects.requireNonNull(writerFactory, "writerFactory");
        this.initializer   = Objects.requireNonNull(initializer, "initializer");
    }

    @Override
    public final String getName() {
        return pluginName;
    }

    @Override
    public final void execute(PluginContext context) {
        initializer.run();
        CodegenSettings settings = CodegenSettings.fromNode(context.getSettings());
        Model        model   = context.getModel();
        ServiceShape service = model.expectShape(settings.serviceShapeId(), ServiceShape.class);
        var protocol = ProtocolAnalyzerFactory.forService(service, model);
        LanguageWriter writer = writerFactory.get();
        var output   = FileOutput.forPlugin(settings.outputDir());
        ClassLoader cl = writer.getClass().getClassLoader();
        run(service, model, settings, protocol, writer, output, cl);
    }

    /**
     * Invoked by {@link #execute} after all shared setup is complete.
     *
     * @param service        resolved service shape
     * @param model          the full Smithy model
     * @param settings       parsed plugin configuration
     * @param protocol       resolved protocol analyzer for the service
     * @param writer         language-specific writer
     * @param output         file-output handle (filesystem mode)
     * @param resourceLoader class loader for runtime-resource copying
     */
    protected abstract void run(
            ServiceShape service,
            Model model,
            CodegenSettings settings,
            ProtocolAnalyzer protocol,
            LanguageWriter writer,
            FileOutput output,
            ClassLoader resourceLoader);
}
