package io.smithy.beam.test;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import io.smithy.beam.erlang.ErlangServerPlugin;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AwsJson11CodecTest {

    private static final String SERVICE = "smithy.beam.test.awsjson11#Json11Service";

    private static Model loadModel() {
        URL resource = AwsJson11CodecTest.class.getResource("/model/aws_json_1_1_fixture.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static MockManifest runErlangClient(Model model) {
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", SERVICE)
                .withMember("edition", "2026")
                .build();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());
        return manifest;
    }

    private static MockManifest runElixirClient(Model model) {
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", SERVICE)
                .withMember("edition", "2026")
                .build();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());
        return manifest;
    }

    private static MockManifest runErlangServer(Model model) {
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", SERVICE)
                .withMember("edition", "2026")
                .build();
        new ErlangServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());
        return manifest;
    }

    @Test
    void fixtureModelResolvesAwsJson11Protocol() {
        Model model = loadModel();
        ServiceShape service = model.expectShape(ShapeId.from(SERVICE), ServiceShape.class);
        assertThat(BeamProtocolResolver.resolveServiceProtocol(model, service))
                .contains(ShapeId.from("aws.protocols#awsJson1_1"));
        assertThat(BeamAwsServiceMetadata.from(service)).isPresent();
    }

    @Test
    void erlangClientCodecUsesAwsJson11WireMetadata() {
        MockManifest manifest = runErlangClient(loadModel());
        String codec = manifest.expectFileString(findAwsJson11ErlangCodec(manifest));
        assertThat(codec).contains("encode_get_user_request(");
        assertThat(codec).contains("X-Amz-Target");
        assertThat(codec).contains("application/x-amz-json-1.1");
        assertThat(codec).contains("Json11Service.GetUser");
    }

    @Test
    void elixirClientCodecUsesAwsJson11WireMetadata() {
        MockManifest manifest = runElixirClient(loadModel());
        String codec = manifest.expectFileString(findAwsJson11ElixirCodec(manifest));
        assertThat(codec).contains("def encode_get_user_request(");
        assertThat(codec).contains("X-Amz-Target");
        assertThat(codec).contains("application/x-amz-json-1.1");
        assertThat(codec).contains("Json11Service.GetUser");
    }

    @Test
    void erlangServerRouterDispatchesByAmzTarget() {
        MockManifest manifest = runErlangServer(loadModel());
        String router = manifest.expectFileString(findRouterErl(manifest));
        assertThat(router).contains("X-Amz-Target");
        assertThat(router).contains("Json11Service.GetUser");
        assertThat(router).contains("route(<<\"POST\">>, <<\"/\">>");
    }

    private static String findAwsJson11ErlangCodec(MockManifest manifest) {
        return manifest.getFiles().stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith("aws_json_1_1.erl"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing aws_json_1_1 codec, files: "
                        + manifest.getFiles().stream()
                                .map(Path::getFileName)
                                .map(Path::toString)
                                .collect(Collectors.toList())));
    }

    private static String findAwsJson11ElixirCodec(MockManifest manifest) {
        return manifest.getFiles().stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith("aws_json_1_1.ex"))
                .findFirst()
                .orElseThrow();
    }

    private static String findRouterErl(MockManifest manifest) {
        return manifest.getFiles().stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith("_router.erl"))
                .findFirst()
                .orElseThrow();
    }
}
