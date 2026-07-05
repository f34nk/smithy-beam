package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.Module;
import java.io.IOException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangPresignerIrTest {
  private static final ShapeId SIGV4_SERVICE =
      ShapeId.from("smithy.beam.test.sigv4#Sigv4TestService");

  @Test
  void presignUrlMatchesGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangPresignerIr.presignUrl("sigv4test_service_sigv4"),
        "ir/presigner_presign_url.expected.erl");
  }

  @Test
  void presignerModuleMatchesGolden() throws IOException {
    ServiceShape service = sigv4Model().expectShape(SIGV4_SERVICE, ServiceShape.class);
    Module module =
        ErlangPresignerIr.presignerModule(
            "sigv4test_service_presigner", "runtime_types.hrl", "sigv4test_service_sigv4", service);
    IrGoldenAssertions.assertGolden(module, "ir/presigner_module.expected.erl");
  }

  private static Model sigv4Model() {
    return Model.assembler()
        .addImport(ErlangPresignerIrTest.class.getResource("/model/sigv4_fixture.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }
}
