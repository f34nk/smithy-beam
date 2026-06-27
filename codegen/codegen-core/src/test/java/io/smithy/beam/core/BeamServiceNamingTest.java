package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamServiceNamingTest {

  @Test
  void effectiveServiceSnakeName_usesNameOverride_whenSet() {
    BeamSettings settings = new BeamSettings();
    settings.name("aws_lambda");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("com.amazonaws.lambda#AWSGirApiService"))
            .version("1")
            .build();

    assertThat(BeamServiceNaming.effectiveServiceSnakeName(settings, service))
        .isEqualTo("aws_lambda");
  }

  @Test
  void effectiveServiceSnakeName_derivesFromService_whenNameUnset() {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("smithy.beam.demo.basic#BasicService"))
            .version("1")
            .build();

    assertThat(BeamServiceNaming.effectiveServiceSnakeName(settings, service))
        .isEqualTo("basic_service");
  }

  @Test
  void effectiveServiceSnakeName_honorsSelfRename_whenNameUnset() {
    BeamSettings settings = new BeamSettings();
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("smithy.beam.test#OriginalName"))
            .version("1")
            .putRename(ShapeId.from("smithy.beam.test#OriginalName"), "RenamedService")
            .build();

    assertThat(BeamServiceNaming.effectiveServiceSnakeName(settings, service))
        .isEqualTo("renamed_service");
  }

  @Test
  void effectiveServiceSnakeName_nameOverrideWinsOverSelfRename() {
    BeamSettings settings = new BeamSettings();
    settings.name("custom_stem");
    ServiceShape service =
        ServiceShape.builder()
            .id(ShapeId.from("smithy.beam.test#OriginalName"))
            .version("1")
            .putRename(ShapeId.from("smithy.beam.test#OriginalName"), "RenamedService")
            .build();

    assertThat(BeamServiceNaming.effectiveServiceSnakeName(settings, service))
        .isEqualTo("custom_stem");
  }

  @Test
  void validateSnakeStem_rejectsUppercase() {
    assertThatThrownBy(() -> BeamServiceNaming.validateSnakeStem("AwsLambda"))
        .isInstanceOf(CodegenException.class)
        .hasMessageContaining("name");
  }

  @Test
  void validateSnakeStem_rejectsRoleSuffixStyleValue() {
    BeamServiceNaming.validateSnakeStem("aws_lambda_client");
  }

  @Test
  void validateSnakeStem_acceptsSimpleStem() {
    BeamServiceNaming.validateSnakeStem("lambda");
  }
}
