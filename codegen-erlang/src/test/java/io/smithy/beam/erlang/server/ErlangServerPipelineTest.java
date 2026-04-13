package io.smithy.beam.erlang.server;

import io.smithy.beam.core.output.FileOutput;
import io.smithy.beam.core.pipeline.ServerPipeline;
import io.smithy.beam.core.protocol.ProtocolAnalyzerFactory;
import io.smithy.beam.core.settings.CodegenSettings;
import io.smithy.beam.erlang.writer.ErlangWriter;
import io.smithy.beam.protocols.ProtocolRegistrations;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangServerPipelineTest {

    private static final String SVC = "example.weather#WeatherService";
    private static final String BASE = "weather_server";

    private static Model loadWeatherModel(ClassLoader cl) {
        ProtocolRegistrations.init();
        return Model.assembler(cl)
                .discoverModels(cl)
                // Re-use the weather.smithy fixture from the client test resources.
                .addImport(cl.getResource("io/smithy/beam/erlang/client/weather.smithy"))
                .assemble()
                .unwrap();
    }

    private static MockManifest runPipeline(String moduleName) {
        ClassLoader cl = ErlangServerPipelineTest.class.getClassLoader();
        Model model = loadWeatherModel(cl);
        ServiceShape service = model.expectShape(ShapeId.from(SVC), ServiceShape.class);
        var protocol = ProtocolAnalyzerFactory.forService(service, model);
        CodegenSettings settings = CodegenSettings.builder()
                .serviceShapeId(service.getId())
                .moduleName(moduleName)
                .build();
        MockManifest manifest = new MockManifest();
        var output = new FileOutput(manifest, ".erl");
        var writer = new ErlangWriter();
        new ServerPipeline().generate(service, model, protocol, writer, settings, output, cl);
        return manifest;
    }

    // ── Four-file generation ──────────────────────────────────────────────────

    @Test
    void generatesHandlerFile() {
        MockManifest manifest = runPipeline(BASE);
        assertThat(manifest.hasFile("src/generated/" + BASE + "_handler.erl")).isTrue();
    }

    @Test
    void generatesRouterFile() {
        MockManifest manifest = runPipeline(BASE);
        assertThat(manifest.hasFile("src/generated/" + BASE + "_router.erl")).isTrue();
    }

    @Test
    void generatesDispatcherFile() {
        MockManifest manifest = runPipeline(BASE);
        assertThat(manifest.hasFile("src/generated/" + BASE + "_dispatcher.erl")).isTrue();
    }

    @Test
    void generatesImplScaffoldFile() {
        MockManifest manifest = runPipeline(BASE);
        assertThat(manifest.hasFile("src/generated/" + BASE + "_impl.erl")).isTrue();
    }

    // ── Runtime modules copied ────────────────────────────────────────────────

    @Test
    void copiesSmithyServerRuntime() {
        MockManifest manifest = runPipeline(BASE);
        assertThat(manifest.hasFile("smithy_server.erl")).isTrue();
    }

    @Test
    void copiesSmithyValidatorRuntime() {
        MockManifest manifest = runPipeline(BASE);
        assertThat(manifest.hasFile("smithy_validator.erl")).isTrue();
    }

    @Test
    void copiesSmithyErrorMapRuntime() {
        MockManifest manifest = runPipeline(BASE);
        assertThat(manifest.hasFile("smithy_error_map.erl")).isTrue();
    }

    // ── Content correctness ───────────────────────────────────────────────────

    @Test
    void handlerContainsCallbackForGetWeather() {
        MockManifest manifest = runPipeline(BASE);
        String handler = new String(manifest.expectFileBytes("src/generated/" + BASE + "_handler.erl"));
        assertThat(handler).contains("-callback get_weather(");
    }

    @Test
    void handlerContainsModuleDeclaration() {
        MockManifest manifest = runPipeline(BASE);
        String handler = new String(manifest.expectFileBytes("src/generated/" + BASE + "_handler.erl"));
        assertThat(handler).contains("-module(" + BASE + "_handler).");
    }

    @Test
    void routerContainsRouteClauseForGetWeather() {
        MockManifest manifest = runPipeline(BASE);
        String router = new String(manifest.expectFileBytes("src/generated/" + BASE + "_router.erl"));
        assertThat(router).contains("{ok, get_weather}");
    }

    @Test
    void routerContainsFallbackClause() {
        MockManifest manifest = runPipeline(BASE);
        String router = new String(manifest.expectFileBytes("src/generated/" + BASE + "_router.erl"));
        assertThat(router).contains("{error, not_found}");
    }

    @Test
    void dispatcherExportsHandle3() {
        MockManifest manifest = runPipeline(BASE);
        String dispatcher = new String(manifest.expectFileBytes("src/generated/" + BASE + "_dispatcher.erl"));
        assertThat(dispatcher).contains("handle/3");
    }

    @Test
    void dispatcherCallsSmithyServerExtract() {
        MockManifest manifest = runPipeline(BASE);
        String dispatcher = new String(manifest.expectFileBytes("src/generated/" + BASE + "_dispatcher.erl"));
        assertThat(dispatcher).contains("smithy_server:extract(");
    }

    @Test
    void dispatcherContainsDeserializeForGetWeather() {
        MockManifest manifest = runPipeline(BASE);
        String dispatcher = new String(manifest.expectFileBytes("src/generated/" + BASE + "_dispatcher.erl"));
        assertThat(dispatcher).contains("deserialize_get_weather(");
    }

    @Test
    void dispatcherContainsSerializeForGetWeather() {
        MockManifest manifest = runPipeline(BASE);
        String dispatcher = new String(manifest.expectFileBytes("src/generated/" + BASE + "_dispatcher.erl"));
        assertThat(dispatcher).contains("serialize_get_weather(");
    }

    @Test
    void implScaffoldDeclaresCorrectBehaviour() {
        MockManifest manifest = runPipeline(BASE);
        String impl = new String(manifest.expectFileBytes("src/generated/" + BASE + "_impl.erl"));
        assertThat(impl).contains("-behaviour(" + BASE + "_handler).");
    }

    @Test
    void implScaffoldContainsStubForGetWeather() {
        MockManifest manifest = runPipeline(BASE);
        String impl = new String(manifest.expectFileBytes("src/generated/" + BASE + "_impl.erl"));
        assertThat(impl).contains("get_weather(_Input, _Context)");
        assertThat(impl).contains("{error, not_implemented}");
    }

    // ── writeIfAbsent: impl scaffold is not overwritten ───────────────────────

    @Test
    void implScaffoldIsNotOverwrittenOnSecondRun() {
        ClassLoader cl = ErlangServerPipelineTest.class.getClassLoader();
        Model model = loadWeatherModel(cl);
        ServiceShape service = model.expectShape(ShapeId.from(SVC), ServiceShape.class);
        var protocol = ProtocolAnalyzerFactory.forService(service, model);
        CodegenSettings settings = CodegenSettings.builder()
                .serviceShapeId(service.getId())
                .moduleName(BASE)
                .build();

        MockManifest manifest = new MockManifest();
        var output = new FileOutput(manifest, ".erl");
        var writer = new ErlangWriter();

        // First run — scaffold is written.
        new ServerPipeline().generate(service, model, protocol, writer, settings, output, cl);
        byte[] firstBytes = manifest.expectFileBytes("src/generated/" + BASE + "_impl.erl");

        // Second run — scaffold must NOT be overwritten (MockManifest tracks existing files).
        new ServerPipeline().generate(service, model, protocol, writer, settings, output, cl);
        byte[] secondBytes = manifest.expectFileBytes("src/generated/" + BASE + "_impl.erl");

        assertThat(secondBytes).isEqualTo(firstBytes);
    }

    // ── ServerPipeline source contains no Erlang literals ─────────────────────

    @Test
    void serverPipelineShouldContainNoErlangLiterals() throws IOException {
        Path source = Path.of("../codegen-core/src/main/java/io/smithy/beam/core/pipeline/ServerPipeline.java");
        String code = Files.readString(source);
        assertThat(code)
                .doesNotContain("jsx:")
                .doesNotContain("maps:get")
                .doesNotContain("maps:find")
                .doesNotContain("httpc:")
                .doesNotContain("aws_sigv4")
                .doesNotContain("aws_retry")
                .doesNotContain("binary_to_list")
                .doesNotContain("-spec ")
                .doesNotContain("-type ")
                .doesNotContain("<<\"");
    }
}
