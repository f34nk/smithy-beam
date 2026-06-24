package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangAwsQueryIrTest {
    @Test
    void queryHelpersAwsAsStringMatchGolden() throws IOException {
        assertGolden(ErlangAwsQueryIr.queryHelpers(false), "ir/aws_query_flatten_helpers_aws.expected.erl");
    }

    @Test
    void queryHelpersEc2AsStringMatchGolden() throws IOException {
        assertGolden(ErlangAwsQueryIr.queryHelpers(true), "ir/aws_query_flatten_helpers_ec2.expected.erl");
    }

    @Test
    void xmlHelpersAwsAsStringMatchGolden() throws IOException {
        assertGolden(ErlangAwsQueryIr.xmlHelpers(false), "ir/aws_query_xml_helpers_aws.expected.erl");
    }

    @Test
    void serverQueryDecodeHelpersAwsAsStringMatchGolden() throws IOException {
        assertGolden(ErlangAwsQueryIr.serverQueryDecodeHelpers(false), "ir/aws_query_form_decode_aws.expected.erl");
    }

    @Test
    void queryHelpersAwsContainsMapListAndStructureClauses() {
        for (ErlFunction fn : ErlangAwsQueryIr.queryHelpers(false)) {
            assertStructural(fn);
        }
        String text = helpersAsString(ErlangAwsQueryIr.queryHelpers(false));
        assertThat(text).contains("flatten_member(Key, Value) when is_map(Value) ->");
        assertThat(text).contains("flatten_member(Key, Value) when is_list(Value) ->");
        assertThat(text).contains("flatten_member(Key, Value) when is_tuple(Value) ->");
        assertThat(text).contains("flatten_structure(_Key, _Value) ->");
    }

    @Test
    void xmlHelpersAwsContainsListDecodeHelpers() {
        for (ErlFunction fn : ErlangAwsQueryIr.xmlHelpers(false)) {
            assertStructural(fn);
        }
        String text = helpersAsString(ErlangAwsQueryIr.xmlHelpers(false));
        assertThat(text).contains("xml_child_list(Parent, ListName, ItemName) ->");
        assertThat(text).contains("xml_child_struct_list(Parent, ListName, ItemName, DecodeFun) ->");
    }

    @Test
    void serverQueryDecodeHelpersAreStructural() {
        for (ErlFunction fn : ErlangAwsQueryIr.serverQueryDecodeHelpers(false)) {
            assertStructural(fn);
        }
    }

    @Test
    void serverXmlEncodeHelpersAreStructural() {
        for (ErlFunction fn : ErlangAwsQueryIr.serverXmlEncodeHelpers(false)) {
            assertStructural(fn);
        }
        for (ErlFunction fn : ErlangAwsQueryIr.serverXmlEncodeHelpers(true)) {
            assertStructural(fn);
        }
    }

    private static void assertGolden(List<ErlFunction> functions, String resourcePath) throws IOException {
        assertThat(helpersAsString(functions)).isEqualTo(readExpectedString(resourcePath));
        for (ErlFunction fn : functions) {
            assertStructural(fn);
        }
    }

    private static String helpersAsString(List<ErlFunction> functions) {
        return functions.stream().map(ErlFunction::asString).collect(Collectors.joining("\n\n"));
    }

    private static void assertStructural(ErlFunction fn) {
        assertThat(fn.name()).isNotBlank();
        assertThat(fn.clauses()).isNotEmpty();
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlangAwsQueryIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }
}
