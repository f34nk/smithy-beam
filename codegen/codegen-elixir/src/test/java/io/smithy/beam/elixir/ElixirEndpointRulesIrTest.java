package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamContextParamsIndex;
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

class ElixirEndpointRulesIrTest {
  private static final ShapeId ENDPOINT_SERVICE =
      ShapeId.from("smithy.beam.test.endpoints#EndpointRulesService");

  @Test
  void resolveMatchesGolden() throws IOException {
    assertThat(ElixirEndpointRulesIr.resolve().asString())
        .isEqualTo(readExpectedString("ir/endpoint_rules_resolve.expected.ex"));
  }

  @Test
  void mergeParamsFunctionsMatchGolden() throws IOException {
    ServiceShape service = endpointModel().expectShape(ENDPOINT_SERVICE, ServiceShape.class);
    var clientContextKeys = BeamContextParamsIndex.clientContextConfigKeys(service);
    String combined =
        ElixirEndpointRulesIr.mergeParamsFunctions(clientContextKeys).stream()
            .map(ExFunction::asString)
            .collect(Collectors.joining("\n\n"));
    assertThat(combined).isEqualTo(readExpectedString("ir/endpoint_rules_merge_params.expected.ex"));
  }

  @Test
  void endpointRulesModuleMatchesGolden() throws IOException {
    ServiceShape service = endpointModel().expectShape(ENDPOINT_SERVICE, ServiceShape.class);
    ExModule module = ElixirEndpointRulesIr.endpointRulesModule(testContext(service), service);
    assertThat(module.asString())
        .isEqualTo(readExpectedString("ir/endpoint_rules_module.expected.ex"));
  }

  private static ElixirContext testContext(ServiceShape service) {
    Model model = endpointModel();
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    MockManifest manifest = new MockManifest();
    return new ElixirContext(
        model,
        settings,
        null,
        manifest,
        new WriterDelegator<>(manifest, null, ElixirWriter.factory("endpoints")),
        java.util.List.of(),
        service,
        BeamHttpBindings.from(model),
        null,
        null,
        "endpoints",
        "endpoints.ex");
  }

  private static Model endpointModel() {
    return Model.assembler()
        .addImport(
            ElixirEndpointRulesIrTest.class.getResource("/model/endpoint_rules_minimal.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirEndpointRulesIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
