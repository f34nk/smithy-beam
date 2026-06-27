package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BeamNameUtilsTest {

  @Test
  void toCamelCaseVariableMapsSnakeCaseFields() {
    assertThat(BeamNameUtils.toCamelCaseVariable("name")).isEqualTo("Name");
    assertThat(BeamNameUtils.toCamelCaseVariable("client_token")).isEqualTo("ClientToken");
    assertThat(BeamNameUtils.toCamelCaseVariable("request_tag")).isEqualTo("RequestTag");
    assertThat(BeamNameUtils.toCamelCaseVariable("next_token")).isEqualTo("NextToken");
    assertThat(BeamNameUtils.toCamelCaseVariable("basic_string")).isEqualTo("BasicString");
  }

  @Test
  void toCamelCaseVariableHandlesEmptyInput() {
    assertThat(BeamNameUtils.toCamelCaseVariable("")).isEmpty();
  }
}
