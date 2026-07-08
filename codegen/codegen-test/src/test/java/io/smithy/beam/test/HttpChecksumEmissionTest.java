package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class HttpChecksumEmissionTest {

  private static final String REST_JSON_SERVICE =
      "smithy.beam.test.checksum#HttpChecksumRestJsonService";
  private static final String REST_XML_SERVICE =
      "smithy.beam.test.checksum#HttpChecksumRestXmlService";

  private Model checksumFixtureModel() {
    URL resource =
        HttpChecksumEmissionTest.class.getResource("/model/http_checksum_fixture.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  @Test
  void erlangRestJson1EmitsRequiredAndFlexibleChecksumHeaders() {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(checksumFixtureModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", REST_JSON_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

    String codec =
        manifest.getFileString("http_checksum_rest_json_service_rest_json_1.erl").orElse("");
    assertThat(codec).doesNotContain("md5_hash(Body) ->");
    assertThat(codec).doesNotContain("validate_response_checksum(_Body");
    assertThat(codec).doesNotContain("checksum_digest(Body");
    assertThat(codec).contains("runtime_helpers:headers_set(<<\"Content-MD5\">>");
    assertThat(codec).contains("http_checksum:checksum_header_encode(");
    assertThat(codec).contains("http_checksum:md5_hash(Body)");
    assertThat(codec).contains("http_checksum:crc32c_hash(Body)");
    assertThat(codec).contains("http_checksum:sha256_hash(Body)");
    assertThat(codec).contains("http_checksum:validate_response_checksum(Body, Headers,");
    assertThat(codec).contains("http_checksum:checksum_header_encode(");
    assertThat(codec).contains("HeadersWithChecksum =");
    assertThat(codec).contains("case ChecksumAlgorithm of");
    assertThat(codec).contains("undefined ->");
    assertThat(codec).contains("Headers;");
    assertThat(codec).contains("crc32c ->");
    assertThat(codec).contains("sha256 ->");
    assertThat(codec).contains("Other ->");
    assertThat(codec).contains("error({unsupported_checksum_algorithm, Other})");

    assertThat(manifest.getFileString("runtime_helpers.erl")).isEmpty();
  }

  @Test
  void erlangRestXmlEmitsRequiredChecksumHeaders() {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(checksumFixtureModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", REST_XML_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

    String codec = manifest.getFileString("http_checksum_rest_xml_service_rest_xml.erl").orElse("");
    assertThat(codec).doesNotContain("md5_hash(Body) ->");
    assertThat(codec).doesNotContain("validate_response_checksum(_Body");
    assertThat(codec).doesNotContain("checksum_digest(Body");
    assertThat(codec).contains("runtime_helpers:headers_set(<<\"Content-MD5\">>");
    assertThat(codec).contains("http_checksum:checksum_header_encode(");
    assertThat(codec).contains("http_checksum:md5_hash(Body)");
  }

  @Test
  void elixirRestJson1EmitsChecksumHelpersAndHeaders() {
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(checksumFixtureModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", REST_JSON_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

    String codec =
        manifest.getFileString("http_checksum_rest_json_service_rest_json_1.ex").orElse("");
    assertThat(codec).contains("defp headers_set(");
    assertThat(codec).contains("Base.encode16(");
    assertThat(codec).contains("headers = headers_set(\"Content-MD5\"");
    assertThat(codec).contains(":crypto.hash(:md5, body)");
    assertThat(codec).contains("validate_response_checksum(body, headers,");
  }

  @Test
  void elixirRestXmlEmitsRequiredChecksumHeaders() {
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(checksumFixtureModel())
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", REST_XML_SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());

    String codec = manifest.getFileString("http_checksum_rest_xml_service_rest_xml.ex").orElse("");
    assertThat(codec).contains("headers = headers_set(\"Content-MD5\"");
    assertThat(codec).contains(":crypto.hash(:md5, body)");
  }
}
