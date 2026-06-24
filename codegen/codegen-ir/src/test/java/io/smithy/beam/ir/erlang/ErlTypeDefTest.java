package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlTypeDefTest {
    @Test
    void lines() {
        assertThat(new ErlTypeDef("basic_item", "#basic_item{}").lines(0))
                .containsExactly("-type basic_item() :: #basic_item{}.");
    }

    @Test
    void asString() {
        assertThat(new ErlTypeDef("basic_item", "#basic_item{}").asString())
                .isEqualTo("-type basic_item() :: #basic_item{}.");
    }
}
