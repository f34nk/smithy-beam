package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.elixir.ElixirServerPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import io.smithy.beam.erlang.ErlangServerPlugin;
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

class AwsQueryCodecTest {

  private static final String SERVICE = "smithy.beam.test.awsquery#QueryService";

  private static Model loadModel() {
    URL resource = AwsQueryCodecTest.class.getResource("/model/aws_query_fixture.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static Model loadVoidOutputModel() {
    URL resource =
        AwsQueryCodecTest.class.getResource("/model/aws_query_void_output_fixture.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static MockManifest runErlangClient(Model model) {
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", serviceFor(model))
            .withMember("edition", "2026")
            .build();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());
    return manifest;
  }

  private static MockManifest runElixirClient(Model model) {
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", serviceFor(model))
            .withMember("edition", "2026")
            .build();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());
    return manifest;
  }

  private static MockManifest runErlangServer(Model model) {
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder().withMember("service", SERVICE).withMember("edition", "2026").build();
    new ErlangServerPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());
    return manifest;
  }

  private static MockManifest runElixirServer(Model model) {
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder().withMember("service", SERVICE).withMember("edition", "2026").build();
    new ElixirServerPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());
    return manifest;
  }

  @Test
  void fixtureModelResolvesAwsQueryProtocol() {
    Model model = loadModel();
    ServiceShape service = model.expectShape(ShapeId.from(SERVICE), ServiceShape.class);
    assertThat(BeamProtocolResolver.resolveServiceProtocol(model, service))
        .contains(ShapeId.from("aws.protocols#awsQuery"));
    assertThat(BeamAwsServiceMetadata.from(service)).isPresent();
  }

  @Test
  void erlangClientCodecUsesAwsQueryWireMetadata() {
    MockManifest manifest = runErlangClient(loadModel());
    String codec = manifest.expectFileString(findAwsQueryErlangCodec(manifest));
    assertThat(codec).contains("encode_list_users_request(");
    assertThat(codec).contains("decode_list_users_response(");
    assertThat(codec).contains("<<\"Action\">>");
    assertThat(codec).contains("<<\"ListUsers\">>");
    assertThat(codec).contains("<<\"Version\">>");
    assertThat(codec).contains("<<\"2010-05-08\">>");
    assertThat(codec).contains("application/x-www-form-urlencoded");
    assertThat(codec).contains("unwrap_query_result(");
    assertThat(codec).contains("<<\"ListUsersResult\">>");
  }

  @Test
  void elixirClientCodecUsesAwsQueryWireMetadata() {
    MockManifest manifest = runElixirClient(loadModel());
    String codec = manifest.expectFileString(findAwsQueryElixirCodec(manifest));
    assertThat(codec).contains("def encode_list_users_request(");
    assertThat(codec).contains("def decode_list_users_response(");
    assertThat(codec).contains("\"Action\"");
    assertThat(codec).contains("\"ListUsers\"");
    assertThat(codec).contains("\"Version\"");
    assertThat(codec).contains("\"2010-05-08\"");
    assertThat(codec).contains("application/x-www-form-urlencoded");
    assertThat(codec).contains("unwrap_query_result(");
    assertThat(codec).contains("\"ListUsersResult\"");
  }

  @Test
  void erlangClientCodecAcceptsMissingResultForVoidOutput() {
    MockManifest manifest = runErlangClient(loadVoidOutputModel());
    String codec = manifest.expectFileString(findAwsQueryErlangCodec(manifest));
    assertThat(codec).contains("decode_delete_user_response(");
    assertThat(codec).contains("{error, {missing_result, _}} -> {ok, #delete_user_output{}}");
  }

  @Test
  void elixirClientCodecAcceptsMissingResultForVoidOutput() {
    MockManifest manifest = runElixirClient(loadVoidOutputModel());
    String codec = manifest.expectFileString(findAwsQueryElixirCodec(manifest));
    assertThat(codec).contains("def decode_delete_user_response(");
    assertThat(codec)
        .contains("{:error, {:missing_result, _}} -> {:ok, %Types.DeleteUserOutput{}}");
  }

  @Test
  void erlangClientCodecDecodesRootLevelQueryErrors() {
    MockManifest manifest = runErlangClient(loadModel());
    String codec = manifest.expectFileString(findAwsQueryErlangCodec(manifest));
    assertThat(codec).contains("query_result_element(Root, <<\"ErrorResponse\">>)");
  }

  @Test
  void elixirClientCodecDecodesRootLevelQueryErrors() {
    MockManifest manifest = runElixirClient(loadModel());
    String codec = manifest.expectFileString(findAwsQueryElixirCodec(manifest));
    assertThat(codec).contains("query_result_element(xml, \"ErrorResponse\")");
  }

  @Test
  void erlangServerCodecEmitsDecodeAndEncodeFunctions() {
    MockManifest manifest = runErlangServer(loadModel());
    String codec = manifest.expectFileString(findAwsQueryErlangCodec(manifest));
    assertThat(codec).contains("decode_list_users_request(");
    assertThat(codec).contains("encode_list_users_response(");
    assertThat(codec).contains("parse_query_params(");
    assertThat(codec).contains("wrap_aws_query_response(");
  }

  @Test
  void elixirServerCodecEmitsDecodeAndEncodeFunctions() {
    MockManifest manifest = runElixirServer(loadModel());
    String codec = manifest.expectFileString(findAwsQueryElixirCodec(manifest));
    assertThat(codec).contains("def decode_list_users_request(");
    assertThat(codec).contains("def encode_list_users_response(");
    assertThat(codec).contains("parse_query_params(");
    assertThat(codec).contains("wrap_aws_query_response(");
  }

  private static String findAwsQueryErlangCodec(MockManifest manifest) {
    return manifest.getFiles().stream()
        .map(Path::getFileName)
        .map(Path::toString)
        .filter(name -> name.endsWith("aws_query.erl"))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "missing aws_query codec, files: "
                        + manifest.getFiles().stream()
                            .map(Path::getFileName)
                            .map(Path::toString)
                            .collect(Collectors.toList())));
  }

  private static String findAwsQueryElixirCodec(MockManifest manifest) {
    return manifest.getFiles().stream()
        .map(Path::getFileName)
        .map(Path::toString)
        .filter(name -> name.endsWith("aws_query.ex"))
        .findFirst()
        .orElseThrow();
  }

  private static String serviceFor(Model model) {
    return model.getServiceShapes().stream()
        .findFirst()
        .map(shape -> shape.getId().toString())
        .orElseThrow();
  }
}
