package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Module;
import java.io.IOException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangSigV4IrTest {
  private static final ShapeId SIGV4_SERVICE =
      ShapeId.from("smithy.beam.test.sigv4#Sigv4TestService");

  @Test
  void signMatchesGolden() throws IOException {
    IrGoldenAssertions.assertGolden(ErlangSigV4Ir.sign(), "ir/sigv4_sign.expected.erl");
  }

  @Test
  void signRequestMatchesGolden() throws IOException {
    IrGoldenAssertions.assertGolden(ErlangSigV4Ir.signRequest(), "ir/sigv4_sign_request.expected.erl");
  }

  @Test
  void helperFunctionsMatchGolden() throws IOException {
    String combined =
        ErlangSigV4Ir.helperFunctions().stream()
            .map(ErlangRenderer::renderFunction)
            .collect(Collectors.joining("\n\n"));
    assertThat(combined).isEqualTo(IrGoldenAssertions.readExpectedString("ir/sigv4_helpers.expected.erl"));
  }

  @Test
  void sigV4ModuleMatchesGolden() throws IOException {
    ServiceShape service = sigv4Model().expectShape(SIGV4_SERVICE, ServiceShape.class);
    Module module =
        ErlangSigV4Ir.sigV4Module("sigv4test_service_sigv4", "runtime_types.hrl", service);
    IrGoldenAssertions.assertGolden(module, "ir/sigv4_module.expected.erl");
  }

  private static Model sigv4Model() {
    return Model.assembler()
        .addImport(ErlangSigV4IrTest.class.getResource("/model/sigv4_fixture.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }
}
