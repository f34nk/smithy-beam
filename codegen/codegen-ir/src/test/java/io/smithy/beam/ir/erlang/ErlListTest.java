package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlListTest {
    @Test
    void listLiteralLines() {
        ErlList list = ErlList.list(ErlAtom.atom("a"), ErlAtom.atom("b"), ErlAtom.atom("c"));
        assertThat(list.lines()).containsExactly("[a, b, c]");
    }

    @Test
    void listLiteralAsString() {
        ErlList list = ErlList.list(ErlAtom.atom("a"), ErlAtom.atom("b"), ErlAtom.atom("c"));
        assertThat(list.asString()).isEqualTo("[a, b, c]");
    }

    @Test
    void consListLines() {
        ErlList list = ErlList.cons(new ErlVar("H"), new ErlVar("T"));
        assertThat(list.lines()).containsExactly("[H | T]");
    }

    @Test
    void consListAsString() {
        ErlList list = ErlList.cons(new ErlVar("H"), new ErlVar("T"));
        assertThat(list.asString()).isEqualTo("[H | T]");
    }
}
