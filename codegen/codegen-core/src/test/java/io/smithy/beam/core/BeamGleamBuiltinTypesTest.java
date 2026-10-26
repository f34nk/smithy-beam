package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BeamGleamBuiltinTypesTest {

  @Test
  void detectsBuiltinTypeNameCollisions() {
    assertThat(BeamGleamBuiltinTypes.shadowsBuiltinTypeName("String")).isTrue();
    assertThat(BeamGleamBuiltinTypes.shadowsBuiltinTypeName("Int")).isTrue();
    assertThat(BeamGleamBuiltinTypes.shadowsBuiltinTypeName("Dict")).isTrue();
    assertThat(BeamGleamBuiltinTypes.shadowsBuiltinTypeName("Option")).isTrue();
    assertThat(BeamGleamBuiltinTypes.shadowsBuiltinTypeName("BitArray")).isTrue();
  }

  @Test
  void allowsNonBuiltinAliasNames() {
    assertThat(BeamGleamBuiltinTypes.shadowsBuiltinTypeName("BasicString")).isFalse();
    assertThat(BeamGleamBuiltinTypes.shadowsBuiltinTypeName("Status")).isFalse();
  }

  @Test
  void detectsBuiltinConstructorCollisions() {
    assertThat(BeamGleamBuiltinTypes.shadowsBuiltinConstructorName("Ok")).isTrue();
    assertThat(BeamGleamBuiltinTypes.shadowsBuiltinConstructorName("None")).isTrue();
    assertThat(BeamGleamBuiltinTypes.shadowsBuiltinConstructorName("Active")).isFalse();
  }
}
