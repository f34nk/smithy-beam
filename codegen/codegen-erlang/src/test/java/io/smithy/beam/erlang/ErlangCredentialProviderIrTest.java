package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangCredentialProviderIrTest {
  private static final ShapeId SIGV4_SERVICE =
      ShapeId.from("smithy.beam.test.sigv4#Sigv4TestService");

  @Test
  void resolveMatchesGolden() throws IOException {
    assertThat(ErlangCredentialProviderIr.resolve().asString())
        .isEqualTo(readExpectedString("ir/credential_provider_resolve.expected.erl"));
  }

  @Test
  void resolveChainMatchesGolden() throws IOException {
    String combined =
        ErlangCredentialProviderIr.credentialFunctions().stream()
            .filter(
                fn -> fn.name().startsWith("resolve_chain") || fn.name().equals("resolve_provider"))
            .map(ErlFunction::asString)
            .collect(Collectors.joining("\n\n"));
    assertThat(combined).isEqualTo(readExpectedString("ir/credential_provider_chain.expected.erl"));
  }

  @Test
  void credentialsModuleMatchesGolden() throws IOException {
    ServiceShape service = sigv4Model().expectShape(SIGV4_SERVICE, ServiceShape.class);
    ErlModule module =
        ErlangCredentialProviderIr.credentialsModule("sigv4test_service_credentials", service);
    assertThat(module.asString())
        .isEqualTo(readExpectedString("ir/credential_provider_module.expected.erl"));
  }

  private static Model sigv4Model() {
    return Model.assembler()
        .addImport(ErlangCredentialProviderIrTest.class.getResource("/model/sigv4_fixture.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangCredentialProviderIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
