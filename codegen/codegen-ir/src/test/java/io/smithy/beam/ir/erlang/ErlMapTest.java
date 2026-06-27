package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlMapTest {
    @Test
    void mapLiteralLines() {
        ErlMap map = ErlMap.map(ErlMapEntry.entry(new ErlBinary("k"), new ErlVar("V")));
        assertThat(map.lines()).containsExactly("#{<<\"k\">> => V}");
    }

    @Test
    void mapLiteralAsString() {
        ErlMap map = ErlMap.map(ErlMapEntry.entry(new ErlBinary("k"), new ErlVar("V")));
        assertThat(map.asString()).isEqualTo("#{<<\"k\">> => V}");
    }
}
