package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlCallTest {
    @Test
    void remoteCallLines() {
        ErlCall call = new ErlCall(
                new ErlAtom("maps"), "get",
                List.of(new ErlBinary("name"), new ErlVar("Map"), new ErlAtom("undefined")));
        assertThat(call.lines()).containsExactly("maps:get(<<\"name\">>, Map, undefined)");
    }

    @Test
    void remoteCallAsString() {
        ErlCall call = new ErlCall(
                new ErlAtom("maps"), "get",
                List.of(new ErlBinary("name"), new ErlVar("Map"), new ErlAtom("undefined")));
        assertThat(call.asString()).isEqualTo("maps:get(<<\"name\">>, Map, undefined)");
    }

    @Test
    void localCallLines() {
        ErlCallLocal call = new ErlCallLocal("is_map", List.of(new ErlVar("Map")));
        assertThat(call.lines()).containsExactly("is_map(Map)");
    }

    @Test
    void localCallAsString() {
        ErlCallLocal call = new ErlCallLocal("is_map", List.of(new ErlVar("Map")));
        assertThat(call.asString()).isEqualTo("is_map(Map)");
    }
}
