package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErlLayoutTest {
    @Test
    void rendersBareAtom() {
        assertThat(ErlLayout.renderAtom("undefined")).isEqualTo("undefined");
    }

    @Test
    void rendersQuotedAtom() {
        assertThat(ErlLayout.renderAtom("Region")).isEqualTo("'Region'");
    }

    @Test
    void rendersBinaryLiteral() {
        assertThat(ErlLayout.renderBinaryLiteral("name")).isEqualTo("<<\"name\">>");
    }
}
