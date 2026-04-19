package io.smithy.beam.elixir.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ElixirReservedWordsTest {

    private static final List<String> ELIXIR_KEYWORDS = List.of(
            "after", "and", "catch", "cond", "do", "else", "end",
            "false", "fn", "for", "if", "in", "nil", "not", "or",
            "raise", "receive", "rescue", "true", "unless", "use",
            "when", "with");

    // -------------------------------------------------------------------------
    // MODULE_NAMES — PascalCase first letter + "Ex" suffix
    // -------------------------------------------------------------------------

    @Test
    void moduleNamesEscapesEveryKeywordWithPascalCaseAndExSuffix() {
        for (String keyword : ELIXIR_KEYWORDS) {
            String expectedEscaped = Character.toUpperCase(keyword.charAt(0))
                    + keyword.substring(1) + "Ex";
            assertThat(ElixirReservedWords.MODULE_NAMES.escape(keyword))
                    .as("MODULE_NAMES should escape '%s'", keyword)
                    .isEqualTo(expectedEscaped);
        }
    }

    @Test
    void nonKeywordsPassThroughModuleNames() {
        assertThat(ElixirReservedWords.MODULE_NAMES.escape("MyModule")).isEqualTo("MyModule");
        assertThat(ElixirReservedWords.MODULE_NAMES.escape("WeatherService")).isEqualTo("WeatherService");
        assertThat(ElixirReservedWords.MODULE_NAMES.escape("FooBar123")).isEqualTo("FooBar123");
    }

    @Test
    void doIsEscapedToDoExForModuleNames() {
        assertThat(ElixirReservedWords.MODULE_NAMES.escape("do")).isEqualTo("DoEx");
    }

    @Test
    void receiveIsEscapedToReceiveExForModuleNames() {
        assertThat(ElixirReservedWords.MODULE_NAMES.escape("receive")).isEqualTo("ReceiveEx");
    }

    @Test
    void endIsEscapedToEndExForModuleNames() {
        assertThat(ElixirReservedWords.MODULE_NAMES.escape("end")).isEqualTo("EndEx");
    }

    // -------------------------------------------------------------------------
    // MEMBER_NAMES — "_field" suffix
    // -------------------------------------------------------------------------

    @Test
    void memberNamesEscapesEveryKeywordWithFieldSuffix() {
        for (String keyword : ELIXIR_KEYWORDS) {
            assertThat(ElixirReservedWords.MEMBER_NAMES.escape(keyword))
                    .as("MEMBER_NAMES should escape '%s'", keyword)
                    .isEqualTo(keyword + "_field");
        }
    }

    @Test
    void nonKeywordsPassThroughMemberNames() {
        assertThat(ElixirReservedWords.MEMBER_NAMES.escape("my_field")).isEqualTo("my_field");
        assertThat(ElixirReservedWords.MEMBER_NAMES.escape("user_id")).isEqualTo("user_id");
        assertThat(ElixirReservedWords.MEMBER_NAMES.escape("count")).isEqualTo("count");
    }

    @Test
    void doIsEscapedToDoFieldForMemberNames() {
        assertThat(ElixirReservedWords.MEMBER_NAMES.escape("do")).isEqualTo("do_field");
    }

    @Test
    void receiveIsEscapedToReceiveFieldForMemberNames() {
        assertThat(ElixirReservedWords.MEMBER_NAMES.escape("receive")).isEqualTo("receive_field");
    }

    @Test
    void moduleNamesAndMemberNamesAreDistinctInstances() {
        assertThat(ElixirReservedWords.MODULE_NAMES)
                .isNotSameAs(ElixirReservedWords.MEMBER_NAMES);
    }
}
