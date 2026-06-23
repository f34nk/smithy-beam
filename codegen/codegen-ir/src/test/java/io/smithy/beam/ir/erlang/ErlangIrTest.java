package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangIrTest {
    @Test
    void buildsDecodeBasicItemFunction() {
        ErlFunction decodeBasicItem = decodeBasicItemFunction(null);

        assertThat(decodeBasicItem.name()).isEqualTo("decode_basic_item");
        assertThat(decodeBasicItem.arity()).isEqualTo(1);
        assertThat(decodeBasicItem.clauses()).hasSize(3);

        ErlClause mapClause = decodeBasicItem.clauses().get(2);
        assertThat(mapClause.guards()).hasSize(1);
        assertThat(mapClause.body()).hasSize(1);
        assertThat(mapClause.body().get(0)).isInstanceOf(ErlRecord.class);

        ErlRecord record = (ErlRecord) mapClause.body().get(0);
        assertThat(record.name()).isEqualTo("basic_item");
        assertThat(record.fields()).hasSize(2);
        assertThat(record.fields().get(0).name()).isEqualTo("name");
        assertThat(record.fields().get(0).value()).isInstanceOf(ErlCall.class);
        assertThat(record.fields().get(1).name()).isEqualTo("count");
        assertThat(record.fields().get(1).value()).isInstanceOf(ErlCall.class);
    }

    @Test
    void decodeBasicItemLinesMatchGolden() throws IOException {
        ErlFunction decodeBasicItem = decodeBasicItemFunction(
                ErlangIr.functionSpec(
                        "decode_basic_item",
                        "undefined | null | map()",
                        "undefined | #basic_item{}"));

        assertThat(decodeBasicItem.lines()).isEqualTo(readExpectedLines("ir/decode_basic_item.expected.erl"));
    }

    @Test
    void decodeBasicItemAsStringMatchGolden() throws IOException {
        ErlFunction decodeBasicItem = decodeBasicItemFunction(
                ErlangIr.functionSpec(
                        "decode_basic_item",
                        "undefined | null | map()",
                        "undefined | #basic_item{}"));

        assertThat(decodeBasicItem.asString()).isEqualTo(readExpectedString("ir/decode_basic_item.expected.erl"));
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlangIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }

    private static ErlFunction decodeBasicItemFunction(ErlFunctionSpec specOrNull) {
        List<ErlClause> clauses = List.of(
                ErlangIr.clause(
                        List.of(ErlangIr.atomPattern("undefined")),
                        ErlangIr.atom("undefined")),
                ErlangIr.clause(
                        List.of(ErlangIr.atomPattern("null")),
                        ErlangIr.atom("undefined")),
                ErlangIr.clause(
                        List.of(ErlangIr.varPattern("Map")),
                        List.of(ErlangIr.guard("is_map", ErlangIr.var("Map"))),
                        basicItemRecord()));

        if (specOrNull == null) {
            return ErlangIr.function("decode_basic_item", 1, clauses);
        }
        return ErlangIr.functionWithSpec("decode_basic_item", 1, specOrNull, clauses);
    }

    private static ErlRecord basicItemRecord() {
        return ErlangIr.record(
                "basic_item",
                ErlangIr.field(
                        "name",
                        ErlangIr.call(
                                "maps",
                                "get",
                                ErlangIr.binary("name"),
                                ErlangIr.var("Map"),
                                ErlangIr.atom("undefined"))),
                ErlangIr.field(
                        "count",
                        ErlangIr.call(
                                "maps",
                                "get",
                                ErlangIr.binary("count"),
                                ErlangIr.var("Map"),
                                ErlangIr.atom("undefined"))));
    }

    private static List<String> readExpectedLines(String resourcePath) throws IOException {
        try (InputStream in = ErlangIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return Arrays.asList(text.split("\n", -1));
        }
    }
}
