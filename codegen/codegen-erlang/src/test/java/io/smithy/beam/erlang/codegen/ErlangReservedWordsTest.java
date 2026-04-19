package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ErlangReservedWordsTest {

    private static final List<String> ERLANG_KEYWORDS = List.of(
            "after", "and", "andalso", "band", "begin", "bnot", "bor",
            "bsl", "bsr", "bxor", "case", "catch", "cond", "div",
            "end", "fun", "if", "let", "maybe", "not", "of", "or",
            "orelse", "receive", "rem", "try", "when", "xor");

    @Test
    void moduleNamesEscapesEveryKeywordWithTrailingUnderscore() {
        for (String keyword : ERLANG_KEYWORDS) {
            assertThat(ErlangReservedWords.MODULE_NAMES.escape(keyword))
                    .as("MODULE_NAMES should escape '%s'", keyword)
                    .isEqualTo(keyword + "_");
        }
    }

    @Test
    void memberNamesEscapesEveryKeywordWithTrailingUnderscore() {
        for (String keyword : ERLANG_KEYWORDS) {
            assertThat(ErlangReservedWords.MEMBER_NAMES.escape(keyword))
                    .as("MEMBER_NAMES should escape '%s'", keyword)
                    .isEqualTo(keyword + "_");
        }
    }

    @Test
    void nonKeywordsPassThroughModuleNames() {
        assertThat(ErlangReservedWords.MODULE_NAMES.escape("my_module")).isEqualTo("my_module");
        assertThat(ErlangReservedWords.MODULE_NAMES.escape("weather_service")).isEqualTo("weather_service");
        assertThat(ErlangReservedWords.MODULE_NAMES.escape("foo123")).isEqualTo("foo123");
    }

    @Test
    void nonKeywordsPassThroughMemberNames() {
        assertThat(ErlangReservedWords.MEMBER_NAMES.escape("my_field")).isEqualTo("my_field");
        assertThat(ErlangReservedWords.MEMBER_NAMES.escape("user_id")).isEqualTo("user_id");
        assertThat(ErlangReservedWords.MEMBER_NAMES.escape("count")).isEqualTo("count");
    }

    @Test
    void receiveIsEscapedByBothInstances() {
        assertThat(ErlangReservedWords.MODULE_NAMES.escape("receive")).isEqualTo("receive_");
        assertThat(ErlangReservedWords.MEMBER_NAMES.escape("receive")).isEqualTo("receive_");
    }

    @Test
    void endIsEscapedByBothInstances() {
        assertThat(ErlangReservedWords.MODULE_NAMES.escape("end")).isEqualTo("end_");
        assertThat(ErlangReservedWords.MEMBER_NAMES.escape("end")).isEqualTo("end_");
    }

    @Test
    void moduleNamesAndMemberNamesAreDistinctInstances() {
        assertThat(ErlangReservedWords.MODULE_NAMES)
                .isNotSameAs(ErlangReservedWords.MEMBER_NAMES);
    }
}
