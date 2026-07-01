package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BeamElixirBuiltinTypesTest {

  @Test
  void detectsBuiltinTypeNameCollisions() {
    assertThat(BeamElixirBuiltinTypes.shadowsBuiltinTypeName("string")).isTrue();
    assertThat(BeamElixirBuiltinTypes.shadowsBuiltinTypeName("string()")).isTrue();
    assertThat(BeamElixirBuiltinTypes.shadowsBuiltinTypeName("atom")).isTrue();
  }

  @Test
  void allowsNonBuiltinAliasNames() {
    assertThat(BeamElixirBuiltinTypes.shadowsBuiltinTypeName("snapshot_id")).isFalse();
    assertThat(BeamElixirBuiltinTypes.shadowsBuiltinTypeName("basic_string")).isFalse();
  }
}
