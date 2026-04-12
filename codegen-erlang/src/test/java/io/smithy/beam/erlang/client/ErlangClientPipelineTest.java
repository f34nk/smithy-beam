package io.smithy.beam.erlang.client;

import io.smithy.beam.core.output.FileOutput;
import io.smithy.beam.core.pipeline.ClientPipeline;
import io.smithy.beam.core.protocol.ProtocolAnalyzerFactory;
import io.smithy.beam.core.settings.CodegenSettings;
import io.smithy.beam.erlang.writer.ErlangWriter;
import io.smithy.beam.protocols.ProtocolRegistrations;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangClientPipelineTest {

    @Test
    void generateWritesEmptyClientModuleAndCopiesRuntime() {
        ProtocolRegistrations.init();
        Model model = Model.assembler(getClass().getClassLoader())
                .discoverModels(getClass().getClassLoader())
                .addImport(getClass().getResource("weather.smithy"))
                .assemble()
                .unwrap();
        ServiceShape service = model.expectShape(ShapeId.from("example.weather#WeatherService"), ServiceShape.class);
        var protocol = ProtocolAnalyzerFactory.forService(service, model);
        CodegenSettings settings =
                CodegenSettings.builder().serviceShapeId(service.getId()).moduleName("weather_client").build();
        MockManifest manifest = new MockManifest();
        var output = new FileOutput(manifest, ".erl");
        var writer = new ErlangWriter();
        new ClientPipeline()
                .generate(service, model, protocol, writer, settings, output, getClass().getClassLoader());

        assertThat(manifest.hasFile("src/generated/weather_client.erl")).isTrue();
        assertThat(manifest.expectFileString("src/generated/weather_client.erl")).isEmpty();
        assertThat(manifest.hasFile("client/aws_retry.erl")).isTrue();
        assertThat(manifest.hasFile("client/aws_config.erl")).isTrue();
    }

    @Test
    void copyRuntimeBundlesAwsSigV4Resource() {
        MockManifest manifest = new MockManifest();
        FileOutput out = new FileOutput(manifest, ".erl");
        out.copyRuntime("erlang", "client/aws_sigv4.erl", ErlangClientPlugin.class.getClassLoader());
        assertThat(manifest.hasFile("client/aws_sigv4.erl")).isTrue();
        assertThat(manifest.expectFileBytes("client/aws_sigv4.erl").length).isPositive();
    }
}
