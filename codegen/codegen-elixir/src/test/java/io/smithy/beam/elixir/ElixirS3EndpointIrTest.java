package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.Module;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ElixirS3EndpointIrTest {
  private static final ShapeId S3_SERVICE =
      ShapeId.from("smithy.beam.test.s3restxml#S3RestXmlService");

  @Test
  void regionHostMatchesExpectedShape() {
    Function fn = ElixirS3EndpointIr.regionHost();
    String text = ElixirRenderer.renderFunction(fn);

    ElixirIrTestSupport.assertStructural(fn);
    assertThat(text).contains("@spec region_host(map()) :: String.t()");
    assertThat(text).contains("def region_host(config) do");
    assertThat(text).contains("RuntimeUtils.split_base_url(base_url)");
    assertThat(text).contains("{_scheme, authority}");
  }

  @Test
  void helperFunctionsMatchExpectedShape() {
    String combined =
        ElixirS3EndpointIr.helperFunctions().stream()
            .map(ElixirRenderer::renderFunction)
            .collect(Collectors.joining("\n\n"));

    for (Function fn : ElixirS3EndpointIr.helperFunctions()) {
      ElixirIrTestSupport.assertStructural(fn);
    }
    assertThat(combined).contains("defp key_path(\"\")");
    assertThat(combined).contains("defp key_path(key)");
    assertThat(combined).contains("s3_use_accelerate");
    assertThat(combined).contains("s3-accelerate.amazonaws.com");
    assertThat(combined).contains("s3_host_suffix");
    assertThat(combined).contains("s3_use_dualstack");
  }

  @Test
  void s3EndpointModuleMatchesExpectedShape() {
    ServiceShape service = s3Model().expectShape(S3_SERVICE, ServiceShape.class);
    Module module = ElixirS3EndpointIr.s3EndpointModule(service);
    String text = ElixirRenderer.render(module);

    assertThat(text).contains("defmodule S3Endpoint do");
    assertThat(text).contains("@moduledoc false");
    assertThat(text).contains("def resolve_bucket_url(config, bucket, key) do");
    assertThat(text).contains(":virtual_host");
    assertThat(text).contains(":path_style");
    assertThat(text).contains("defp virtual_host(config, bucket, region_host) do");
  }

  private static Model s3Model() {
    return Model.assembler()
        .addImport(ElixirS3EndpointIrTest.class.getResource("/model/s3_rest_xml_fixture.smithy"))
        .discoverModels()
        .assemble()
        .unwrap();
  }
}
