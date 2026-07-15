package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamErlangLayoutTest {

  @Test
  void typesHeaderFile_usesServiceSnakeName() {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("smithy.beam.demo.basic#BasicService"))
            .version("1")
            .build();
    BeamErlangLayout layout =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service);

    assertThat(layout.typesHeaderFile()).isEqualTo("basic_service_types.hrl");
    assertThat(layout.clientModuleFile()).isEqualTo("basic_service_client.erl");
  }

  @Test
  void nameOverride_replacesDerivedStemForAllRoles() {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    settings.name("aws_lambda");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("com.amazonaws.lambda#AWSGirApiService"))
            .version("1")
            .build();
    BeamErlangLayout layout =
        new BeamErlangLayout(settings, service.getId().getNamespace(), service);

    assertThat(layout.clientModuleFile()).isEqualTo("aws_lambda_client.erl");
    assertThat(layout.typesHeaderFile()).isEqualTo("aws_lambda_types.hrl");
    assertThat(layout.clientCodecModuleName()).startsWith("aws_lambda_");
  }
}
