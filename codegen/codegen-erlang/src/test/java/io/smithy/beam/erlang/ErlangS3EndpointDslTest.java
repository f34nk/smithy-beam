package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.erlang.Module;
import java.io.IOException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ErlangS3EndpointDslTest {
  private static final ShapeId S3_SERVICE =
      ShapeId.from("smithy.beam.test.s3restxml#S3RestXmlService");

  @Test
  void regionHostMatchesGolden() throws IOException {
    DslGoldenAssertions.assertGolden(
        ErlangS3EndpointDsl.regionHost(), "dsl/s3_endpoint_region_host.expected.erl");
  }

  @Test
  void helperFunctionsMatchGolden() throws IOException {
    String combined = DslGoldenAssertions.renderFunctions(ErlangS3EndpointDsl.helperFunctions());
    assertThat(combined)
        .isEqualTo(DslGoldenAssertions.readExpectedString("dsl/s3_endpoint_helpers.expected.erl"));
  }

  @Test
  void s3EndpointModuleMatchesGolden() throws IOException {
    ServiceShape service = s3Model().expectShape(S3_SERVICE, ServiceShape.class);
    Module module = ErlangS3EndpointDsl.s3EndpointModule(service);
    DslGoldenAssertions.assertGolden(module, "dsl/s3_endpoint_module.expected.erl");
  }

  private static Model s3Model() {
    return Model.assembler()
        .addImport(ErlangS3EndpointDslTest.class.getResource("/model/s3_rest_xml_fixture.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }
}
