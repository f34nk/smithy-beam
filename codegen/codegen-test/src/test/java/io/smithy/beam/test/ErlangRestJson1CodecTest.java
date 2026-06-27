package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.ErlangClientPlugin;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ErlangRestJson1CodecTest {

  private static Model loadFixture() {
    URL resource =
        ErlangRestJson1CodecTest.class.getResource("/model/protocol_rest_json_fixture.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static MockManifest runPlugin(Model model) {
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
            .withMember("edition", "2026")
            .build();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());
    return manifest;
  }

  @Test
  void getOperationEncoderBuildsPathWithLabel() {
    MockManifest manifest = runPlugin(loadFixture());
    String codec = manifest.expectFileString("demo_rest_json_rest_json_1.erl");
    assertThat(codec).contains("encode_describe_item_request(");
    assertThat(codec).contains("uri_encode(");
    assertThat(codec).contains("/items/");
  }

  @Test
  void getOperationEncoderHandlesQueryAndHeader() {
    MockManifest manifest = runPlugin(loadFixture());
    String codec = manifest.expectFileString("demo_rest_json_rest_json_1.erl");
    assertThat(codec).contains("verbose");
    assertThat(codec).contains("X-Request-Tag");
  }

  @Test
  void postOperationEncoderEmitsJsonBody() {
    MockManifest manifest = runPlugin(loadFixture());
    String codec = manifest.expectFileString("demo_rest_json_rest_json_1.erl");
    assertThat(codec).contains("encode_create_item_request(");
    assertThat(codec).contains("jsone:encode(");
    assertThat(codec).doesNotContain(",\n    }),");
  }

  @Test
  void decoderHandlesResponseHeaderExtraction() {
    MockManifest manifest = runPlugin(loadFixture());
    String codec = manifest.expectFileString("demo_rest_json_rest_json_1.erl");
    assertThat(codec).contains("decode_describe_item_response(");
    assertThat(codec).contains("ETag");
    assertThat(codec).contains("proplists:get_value(");
  }

  @Test
  void codecModuleIncludesRuntimeTypesHeader() {
    MockManifest manifest = runPlugin(loadFixture());
    String codec = manifest.expectFileString("demo_rest_json_rest_json_1.erl");
    assertThat(codec).contains("-include(\"runtime_types.hrl\").");
  }

  @Test
  void clientModuleCallsCodecAndDispatch() {
    MockManifest manifest = runPlugin(loadFixture());
    String client = manifest.expectFileString("demo_rest_json_client.erl");
    assertThat(client).contains("demo_rest_json_rest_json_1:encode_describe_item_request(");
    assertThat(client).contains("runtime_http:dispatch(");
    assertThat(client).contains("demo_rest_json_rest_json_1:decode_describe_item_response(");
  }

  @Test
  void httpDispatchModuleIsEmitted() {
    MockManifest manifest = runPlugin(loadFixture());
    String http = manifest.expectFileString("runtime_http.erl");
    assertThat(http).contains("-module(runtime_http).");
    assertThat(http).contains("dispatch_signed(HttpClient, Config, #http_request{");
    assertThat(http).contains("HttpClient:request(");
  }
}
