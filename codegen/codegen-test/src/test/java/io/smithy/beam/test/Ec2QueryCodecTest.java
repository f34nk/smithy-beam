package io.smithy.beam.test;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.net.URL;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class Ec2QueryCodecTest {

    private static final String SERVICE = "smithy.beam.test.ec2query#Ec2QueryService";

    private static Model loadModel() {
        URL resource = Ec2QueryCodecTest.class.getResource("/model/ec2_query_fixture.smithy");
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

    @Test
    void fixtureModelResolvesEc2QueryProtocol() {
        Model model = loadModel();
        ServiceShape service = model.expectShape(ShapeId.from(SERVICE), ServiceShape.class);
        assertThat(BeamProtocolResolver.resolveServiceProtocol(model, service))
                .contains(ShapeId.from("aws.protocols#ec2Query"));
        assertThat(BeamAwsServiceMetadata.from(service)).isPresent();
    }

    @Test
    void erlangClientCodecUsesEc2QueryWireMetadata() {
        MockManifest manifest = runErlangClient(loadModel());
        String codec = manifest.expectFileString(findEc2QueryErlangCodec(manifest));
        assertThat(codec).contains("encode_describe_instances_request(");
        assertThat(codec).contains("decode_describe_instances_response(");
        assertThat(codec).contains("<<\"Action\">>");
        assertThat(codec).contains("<<\"DescribeInstances\">>");
        assertThat(codec).contains("<<\"Version\">>");
        assertThat(codec).contains("<<\"2020-07-02\">>");
        assertThat(codec).contains("<<\"InstanceId\">>");
        assertThat(codec).contains("application/x-www-form-urlencoded");
        assertThat(codec).contains("unwrap_query_result(");
        assertThat(codec).contains("<<\"DescribeInstancesResponse\">>");
        assertThat(codec).contains("Key/binary, \".\", (integer_to_binary(I))/binary");
        assertThat(codec).doesNotContain(".member.");
    }

    @Test
    void elixirClientCodecUsesEc2QueryWireMetadata() {
        MockManifest manifest = runElixirClient(loadModel());
        String codec = manifest.expectFileString(findEc2QueryElixirCodec(manifest));
        assertThat(codec).contains("def encode_describe_instances_request(");
        assertThat(codec).contains("def decode_describe_instances_response(");
        assertThat(codec).contains("\"Action\"");
        assertThat(codec).contains("\"DescribeInstances\"");
        assertThat(codec).contains("\"Version\"");
        assertThat(codec).contains("\"2020-07-02\"");
        assertThat(codec).contains("\"InstanceId\"");
        assertThat(codec).contains("application/x-www-form-urlencoded");
        assertThat(codec).contains("unwrap_query_result(");
        assertThat(codec).contains("\"DescribeInstancesResponse\"");
        assertThat(codec).contains("\"#{key}.#{i}\"");
        assertThat(codec).doesNotContain(".member.");
    }

    private static String findEc2QueryErlangCodec(MockManifest manifest) {
        return manifest.getFiles().stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith("ec2_query.erl"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing ec2_query codec, files: "
                        + manifest.getFiles().stream()
                                .map(Path::getFileName)
                                .map(Path::toString)
                                .collect(Collectors.toList())));
    }

    private static String findEc2QueryElixirCodec(MockManifest manifest) {
        return manifest.getFiles().stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith("ec2_query.ex"))
                .findFirst()
                .orElseThrow();
    }
}
