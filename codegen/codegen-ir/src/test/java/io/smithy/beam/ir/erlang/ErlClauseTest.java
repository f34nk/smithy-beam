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
    void multilineRecordBodyAsString() {
        ErlRecord record = basicItemRecord();
        assertThat(record.asString(1)).isEqualTo(
                "    #basic_item{\n"
                        + "        name = maps:get(<<\"name\">>, Map, undefined),\n"
                        + "        count = maps:get(<<\"count\">>, Map, undefined)\n"
                        + "    }");
    }
}
