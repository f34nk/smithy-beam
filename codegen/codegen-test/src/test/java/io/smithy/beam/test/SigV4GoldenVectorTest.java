package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

class SigV4GoldenVectorTest {

  @ParameterizedTest
  @ValueSource(strings = {"get-vanilla.json", "post-unsigned-payload.json"})
  void goldenVectorMatchesExpectedSignature(String resourceName) throws IOException {
    ObjectNode vector = loadVector(resourceName);
    ObjectNode expected = vector.expectObjectMember("expected");

    AwsSigV4TestSigner.SignResult result = AwsSigV4TestSigner.sign(vector);

    assertThat(result.canonicalRequest())
        .isEqualTo(expected.expectStringMember("canonicalRequest").getValue());
    assertThat(result.stringToSign())
        .isEqualTo(expected.expectStringMember("stringToSign").getValue());
    assertThat(result.signature()).isEqualTo(expected.expectStringMember("signature").getValue());

    if (vector.getBooleanMemberOrDefault("unsignedPayload", false)) {
      assertThat(result.signedHeaders().get("x-amz-content-sha256")).isEqualTo("UNSIGNED-PAYLOAD");
    }
  }

  @Test
  void getVanillaAuthorizationHeaderUsesComputedSignature() throws IOException {
    ObjectNode vector = loadVector("get-vanilla.json");
    AwsSigV4TestSigner.SignResult result = AwsSigV4TestSigner.sign(vector);

    assertThat(result.signedHeaders().get("authorization"))
        .contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date")
        .contains(
            "Signature="
                + vector.expectObjectMember("expected").expectStringMember("signature").getValue());
  }

  private static ObjectNode loadVector(String resourceName) throws IOException {
    String path = "/sigv4/" + resourceName;
    try (InputStream in = SigV4GoldenVectorTest.class.getResourceAsStream(path)) {
      assertThat(in).as("missing resource " + path).isNotNull();
      String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      return Node.parse(json).expectObjectNode();
    }
  }
}
