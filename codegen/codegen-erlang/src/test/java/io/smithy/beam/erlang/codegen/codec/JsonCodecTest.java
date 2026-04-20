package io.smithy.beam.erlang.codegen.codec;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.codegen.CodegenTestSupport;
import io.smithy.beam.erlang.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Unit tests for {@link JsonCodec}.
 *
 * <p>Asserts the substring patterns the codec is expected to emit for each
 * of {@code writeRequestEncode}, {@code writeResponseDecode}, and
 * {@code writeErrorDecode} for both the standard and AWS JSON variants.
 */
class JsonCodecTest {

    private static final String REST_MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.json",
            "",
            "service Svc {",
            "    version: \"2024\"",
            "    operations: [GetItem, Ping]",
            "}",
            "",
            "@http(method: \"POST\", uri: \"/items/{id}\")",
            "operation GetItem {",
            "    input: GetItemInput",
            "    output: GetItemOutput",
            "}",
            "",
            "@http(method: \"GET\", uri: \"/ping\")",
            "operation Ping {",
            "    input: PingInput",
            "    output: PingOutput",
            "}",
            "",
            "structure GetItemInput {",
            "    @httpLabel @required id: String",
            "    @httpHeader(\"X-Tag\") tag: String",
            "    name: String",
            "}",
            "",
            "structure GetItemOutput {",
            "    name: String",
            "    count: Integer",
            "}",
            "",
            "structure PingInput {",
            "    @httpQuery(\"q\") q: String",
            "}",
            "",
            "structure PingOutput {}");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(REST_MODEL, "test.json#Svc", "svc");
    }

    @Test
    void writeRequestEncodeEmitsJsxEncodeForBodyMembers() {
        OperationShape op = fx.operation("test.json#GetItem");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new JsonCodec().writeRequestEncode(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("Body = jsx:encode(");
        assertThat(out).contains("<<\"name\">>");
        assertThat(out).doesNotContain("<<\"id\">>");
        assertThat(out).doesNotContain("<<\"tag\">>");
    }

    @Test
    void writeRequestEncodeEmitsEmptyBinaryWhenNoBodyMembers() {
        OperationShape op = fx.operation("test.json#Ping");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new JsonCodec().writeRequestEncode(w, fx.ctx(), op);

        assertThat(w.toString()).contains("Body = <<>>,");
    }

    @Test
    void writeResponseDecodeEmitsJsxDecodeCaseBlock() {
        OperationShape op = fx.operation("test.json#GetItem");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new JsonCodec().writeResponseDecode(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("case ResponseBody of");
        assertThat(out).contains("jsx:decode(ResponseBody, [return_maps])");
        assertThat(out).contains("#get_item_output{");
        assertThat(out).contains("json_decode_error");
    }

    @Test
    void writeResponseDecodeForEmptyOutputStillEmitsCase() {
        OperationShape op = fx.operation("test.json#Ping");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new JsonCodec().writeResponseDecode(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("case ResponseBody of");
        assertThat(out).contains("#ping_output{}");
    }

    @Test
    void writeErrorDecodeStandardModeEmitsHttpErrorTuple() {
        OperationShape op = fx.operation("test.json#GetItem");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new JsonCodec().writeErrorDecode(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("{error, {http_error, StatusCode, Body}}.");
        assertThat(out).doesNotContain("__type");
    }

    @Test
    void writeErrorDecodeAwsModeInspectsTypeField() {
        OperationShape op = fx.operation("test.json#GetItem");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new JsonCodec(JsonCodec.AWS_FLAVOR).writeErrorDecode(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("try jsx:decode(Body, [return_maps]) of");
        assertThat(out).contains("__type");
        assertThat(out).contains("end.");
    }

    @Test
    void contentTypeIsApplicationJson() {
        assertThat(new JsonCodec().contentType()).isEqualTo("application/json");
    }
}
