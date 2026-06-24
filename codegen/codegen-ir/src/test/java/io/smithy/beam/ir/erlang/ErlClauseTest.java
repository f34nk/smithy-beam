package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlClauseTest {
    private static ErlCall mapsGet(String key) {
        return new ErlCall(
                new ErlAtom("maps"), "get",
                List.of(new ErlBinary(key), new ErlVar("Map"), new ErlAtom("undefined")));
    }

    private static ErlRecord basicItemRecord() {
        return new ErlRecord("basic_item", null, List.of(
                new ErlRecordField("name", mapsGet("name")),
                new ErlRecordField("count", mapsGet("count"))));
    }

    private static ErlClause mapClause() {
        return new ErlClause(
                List.of(new ErlVarPattern("Map")),
                List.of(new ErlGuard("is_map", List.of(new ErlVar("Map")))),
                List.of(basicItemRecord()));
    }

    @Test
    void inlineAtomClauseLines() {
        ErlClause clause = new ErlClause(
                List.of(new ErlAtomPattern("undefined")), List.of(),
                List.of(new ErlAtom("undefined")));
        assertThat(clause.lines(0, "decode_basic_item", true))
                .containsExactly("decode_basic_item(undefined) -> undefined;");
    }

    @Test
    void inlineAtomClauseAsStringIsNotSupported() {
        assertThat(new ErlAtom("undefined").asString()).isEqualTo("undefined");
    }

    @Test
    void multilineRecordClauseLines() {
        ErlClause clause = mapClause();
        assertThat(clause.lines(0, "decode_basic_item", false))
                .containsExactly(
                        "decode_basic_item(Map) when is_map(Map) ->",
                        "    #basic_item{",
                        "        name = maps:get(<<\"name\">>, Map, undefined),",
                        "        count = maps:get(<<\"count\">>, Map, undefined)",
                        "    }.");
    }

    @Test
    void recordPatternClauseStaysSingleLine() {
        ErlClause clause = new ErlClause(
                List.of(ErlRecordPattern.recordPattern(
                        "http_response",
                        ErlRecordFieldPattern.fieldPattern("status", ErlIntegerPattern.integerPattern(200)),
                        ErlRecordFieldPattern.fieldPattern("headers", ErlVarPattern.varPattern("Headers")),
                        ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")))),
                List.of(),
                List.of(new ErlAtom("ok")));
        assertThat(clause.lines(0, "decode_get_name_response", false))
                .containsExactly(
                        "decode_get_name_response(#http_response{status = 200, headers = Headers, body = Body}) -> ok.");
    }

    @Test
    void aliasedEmptyMapClauseStaysSingleLine() {
        ErlClause clause = new ErlClause(
                List.of(new ErlRecordPattern("", List.of(), "Map")),
                List.of(),
                List.of(new ErlAtom("undefined")));
        assertThat(clause.lines(0, "decode_event", false))
                .containsExactly("decode_event(Map = #{}) -> undefined.");
    }

    @Test
    void aliasedRecordClauseStaysSingleLine() {
        ErlClause clause = new ErlClause(
                List.of(ErlRecordPattern.recordFunctionHead(
                        "Input", "input_record", List.of("field_a", "field_b"))),
                List.of(),
                List.of(new ErlAtom("ok")));
        assertThat(clause.lines(0, "handle", false))
                .containsExactly("handle(Input = #input_record{field_a, field_b}) -> ok.");
    }

    @Test
    void multilineRecordBodyAsString() {
        ErlRecord record = basicItemRecord();
        assertThat(record.asString(1)).isEqualTo(
                "    #basic_item{\n"
                        + "        name = maps:get(<<\"name\">>, Map, undefined),\n"
                        + "        count = maps:get(<<\"count\">>, Map, undefined)\n"
                        + "    }");
    }
}
