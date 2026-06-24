package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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
        ErlFunction fn = ErlangAwsQueryIr.queryHelpers(false);
        String text = fn.asString();
        assertThat(text).contains("flatten_member(Key, Value) when is_map(Value) ->");
        assertThat(text).contains("flatten_member(Key, Value) when is_list(Value) ->");
        assertThat(text).contains("flatten_member(Key, Value) when is_tuple(Value) ->");
        assertThat(text).contains("flatten_structure(_Key, _Value) ->");
    }

    @Test
    void xmlHelpersAwsContainsListDecodeHelpers() {
        ErlFunction fn = ErlangAwsQueryIr.xmlHelpers(false);
        String text = fn.asString();
        assertThat(text).contains("xml_child_list(Parent, ListName, ItemName) ->");
        assertThat(text).contains("xml_child_struct_list(Parent, ListName, ItemName, DecodeFun) ->");
    }

    private static void assertGolden(ErlFunction fn, String resourcePath) throws IOException {
        assertThat(fn.asString()).isEqualTo(readExpectedString(resourcePath));
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
