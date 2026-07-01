package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamElixirLayoutTest {

  @Test
  void typesModuleFile_usesServiceSnakeNameAtCodegenRoot() {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("smithy.beam.demo.basic#BasicService"))
            .version("1")
            .build();
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);

    assertThat(layout.typesModuleFile()).isEqualTo("basic_service_types.ex");
    assertThat(layout.clientModuleFile()).isEqualTo("basic_service_client.ex");
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
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);

    assertThat(layout.clientModuleFile()).isEqualTo("aws_lambda_client.ex");
    assertThat(layout.typesModuleFile()).isEqualTo("aws_lambda_types.ex");
    assertThat(layout.sigv4ModuleFile()).isEqualTo("aws_lambda_sigv4.ex");
  }

  @Test
  void nestedTypesDirectory_defaultsToTypes() {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("com.example#Ec2"))
            .version("1")
            .build();
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);

    assertThat(layout.nestedTypesDirectory()).isEqualTo("types");
  }

  @Test
  void nestedTypeModuleFile_usesSnakeCaseUnderTypesDirectory() {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    settings.name("ec2");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("com.example#Ec2"))
            .version("1")
            .build();
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);

    assertThat(layout.nestedTypeModuleFile("Instance")).isEqualTo("types/instance.ex");
    assertThat(layout.nestedTypeModuleFile("RunInstancesRequest"))
        .isEqualTo("types/run_instances_request.ex");
  }
}
