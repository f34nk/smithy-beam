package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Module;
import io.smithy.beam.core.BeamContextParamsIndex;
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
class ErlangEndpointRulesIrTest {
  private static final ShapeId ENDPOINT_SERVICE =
      ShapeId.from("smithy.beam.test.endpoints#EndpointRulesService");

  @Test
  void resolveMatchesGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangEndpointRulesIr.resolve(), "ir/endpoint_rules_resolve.expected.erl");
  }

  @Test
  void mergeParamsFunctionsMatchGolden() throws IOException {
    ServiceShape service = endpointModel().expectShape(ENDPOINT_SERVICE, ServiceShape.class);
    var clientContextKeys = BeamContextParamsIndex.clientContextConfigKeys(service);
    String combined = IrGoldenAssertions.renderFunctions(ErlangEndpointRulesIr.mergeParamsFunctions(clientContextKeys));
    assertThat(combined)
        .isEqualTo(IrGoldenAssertions.readExpectedString("ir/endpoint_rules_merge_params.expected.erl"));
  }

  @Test
  void endpointRulesModuleMatchesGolden() throws IOException {
    ServiceShape service = endpointModel().expectShape(ENDPOINT_SERVICE, ServiceShape.class);
    var clientContextKeys = BeamContextParamsIndex.clientContextConfigKeys(service);
    Module module =
        ErlangEndpointRulesIr.endpointRulesModule(
            "endpoint_rules_service_endpoints", "runtime_types.hrl", service, clientContextKeys);
    IrGoldenAssertions.assertGolden(module, "ir/endpoint_rules_module.expected.erl");
  }

  private static Model endpointModel() {
    return Model.assembler()
        .addImport(
            ErlangEndpointRulesIrTest.class.getResource("/model/endpoint_rules_minimal.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangEndpointRulesIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
