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

    private static final String SVC  = "example.weather#WeatherService";
    private static final String BASE = "weather_server";

    private static Model loadWeatherModel(ClassLoader cl) {
        ProtocolRegistrations.init();
        return Model.assembler(cl)
                .discoverModels(cl)
                .addImport(cl.getResource("io/smithy/beam/erlang/client/weather.smithy"))
                .assemble()
                .unwrap();
    }

    /** Runs the {@link ServerPipeline} — produces the consolidated _server.erl and _impl.erl. */
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
        var output = new FileOutput(manifest);
        var writer = new ErlangWriter();
        new ServerPipeline().generate(service, model, protocol, writer, settings, output, cl);
        return manifest;
    }

    // ── File existence ────────────────────────────────────────────────────────

    @Test
    void generatesServerFile() {
        MockManifest manifest = runPipeline(BASE);
        assertThat(manifest.hasFile("src/generated/" + BASE + "_server.erl")).isTrue();
    }

    @Test
    void doesNotGenerateHandlerFile() {
        MockManifest manifest = runPipeline(BASE);
        assertThat(manifest.hasFile("src/generated/" + BASE + "_handler.erl")).isFalse();
    }

    @Test
    void doesNotGenerateRouterFile() {
        MockManifest manifest = runPipeline(BASE);
        assertThat(manifest.hasFile("src/generated/" + BASE + "_router.erl")).isFalse();
    }

    @Test
    void doesNotGenerateDispatcherFile() {
        MockManifest manifest = runPipeline(BASE);
        assertThat(manifest.hasFile("src/generated/" + BASE + "_dispatcher.erl")).isFalse();
    }

    @Test
    void doesNotGenerateCowboyFile() {
        MockManifest manifest = runPipeline(BASE);
        assertThat(manifest.hasFile("src/generated/" + BASE + "_cowboy.erl")).isFalse();
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

    // ── Server module content ─────────────────────────────────────────────────

    @Test
    void serverModuleDeclaration() {
        MockManifest manifest = runPipeline(BASE);
        String server = new String(manifest.expectFileBytes("src/generated/" + BASE + "_server.erl"));
        assertThat(server).contains("-module(" + BASE + "_server).");
    }

    @Test
    void serverModuleDeclaresCowboyBehaviour() {
        MockManifest manifest = runPipeline(BASE);
        String server = new String(manifest.expectFileBytes("src/generated/" + BASE + "_server.erl"));
        assertThat(server).contains("-behaviour(cowboy_handler).");
    }

    @Test
    void serverModuleExportsInit2() {
        MockManifest manifest = runPipeline(BASE);
        String server = new String(manifest.expectFileBytes("src/generated/" + BASE + "_server.erl"));
        assertThat(server).contains("init/2");
    }

    @Test
    void serverModuleContainsCallbackForGetWeather() {
        MockManifest manifest = runPipeline(BASE);
        String server = new String(manifest.expectFileBytes("src/generated/" + BASE + "_server.erl"));
        assertThat(server).contains("-callback get_weather(");
    }

    @Test
    void serverModuleContainsInit2WithImplModule() {
        MockManifest manifest = runPipeline(BASE);
        String server = new String(manifest.expectFileBytes("src/generated/" + BASE + "_server.erl"));
        assertThat(server).contains("init(Req0, State)");
        assertThat(server).contains(BASE + "_impl");
    }

    @Test
    void serverModuleContainsHandle3() {
        MockManifest manifest = runPipeline(BASE);
        String server = new String(manifest.expectFileBytes("src/generated/" + BASE + "_server.erl"));
        assertThat(server).contains("handle(Impl, Req, Context)");
    }

    @Test
    void serverModuleContainsRouteClauseForGetWeather() {
        MockManifest manifest = runPipeline(BASE);
        String server = new String(manifest.expectFileBytes("src/generated/" + BASE + "_server.erl"));
        assertThat(server).contains("route(<<\"GET\">>,");
    }

    @Test
    void serverModuleContainsFallbackClause() {
        MockManifest manifest = runPipeline(BASE);
        String server = new String(manifest.expectFileBytes("src/generated/" + BASE + "_server.erl"));
        assertThat(server).contains("{error, not_found}");
    }

    @Test
    void serverModuleContainsDeserializeForGetWeather() {
        MockManifest manifest = runPipeline(BASE);
        String server = new String(manifest.expectFileBytes("src/generated/" + BASE + "_server.erl"));
        assertThat(server).contains("deserialize_get_weather(");
    }

    @Test
    void serverModuleContainsSerializeForGetWeather() {
        MockManifest manifest = runPipeline(BASE);
        String server = new String(manifest.expectFileBytes("src/generated/" + BASE + "_server.erl"));
        assertThat(server).contains("serialize_get_weather(");
    }

    // ── Impl scaffold content ─────────────────────────────────────────────────

    @Test
    void implScaffoldDeclaresCorrectBehaviour() {
        MockManifest manifest = runPipeline(BASE);
        String impl = new String(manifest.expectFileBytes("src/generated/" + BASE + "_impl.erl"));
        assertThat(impl).contains("-behaviour(" + BASE + "_server).");
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
        var output = new FileOutput(manifest);
        var writer = new ErlangWriter();

        // First run — scaffold is written.
        new ServerPipeline().generate(service, model, protocol, writer, settings, output, cl);
        byte[] firstBytes = manifest.expectFileBytes("src/generated/" + BASE + "_impl.erl");

        // Second run — scaffold must NOT be overwritten (MockManifest tracks existing files).
        new ServerPipeline().generate(service, model, protocol, writer, settings, output, cl);
        byte[] secondBytes = manifest.expectFileBytes("src/generated/" + BASE + "_impl.erl");

        assertThat(secondBytes).isEqualTo(firstBytes);
    }

    // ── ServerPipeline source contains no Erlang/framework literals ───────────

    @Test
    void serverPipelineShouldContainNoErlangLiterals() throws IOException {
        Path source = Path.of("../codegen-core/src/main/java/io/smithy/beam/core/pipeline/ServerPipeline.java");
        String code = Files.readString(source);
        assertThat(code)
                .doesNotContain("jsx:")
                .doesNotContain("maps:get")
                .doesNotContain("maps:find")
                .doesNotContain("httpc:")
                .doesNotContain("smithy_sigv4")
                .doesNotContain("smithy_retry")
                .doesNotContain("binary_to_list")
                .doesNotContain("-spec ")
                .doesNotContain("-type ")
                .doesNotContain("<<\"")
                .doesNotContain("cowboy")
                .doesNotContain("\"_handler\"")
                .doesNotContain("\"_router\"")
                .doesNotContain("\"_dispatcher\"");
    }
}
