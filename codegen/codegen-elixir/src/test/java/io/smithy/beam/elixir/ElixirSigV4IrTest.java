package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExFunction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ElixirSigV4IrTest {
  private static final ShapeId SIGV4_SERVICE =
      ShapeId.from("smithy.beam.test.sigv4#Sigv4TestService");

  @Test
  void signMatchesGolden() throws IOException {
    ExFunction sign = ElixirSigV4Ir.sign();
    ElixirIrTestSupport.assertStructural(sign);
    assertThat(sign.asString()).isEqualTo(readExpectedString("ir/sigv4_sign.expected.ex"));
  }

  @Test
  void presignMatchesGolden() throws IOException {
    ExFunction presign = ElixirSigV4Ir.presign();
    ElixirIrTestSupport.assertStructural(presign);
    assertThat(presign.asString()).isEqualTo(readExpectedString("ir/sigv4_presign.expected.ex"));
  }

  @Test
  void signRequestMatchesGolden() throws IOException {
    ExFunction signRequest = ElixirSigV4Ir.signRequest();
    ElixirIrTestSupport.assertStructural(signRequest);
    assertThat(signRequest.asString())
        .isEqualTo(readExpectedString("ir/sigv4_sign_request.expected.ex"));
  }

  @Test
  void helperFunctionsMatchGolden() throws IOException {
    String combined =
        ElixirSigV4Ir.helperFunctions().stream()
            .map(ExFunction::asString)
            .collect(Collectors.joining("\n\n"));
    assertThat(combined).isEqualTo(readExpectedString("ir/sigv4_helpers.expected.ex"));
    for (ExFunction fn : ElixirSigV4Ir.helperFunctions()) {
      ElixirIrTestSupport.assertStructural(fn);
    }
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
        new WriterDelegator<>(manifest, null, ElixirWriter.factory("sigv4")),
        java.util.List.of(),
        service,
        BeamHttpBindings.from(model),
        null,
        null,
        "sigv4",
        "sigv4.ex");
  }

  private static Model sigv4Model() {
    return Model.assembler()
        .addImport(ElixirSigV4IrTest.class.getResource("/model/sigv4_fixture.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirSigV4IrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
