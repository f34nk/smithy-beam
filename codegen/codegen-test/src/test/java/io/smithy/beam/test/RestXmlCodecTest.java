package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import java.net.URL;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class RestXmlCodecTest {

  private static final String SERVICE = "smithy.beam.test.restxml#RestXmlService";

  private static Model loadModel() {
    URL resource = RestXmlCodecTest.class.getResource("/model/rest_xml_fixture.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static MockManifest runErlangClient(Model model) {
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder().withMember("service", SERVICE).withMember("edition", "2026").build();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());
    return manifest;
  }

  private static MockManifest runElixirClient(Model model) {
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder().withMember("service", SERVICE).withMember("edition", "2026").build();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());
    return manifest;
  }

  @Test
  void fixtureModelResolvesRestXmlProtocol() {
    Model model = loadModel();
    ServiceShape service = model.expectShape(ShapeId.from(SERVICE), ServiceShape.class);
    assertThat(BeamProtocolResolver.resolveServiceProtocol(model, service))
        .contains(ShapeId.from("aws.protocols#restXml"));
    assertThat(BeamAwsServiceMetadata.from(service)).isPresent();
  }

  @Test
  void erlangClientCodecUsesRestXmlWireMetadata() {
    MockManifest manifest = runErlangClient(loadModel());
    String codec = manifest.expectFileString(findRestXmlErlangCodec(manifest));
    assertThat(codec).contains("encode_create_bucket_request(");
    assertThat(codec).contains("decode_list_buckets_response(");
    assertThat(codec).contains("encode_xml(");
    assertThat(codec).contains("application/xml");
    assertThat(codec).contains("<<\"CreateBucketConfiguration\">>");
    assertThat(codec).contains("<<\"LocationConstraint\">>");
    assertThat(codec).contains("<<\"ListBucketsOutput\">>");
    assertThat(codec).contains("xml_namespace()");
    assertThat(codec).contains("http://restxmltest.example/doc/2020-01-01/");
    assertThat(codec).contains("find_element(<<\"Owner\">>, element_content(Parsed))");
    assertThat(codec).contains("Owner_xml ->");
  }

  @Test
  void elixirClientCodecUsesRestXmlWireMetadata() {
    MockManifest manifest = runElixirClient(loadModel());
    String codec = manifest.expectFileString(findRestXmlElixirCodec(manifest));
    assertThat(codec).contains("def encode_create_bucket_request(");
    assertThat(codec).contains("def decode_list_buckets_response(");
    assertThat(codec).contains("encode_xml(");
    assertThat(codec).contains("application/xml");
    assertThat(codec).contains("\"CreateBucketConfiguration\"");
    assertThat(codec).contains("\"LocationConstraint\"");
    assertThat(codec).contains("\"ListBucketsOutput\"");
    assertThat(codec).contains("xml_namespace");
    assertThat(codec).contains("http://restxmltest.example/doc/2020-01-01/");
  }

  @Test
  void erlangClientCodecEncodesUnionPayload() {
    MockManifest manifest = runErlangClient(loadModel());
    String codec = manifest.expectFileString(findRestXmlErlangCodec(manifest));
    assertThat(codec).contains("{location_constraint, V}");
    assertThat(codec).contains("<<\"CreateBucketConfiguration\">>");
    assertThat(codec).contains("<<\"LocationConstraint\">>");
  }

  @Test
  void elixirClientCodecEncodesUnionPayload() {
    MockManifest manifest = runElixirClient(loadModel());
    String codec = manifest.expectFileString(findRestXmlElixirCodec(manifest));
    assertThat(codec).contains("{:location_constraint, v}");
    assertThat(codec).contains("\"CreateBucketConfiguration\" => %{\"LocationConstraint\"");
  }

  private static String findRestXmlErlangCodec(MockManifest manifest) {
    return manifest.getFiles().stream()
        .map(Path::getFileName)
        .map(Path::toString)
        .filter(name -> name.endsWith("rest_xml.erl"))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "missing rest_xml codec, files: "
                        + manifest.getFiles().stream()
                            .map(Path::getFileName)
                            .map(Path::toString)
                            .collect(Collectors.toList())));
  }

  private static String findRestXmlElixirCodec(MockManifest manifest) {
    return manifest.getFiles().stream()
        .map(Path::getFileName)
        .map(Path::toString)
        .filter(name -> name.endsWith("rest_xml.ex"))
        .findFirst()
        .orElseThrow();
  }
}
