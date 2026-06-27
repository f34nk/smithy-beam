package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DecodeBasicItemGoldenTest {
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
                ErlFunctionSpec.functionSpec(
                        "decode_basic_item",
                        "undefined | null | map()",
                        "undefined | #basic_item{}"));

        assertThat(decodeBasicItem.lines()).isEqualTo(readExpectedLines("ir/decode_basic_item.expected.erl"));
    }

    @Test
    void decodeBasicItemAsStringMatchGolden() throws IOException {
        ErlFunction decodeBasicItem = decodeBasicItemFunction(
                ErlFunctionSpec.functionSpec(
                        "decode_basic_item",
                        "undefined | null | map()",
                        "undefined | #basic_item{}"));

        assertThat(decodeBasicItem.asString()).isEqualTo(readExpectedString("ir/decode_basic_item.expected.erl"));
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = DecodeBasicItemGoldenTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
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
                ErlClause.clause(
                        List.of(ErlAtomPattern.atomPattern("undefined")),
                        ErlAtom.atom("undefined")),
                ErlClause.clause(
                        List.of(ErlAtomPattern.atomPattern("null")),
                        ErlAtom.atom("undefined")),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Map")),
                        List.of(ErlGuard.guard("is_map", ErlVar.var("Map"))),
                        basicItemRecord()));

        if (specOrNull == null) {
            return ErlFunction.function("decode_basic_item", 1, clauses);
        }
        return ErlFunction.functionWithSpec("decode_basic_item", 1, specOrNull, clauses);
    }

    private static ErlRecord basicItemRecord() {
        return ErlRecord.record(
                "basic_item",
                ErlRecordField.field(
                        "name",
                        ErlCall.call(
                                "maps",
                                "get",
                                ErlBinary.binary("name"),
                                ErlVar.var("Map"),
                                ErlAtom.atom("undefined"))),
                ErlRecordField.field(
                        "count",
                        ErlCall.call(
                                "maps",
                                "get",
                                ErlBinary.binary("count"),
                                ErlVar.var("Map"),
                                ErlAtom.atom("undefined"))));
    }

    private static List<String> readExpectedLines(String resourcePath) throws IOException {
        try (InputStream in = DecodeBasicItemGoldenTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return Arrays.asList(text.split("\n", -1));
        }
    }
}
