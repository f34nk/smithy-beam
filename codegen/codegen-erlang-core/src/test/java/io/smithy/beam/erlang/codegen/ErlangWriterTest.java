package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.Symbol;

class ErlangWriterTest {

    private ErlangWriter writer() {
        return new ErlangWriter("test.erl");
    }

    // ------------------------------------------------------------------
    // $T formatter
    // ------------------------------------------------------------------

    @Test
    void formatType_atomKind_returnsQuotedWhenNeeded() {
        ErlangWriter w = writer();
        Symbol atom = ErlangSymbol.atom("ok");
        w.write("$T", atom);
        assertThat(w.toString()).contains("ok");
    }

    @Test
    void formatType_builtinKind_returnsVerbatimName() {
        ErlangWriter w = writer();
        Symbol builtin = ErlangSymbol.builtin("binary()");
        w.write("$T", builtin);
        assertThat(w.toString()).contains("binary()");
    }

    @Test
    void formatType_moduleRefKind_returnsModuleColonFun() {
        ErlangWriter w = writer();
        Symbol ref = ErlangSymbol.moduleRef("smithy_json", "encode");
        w.write("$T", ref);
        assertThat(w.toString()).contains("smithy_json:encode");
    }

    @Test
    void formatType_nonSymbol_throwsIllegalArgument() {
        ErlangWriter w = writer();
        assertThatThrownBy(() -> w.write("$T", "not-a-symbol"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // $M formatter
    // ------------------------------------------------------------------

    @Test
    void formatModule_escapesReservedWord() {
        ErlangWriter w = writer();
        w.write("$M", "receive");
        assertThat(w.toString()).contains("receive_");
    }

    @Test
    void formatModule_plainName_unchanged() {
        ErlangWriter w = writer();
        w.write("$M", "weather_client");
        assertThat(w.toString()).contains("weather_client");
    }

    // ------------------------------------------------------------------
    // $F formatter
    // ------------------------------------------------------------------

    @Test
    void formatFunction_escapesReservedWord() {
        ErlangWriter w = writer();
        w.write("$F", "when");
        assertThat(w.toString()).contains("when_");
    }

    @Test
    void formatFunction_plainName_unchanged() {
        ErlangWriter w = writer();
        w.write("$F", "get_city");
        assertThat(w.toString()).contains("get_city");
    }

    // ------------------------------------------------------------------
    // $A formatter
    // ------------------------------------------------------------------

    @Test
    void formatAtom_plainLowercaseIdentifier_noQuotes() {
        ErlangWriter w = writer();
        w.write("$A", "ok");
        assertThat(w.toString()).contains("ok");
        assertThat(w.toString()).doesNotContain("'ok'");
    }

    @Test
    void formatAtom_identifierWithUppercase_getsQuoted() {
        ErlangWriter w = writer();
        w.write("$A", "GetCity");
        assertThat(w.toString()).contains("'GetCity'");
    }

    @Test
    void formatAtom_identifierWithSpaces_getsQuoted() {
        ErlangWriter w = writer();
        w.write("$A", "hello world");
        assertThat(w.toString()).contains("'hello world'");
    }

    // ------------------------------------------------------------------
    // $D formatter
    // ------------------------------------------------------------------

    @Test
    void formatDoc_shortText_emitsDoublePctPrefix() {
        ErlangWriter w = writer();
        w.write("$D", "A short description.");
        assertThat(w.toString()).contains("%% A short description.");
    }

    @Test
    void formatDoc_longText_wrapsLines() {
        ErlangWriter w = writer();
        String longText = "This is a fairly long documentation comment that should be "
                + "wrapped across multiple lines when it exceeds the eighty character limit.";
        w.write("$D", longText);
        String output = w.toString();
        for (String line : output.split("\n")) {
            assertThat(line.length()).isLessThanOrEqualTo(83);
        }
    }

    // ------------------------------------------------------------------
    // Module header helpers
    // ------------------------------------------------------------------

    @Test
    void writeModuleHeader_emitsModuleAndExportAttributes() {
        ErlangWriter w = writer();
        w.writeModuleHeader("weather_client");
        String output = w.toString();
        assertThat(output).contains("-module(weather_client).");
        assertThat(output).contains("-export(");
    }

    @Test
    void addExport_includesEntryInModuleHeader() {
        ErlangWriter w = writer();
        w.addExport("get_city", 2);
        w.writeModuleHeader("weather_client");
        assertThat(w.toString()).contains("get_city/2");
    }

    // ------------------------------------------------------------------
    // Whitespace normalisation
    // ------------------------------------------------------------------

    @Test
    void toString_collapsesExcessiveBlankLines() {
        ErlangWriter w = writer();
        w.write("line1");
        w.write("");
        w.write("");
        w.write("");
        w.write("line2");
        String output = w.toString();
        // Must not have three consecutive newlines (> 2 blank lines)
        assertThat(output).doesNotContain("\n\n\n\n");
    }

    @Test
    void factory_returnsNewWriter() {
        ErlangWriter w = ErlangWriter.factory("module.erl", "ignored");
        assertThat(w).isNotNull();
        assertThat(w.getFilename()).isEqualTo("module.erl");
    }
}
