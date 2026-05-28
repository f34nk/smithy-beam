package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangRestJson1CodecTest {

    private static Model loadFixture() {
        URL resource = ErlangRestJson1CodecTest.class
                .getResource("/model/protocol_rest_json_fixture.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static MockManifest runPlugin(Model model) {
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
                .withMember("edition", "2026")
                .withMember("protocol", "aws.protocols#restJson1")
                .build();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());
        return manifest;
    }

    @Test
    void getOperationEncoderBuildsPathWithLabel() {
        MockManifest manifest = runPlugin(loadFixture());
        String codec = manifest.expectFileString("protocoljson_rest_json_1.erl");
        assertThat(codec).contains("encode_describe_item_request(");
        assertThat(codec).contains("uri_encode(");
        assertThat(codec).contains("/items/");
    }

    @Test
    void getOperationEncoderHandlesQueryAndHeader() {
        MockManifest manifest = runPlugin(loadFixture());
        String codec = manifest.expectFileString("protocoljson_rest_json_1.erl");
        assertThat(codec).contains("verbose");
        assertThat(codec).contains("X-Request-Tag");
    }

    @Test
    void postOperationEncoderEmitsJsonBody() {
        MockManifest manifest = runPlugin(loadFixture());
        String codec = manifest.expectFileString("protocoljson_rest_json_1.erl");
        assertThat(codec).contains("encode_create_item_request(");
        assertThat(codec).contains("jsone:encode(");
        assertThat(codec).doesNotContain(",\n    }),");
    }

    @Test
    void decoderHandlesResponseHeaderExtraction() {
        MockManifest manifest = runPlugin(loadFixture());
        String codec = manifest.expectFileString("protocoljson_rest_json_1.erl");
        assertThat(codec).contains("decode_describe_item_response(");
        assertThat(codec).contains("ETag");
        assertThat(codec).contains("proplists:get_value(");
    }

    @Test
    void codecModuleIncludesRuntimeTypesHeader() {
        MockManifest manifest = runPlugin(loadFixture());
        String codec = manifest.expectFileString("protocoljson_rest_json_1.erl");
        assertThat(codec).contains("-include(\"protocoljson_runtime_types.hrl\").");
    }

    @Test
    void clientModuleCallsCodecAndDispatch() {
        MockManifest manifest = runPlugin(loadFixture());
        String client = manifest.expectFileString("protocoljson_client.erl");
        assertThat(client).contains("protocoljson_rest_json_1:encode_describe_item_request(");
        assertThat(client).contains("protocoljson_http:dispatch(");
        assertThat(client).contains("protocoljson_rest_json_1:decode_describe_item_response(");
    }

    @Test
    void httpDispatchModuleIsEmitted() {
        MockManifest manifest = runPlugin(loadFixture());
        String http = manifest.expectFileString("protocoljson_http.erl");
        assertThat(http).contains("-module(protocoljson_http).");
        assertThat(http).contains("dispatch(HttpClient, Config, #http_request{");
        assertThat(http).contains("HttpClient:request(");
    }
}
