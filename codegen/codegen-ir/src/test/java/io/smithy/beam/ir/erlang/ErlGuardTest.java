package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
