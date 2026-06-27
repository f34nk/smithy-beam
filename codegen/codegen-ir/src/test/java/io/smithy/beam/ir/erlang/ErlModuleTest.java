package io.smithy.beam.ir.erlang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErlModuleTest {
    @Test
    void basicServiceRestJsonLines() throws IOException {
        ErlModule module = buildBasicServiceRestJson();
        assertThat(module.lines()).isEqualTo(readExpectedLines("ir/basic_service_rest_json_1.expected.erl"));
    }

    @Test
    void basicServiceRestJsonAsString() throws IOException {
        ErlModule module = buildBasicServiceRestJson();
        assertThat(module.asString()).isEqualTo(readExpectedString("ir/basic_service_rest_json_1.expected.erl"));
    }

    private static ErlModule buildBasicServiceRestJson() {
        return new ErlModule(
                "basic_service_rest_json_1",
                List.of(
                        ErlComment.comment("Generated REST JSON codec."),
                        ErlModuleDoc.moduleDoc("REST JSON 1 codecs for basic_service (generated).")),
                List.of(
                        new ErlAttribute("include", "\"basic_types.hrl\""),
                        ErlExportAttribute.export(List.of("decode_basic_item/1"))),
                List.of(buildDecodeBasicItem()));
    }

    private static ErlFunction buildDecodeBasicItem() {
        return new ErlFunction(
                "decode_basic_item",
                1,
                null,
                new ErlFunctionSpec(
                        "decode_basic_item",
                        "undefined | null | map()",
                        "undefined | #basic_item{}"),
                List.of(
                        new ErlClause(
                                List.of(new ErlAtomPattern("undefined")),
                                List.of(),
                                List.of(new ErlAtom("undefined"))),
                        new ErlClause(
                                List.of(new ErlAtomPattern("null")),
                                List.of(),
                                List.of(new ErlAtom("undefined"))),
                        new ErlClause(
                                List.of(new ErlVarPattern("Map")),
                                List.of(new ErlGuard("is_map", List.of(new ErlVar("Map")))),
                                List.of(basicItemRecord()))));
    }

    private static ErlRecord basicItemRecord() {
        return new ErlRecord("basic_item", null, List.of(
                new ErlRecordField("name", mapsGet("name")),
                new ErlRecordField("count", mapsGet("count"))));
    }

    private static ErlCall mapsGet(String key) {
        return new ErlCall(
                new ErlAtom("maps"), "get",
                List.of(new ErlBinary(key), new ErlVar("Map"), new ErlAtom("undefined")));
    }

    private static List<String> readExpectedLines(String resourcePath) throws IOException {
        try (InputStream in = ErlModuleTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return Arrays.asList(text.split("\n", -1));
        }
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlModuleTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }
}
