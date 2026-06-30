package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirS3EndpointIrTest {
  private static final ShapeId S3_SERVICE =
      ShapeId.from("smithy.beam.test.s3restxml#S3RestXmlService");

  @Test
  void regionHostMatchesGolden() throws IOException {
    assertThat(ElixirS3EndpointIr.regionHost().asString())
        .isEqualTo(readExpectedString("ir/s3_endpoint_region_host.expected.ex"));
  }

  @Test
  void helperFunctionsMatchGolden() throws IOException {
    String combined =
        ElixirS3EndpointIr.helperFunctions().stream()
            .map(ExFunction::asString)
            .collect(Collectors.joining("\n\n"));
    assertThat(combined).isEqualTo(readExpectedString("ir/s3_endpoint_helpers.expected.ex"));
  }

  @Test
  void s3EndpointModuleMatchesGolden() throws IOException {
    ServiceShape service = s3Model().expectShape(S3_SERVICE, ServiceShape.class);
    ExModule module = ElixirS3EndpointIr.s3EndpointModule(service);
    assertThat(module.asString())
        .isEqualTo(readExpectedString("ir/s3_endpoint_module.expected.ex"));
  }

  private static Model s3Model() {
    return Model.assembler()
        .addImport(ElixirS3EndpointIrTest.class.getResource("/model/s3_rest_xml_fixture.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirS3EndpointIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
