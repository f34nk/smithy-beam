package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void unionTypeMultilineAsString() {
        String out = ErlTypeDef.unionType(
                        "basic_union()",
                        List.of(
                                "{text, basic_string()}",
                                "{number, basic_integer()}",
                                "{flag, basic_boolean()}",
                                "{unknown, binary()}"))
                .asString();

        assertThat(out).contains("-type basic_union() ::");
        assertThat(out).contains("    {text, basic_string()}");
        assertThat(out).contains("    | {unknown, binary()}.");
        assertThat(out).doesNotContain("{text, basic_string()}  |");
    }
}
