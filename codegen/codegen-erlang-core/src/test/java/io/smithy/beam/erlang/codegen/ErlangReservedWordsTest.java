package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlangReservedWordsTest {

    @Test
    void moduleNames_escapesErlangKeyword() {
        assertThat(ErlangReservedWords.MODULE_NAMES.escape("receive")).isEqualTo("receive_");
    }

    @Test
    void moduleNames_escapesIfKeyword() {
        assertThat(ErlangReservedWords.MODULE_NAMES.escape("if")).isEqualTo("if_");
    }

    @Test
    void moduleNames_escapesCaseKeyword() {
        assertThat(ErlangReservedWords.MODULE_NAMES.escape("case")).isEqualTo("case_");
    }

    @Test
    void moduleNames_doesNotEscapeNonReservedWord() {
        assertThat(ErlangReservedWords.MODULE_NAMES.escape("weather_client")).isEqualTo("weather_client");
    }

    @Test
    void memberNames_escapesReceiveKeyword() {
        assertThat(ErlangReservedWords.MEMBER_NAMES.escape("receive")).isEqualTo("receive_");
    }

    @Test
    void memberNames_escapesWhenKeyword() {
        assertThat(ErlangReservedWords.MEMBER_NAMES.escape("when")).isEqualTo("when_");
    }

    @Test
    void memberNames_doesNotEscapeNormalField() {
        assertThat(ErlangReservedWords.MEMBER_NAMES.escape("city_name")).isEqualTo("city_name");
    }

    @Test
    void moduleNames_escapesTryKeyword() {
        assertThat(ErlangReservedWords.MODULE_NAMES.escape("try")).isEqualTo("try_");
    }

    @Test
    void memberNames_escapesBeginKeyword() {
        assertThat(ErlangReservedWords.MEMBER_NAMES.escape("begin")).isEqualTo("begin_");
    }

    @Test
    void memberNames_escapesXorKeyword() {
        assertThat(ErlangReservedWords.MEMBER_NAMES.escape("xor")).isEqualTo("xor_");
    }
}
