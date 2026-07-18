package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BeamNameUtilsTest {

  @Test
  void toSnakeCaseConvertsCamelCase() {
    assertThat(BeamNameUtils.toSnakeCase("fooBar")).isEqualTo("foo_bar");
    assertThat(BeamNameUtils.toSnakeCase("FooBar")).isEqualTo("foo_bar");
    assertThat(BeamNameUtils.toSnakeCase("HTTPRequest")).isEqualTo("http_request");
    assertThat(BeamNameUtils.toSnakeCase("foo_bar")).isEqualTo("foo_bar");
    assertThat(BeamNameUtils.toSnakeCase("Foo")).isEqualTo("foo");
  }

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
