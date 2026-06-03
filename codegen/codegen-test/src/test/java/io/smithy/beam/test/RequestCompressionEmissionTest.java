package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCompressionEmissionTest {

    private static final String REST_JSON_SERVICE =
            "smithy.beam.test.compression#RequestCompressionRestJsonService";

    private Model compressionFixtureModel() {
        URL resource = RequestCompressionEmissionTest.class.getResource(
                "/model/request_compression_fixture.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void erlangRestJson1EmitsGzipCompressionWhenBodyExceedsMinimum() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(compressionFixtureModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", REST_JSON_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String codec = manifest.getFileString("request_compression_rest_json_service_rest_json_1.erl")
                .orElse("");
        assertThat(codec).contains("encode_put_compressed_request");
        assertThat(codec).contains("byte_size(Body) >= 10240");
        assertThat(codec).contains("Compressed = zlib:gzip(Body)");
        assertThat(codec).contains("headers_set(<<\"Content-Encoding\">>, <<\"gzip\">>, Headers1)");
        int plainStart = codec.indexOf("encode_get_plain_request");
        int compressedStart = codec.indexOf("encode_put_compressed_request");
        assertThat(plainStart).isGreaterThan(-1);
        assertThat(compressedStart).isGreaterThan(-1);
        String plainEncoder = codec.substring(plainStart, compressedStart);
        assertThat(plainEncoder).doesNotContain("zlib:gzip");
    }

    @Test
    void elixirRestJson1EmitsGzipCompressionWhenBodyExceedsMinimum() {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(compressionFixtureModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", REST_JSON_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String codec = manifest.getFileString("request_compression_rest_json_service_rest_json_1.ex")
                .orElse("");
        assertThat(codec).contains("def encode_put_compressed_request");
        assertThat(codec).contains(":erlang.byte_size(body) >= 10240");
        assertThat(codec).contains(":zlib.gzip(body)");
        assertThat(codec).contains("headers_set(\"Content-Encoding\", \"gzip\"");
    }
}
