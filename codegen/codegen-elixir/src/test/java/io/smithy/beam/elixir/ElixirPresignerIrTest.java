package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirPresignerIrTest {
  private static final ShapeId SIGV4_SERVICE =
      ShapeId.from("smithy.beam.test.sigv4#Sigv4TestService");

  @Test
  void presignUrlMatchesGolden() throws IOException {
    ElixirIrTestSupport.assertStructural(ElixirPresignerIr.presignUrl());
    assertThat(ElixirPresignerIr.presignUrl().asString())
        .isEqualTo(readExpectedString("ir/presigner_presign_url.expected.ex"));
  }

  @Test
  void presignerModuleMatchesGolden() throws IOException {
    ServiceShape service = sigv4Model().expectShape(SIGV4_SERVICE, ServiceShape.class);
    BeamElixirLayout layout =
        new BeamElixirLayout(new BeamSettings(), service.getId().getNamespace(), service);
    ExModule module =
        ElixirPresignerIr.presignerModule(testContext(service), service, layout.sigv4ModuleName());
    assertThat(module.asString()).isEqualTo(readExpectedString("ir/presigner_module.expected.ex"));
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
        new WriterDelegator<>(manifest, null, ElixirWriter.factory("presigner")),
        java.util.List.of(),
        service,
        BeamHttpBindings.from(model),
        null,
        null,
        "presigner",
        "presigner.ex");
  }

  private static Model sigv4Model() {
    return Model.assembler()
        .addImport(ElixirPresignerIrTest.class.getResource("/model/sigv4_fixture.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirPresignerIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
