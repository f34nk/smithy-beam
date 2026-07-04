package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamContextParamsIndex;
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
class ErlangEndpointRulesIrTest {
  private static final ShapeId ENDPOINT_SERVICE =
      ShapeId.from("smithy.beam.test.endpoints#EndpointRulesService");

  @Test
  void resolveMatchesGolden() throws IOException {
    assertThat(ErlangEndpointRulesIr.resolve().asString())
        .isEqualTo(readExpectedString("ir/endpoint_rules_resolve.expected.erl"));
  }

  @Test
  void mergeParamsFunctionsMatchGolden() throws IOException {
    ServiceShape service = endpointModel().expectShape(ENDPOINT_SERVICE, ServiceShape.class);
    var clientContextKeys = BeamContextParamsIndex.clientContextConfigKeys(service);
    String combined =
        ErlangEndpointRulesIr.mergeParamsFunctions(clientContextKeys).stream()
            .map(ErlFunction::asString)
            .collect(Collectors.joining("\n\n"));
    assertThat(combined)
        .isEqualTo(readExpectedString("ir/endpoint_rules_merge_params.expected.erl"));
  }

  @Test
  void endpointRulesModuleMatchesGolden() throws IOException {
    ServiceShape service = endpointModel().expectShape(ENDPOINT_SERVICE, ServiceShape.class);
    var clientContextKeys = BeamContextParamsIndex.clientContextConfigKeys(service);
    ErlModule module =
        ErlangEndpointRulesIr.endpointRulesModule(
            "endpoint_rules_service_endpoints", "runtime_types.hrl", service, clientContextKeys);
    assertThat(module.asString())
        .isEqualTo(readExpectedString("ir/endpoint_rules_module.expected.erl"));
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
