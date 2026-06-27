package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BeamScalarTypeAliasesTest {

  @Test
  void detectsRedundantErlangAlias() {
    assertThat(BeamScalarTypeAliases.isRedundant("float()", "float()")).isTrue();
    assertThat(BeamScalarTypeAliases.isRedundant("float", "float()")).isTrue();
  }

  @Test
  void rejectsDistinctAliasNames() {
    assertThat(BeamScalarTypeAliases.isRedundant("basic_float()", "float()")).isFalse();
    assertThat(BeamScalarTypeAliases.isRedundant("string()", "binary()")).isFalse();
    assertThat(BeamScalarTypeAliases.isRedundant("long()", "integer()")).isFalse();
    assertThat(BeamScalarTypeAliases.isRedundant("double()", "float()")).isFalse();
  }
}
