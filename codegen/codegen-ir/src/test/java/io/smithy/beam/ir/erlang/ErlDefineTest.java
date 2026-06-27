package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlDefineTest {
    @Test
    void lines() {
        assertThat(new ErlDefine("BEAM_RUNTIME_TYPES_INCLUDED", "true").lines())
                .containsExactly("-define(BEAM_RUNTIME_TYPES_INCLUDED, true).");
    }

    @Test
    void asString() {
        assertThat(new ErlDefine("ENDPOINT_RULE_SET", "#{}").asString())
                .isEqualTo("-define(ENDPOINT_RULE_SET, #{}).");
    }
}
