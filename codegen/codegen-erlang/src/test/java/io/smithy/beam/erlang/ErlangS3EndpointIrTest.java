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
class ErlangS3EndpointIrTest {
  private static final ShapeId S3_SERVICE =
      ShapeId.from("smithy.beam.test.s3restxml#S3RestXmlService");

  @Test
  void regionHostMatchesGolden() throws IOException {
    IrGoldenAssertions.assertGolden(
        ErlangS3EndpointIr.regionHost(), "ir/s3_endpoint_region_host.expected.erl");
  }

  @Test
  void helperFunctionsMatchGolden() throws IOException {
    String combined =
        ErlangS3EndpointIr.helperFunctions().stream()
            .map(ErlangRenderer::renderFunction)
            .collect(Collectors.joining("\n\n"));
    assertThat(combined)
        .isEqualTo(IrGoldenAssertions.readExpectedString("ir/s3_endpoint_helpers.expected.erl"));
  }

  @Test
  void s3EndpointModuleMatchesGolden() throws IOException {
    ServiceShape service = s3Model().expectShape(S3_SERVICE, ServiceShape.class);
    Module module = ErlangS3EndpointIr.s3EndpointModule(service);
    IrGoldenAssertions.assertGolden(module, "ir/s3_endpoint_module.expected.erl");
  }

  private static Model s3Model() {
    return Model.assembler()
        .addImport(ErlangS3EndpointIrTest.class.getResource("/model/s3_rest_xml_fixture.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }
}
