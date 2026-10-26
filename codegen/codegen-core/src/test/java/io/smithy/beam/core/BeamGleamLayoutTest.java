package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamGleamLayoutTest {

  @Test
  void typesModuleFile_usesServiceSnakeNameAtCodegenRoot() {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("smithy.beam.demo.basic#BasicService"))
            .version("1")
            .build();
    BeamGleamLayout layout = new BeamGleamLayout(settings, service.getId().getNamespace(), service);

    assertThat(layout.typesModuleFile()).isEqualTo("basic_service_types.gleam");
    assertThat(layout.typesModuleName()).isEqualTo("basic_service_types");
    assertThat(layout.clientModuleFile()).isEqualTo("basic_service_client.gleam");
    assertThat(layout.serverModuleFile()).isEqualTo("basic_service_server.gleam");
    assertThat(layout.behaviourModuleFile()).isEqualTo("basic_service_behaviour.gleam");
    assertThat(layout.implModuleFile()).isEqualTo("basic_service_impl.gleam");
    assertThat(layout.routerModuleFile()).isEqualTo("basic_service_router.gleam");
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
    BeamGleamLayout layout = new BeamGleamLayout(settings, service.getId().getNamespace(), service);

    assertThat(layout.clientModuleFile()).isEqualTo("aws_lambda_client.gleam");
    assertThat(layout.typesModuleFile()).isEqualTo("aws_lambda_types.gleam");
    assertThat(layout.waitersModuleName()).startsWith("aws_lambda_");
    assertThat(layout.behaviourModuleName()).isEqualTo("aws_lambda_behaviour");
    assertThat(layout.routerModuleName()).isEqualTo("aws_lambda_router");
  }

  @Test
  void codecModuleName_usesProtocolSuffixHelper() {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("smithy.beam.demo.basic#BasicService"))
            .version("1")
            .build();
    BeamGleamLayout layout = new BeamGleamLayout(settings, service.getId().getNamespace(), service);
    ShapeId restJson1 = ShapeId.from("aws.protocols#restJson1");

    assertThat(layout.codecModuleName(restJson1)).isEqualTo("basic_service_rest_json_1");
    assertThat(layout.codecModuleFile(restJson1)).isEqualTo("basic_service_rest_json_1.gleam");
  }
}
