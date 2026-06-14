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

class HttpChecksumEmissionTest {

    private static final String REST_JSON_SERVICE =
            "smithy.beam.test.checksum#HttpChecksumRestJsonService";
    private static final String REST_XML_SERVICE =
            "smithy.beam.test.checksum#HttpChecksumRestXmlService";

    private Model checksumFixtureModel() {
        URL resource = HttpChecksumEmissionTest.class.getResource("/model/http_checksum_fixture.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void erlangRestJson1EmitsRequiredAndFlexibleChecksumHeaders() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(checksumFixtureModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", REST_JSON_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String codec = manifest.getFileString("http_checksum_rest_json_service_rest_json_1.erl").orElse("");
        assertThat(codec).contains("headers_set(<<\"Content-MD5\">>, checksum_header_encode(");
        assertThat(codec).contains("crypto:hash(md5, Body)");
        assertThat(codec).contains("crc32c_hash(Body)");
        assertThat(codec).contains("sha256_hash(Body)");
        assertThat(codec).contains("validate_response_checksum(Body, Headers,");
        assertThat(codec).contains("case Computed =:= Expected of");
        assertThat(codec).containsPattern("end\\s+end\\.");
        assertThat(codec).contains("HeadersWithChecksum = case ChecksumAlgorithm of");
        assertThat(codec).contains("undefined -> Headers;");
        assertThat(codec).contains("crc32c ->");
        assertThat(codec).contains("sha256 ->");
        assertThat(codec).contains("Other -> error({unsupported_checksum_algorithm, Other})");
    }

    @Test
    void erlangRestXmlEmitsRequiredChecksumHeaders() {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(checksumFixtureModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", REST_XML_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String codec = manifest.getFileString("http_checksum_rest_xml_service_rest_xml.erl").orElse("");
        assertThat(codec).contains("headers_set(<<\"Content-MD5\">>, checksum_header_encode(");
        assertThat(codec).contains("crypto:hash(md5, Body)");
    }

    @Test
    void elixirRestJson1EmitsChecksumHelpersAndHeaders() {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(checksumFixtureModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", REST_JSON_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String codec = manifest.getFileString("http_checksum_rest_json_service_rest_json_1.ex").orElse("");
        assertThat(codec).contains("defp headers_set(");
        assertThat(codec).contains("Base.encode16(");
        assertThat(codec).contains("headers = headers_set(\"Content-MD5\"");
        assertThat(codec).contains(":crypto.hash(:md5, body)");
        assertThat(codec).contains("validate_response_checksum(body, headers,");
    }

    @Test
    void elixirRestXmlEmitsRequiredChecksumHeaders() {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(checksumFixtureModel())
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", REST_XML_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

        String codec = manifest.getFileString("http_checksum_rest_xml_service_rest_xml.ex").orElse("");
        assertThat(codec).contains("headers = headers_set(\"Content-MD5\"");
        assertThat(codec).contains(":crypto.hash(:md5, body)");
    }
}
