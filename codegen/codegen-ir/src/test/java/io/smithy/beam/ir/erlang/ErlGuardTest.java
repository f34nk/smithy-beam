package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ErlGuardTest {
  @Test
  void lines() {
    assertThat(new ErlGuard("is_map", List.of(new ErlVar("Map"))).lines())
        .containsExactly("is_map(Map)");
  }

  @Test
  void asString() {
    assertThat(new ErlGuard("is_map", List.of(new ErlVar("Map"))).asString())
        .isEqualTo("is_map(Map)");
  }
}
