package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirCredentialProviderIrTest {
  private static final ShapeId SIGV4_SERVICE =
      ShapeId.from("smithy.beam.test.sigv4#Sigv4TestService");

  @Test
  void resolveMatchesGolden() throws IOException {
    assertThat(ElixirCredentialProviderIr.resolve().asString())
        .isEqualTo(readExpectedString("ir/credential_provider_resolve.expected.ex"));
  }

  @Test
  void resolveChainMatchesGolden() throws IOException {
    String combined =
        ElixirCredentialProviderIr.credentialFunctions().stream()
            .filter(
                fn ->
                    fn.name().startsWith("resolve_chain") || fn.name().equals("resolve_provider"))
            .map(ExFunction::asString)
            .collect(Collectors.joining("\n\n"));
    assertThat(combined).isEqualTo(readExpectedString("ir/credential_provider_chain.expected.ex"));
  }

  @Test
  void credentialsModuleMatchesGolden() throws IOException {
    ServiceShape service = sigv4Model().expectShape(SIGV4_SERVICE, ServiceShape.class);
    ExModule module = ElixirCredentialProviderIr.credentialsModule(testContext(service), service);
    assertThat(module.asString())
        .isEqualTo(readExpectedString("ir/credential_provider_module.expected.ex"));
  }

  private static ElixirContext testContext(ServiceShape service) {
    Model model = sigv4Model();
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    MockManifest manifest = new MockManifest();
    return new ElixirContext(
        model,
        settings,
        null,
        manifest,
        new WriterDelegator<>(manifest, null, ElixirWriter.factory("credentials")),
        java.util.List.of(),
        service,
        BeamHttpBindings.from(model),
        null,
        null,
        "credentials",
        "credentials.ex");
  }

  private static Model sigv4Model() {
    return Model.assembler()
        .addImport(ElixirCredentialProviderIrTest.class.getResource("/model/sigv4_fixture.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirCredentialProviderIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
