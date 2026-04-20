package io.smithy.beam.elixir.codegen.codec;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.codegen.CodegenTestSupport;
import io.smithy.beam.elixir.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Unit tests for {@link JsonCodec}.
 *
 * <p>Asserts the {@code content_type:} / {@code encoding:} / {@code decoding:}
 * / {@code parse_error_fn:} fields the codec contributes to the
 * {@code %SmithyClient.Operation{}} struct.
 */
class JsonCodecTest {

    private static final String MODEL = String.join("\n",
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
            "    name: String",
            "}",
            "",
            "structure GetItemOutput {}",
            "",
            "structure PingInput {",
            "    @httpQuery(\"q\") q: String",
            "}",
            "",
            "structure PingOutput {}");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(MODEL, "test.json#Svc", "Test.Json");
    }

    @Test
    void writeRequestEncodeEmitsJsonEncodingForBodyMembers() {
        OperationShape op = fx.operation("test.json#GetItem");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new JsonCodec().writeRequestEncode(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("content_type: \"application/json\",");
        assertThat(out).contains("encoding: :json,");
    }

    @Test
    void writeRequestEncodeEmitsNoneEncodingWhenNoBodyMembers() {
        OperationShape op = fx.operation("test.json#Ping");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new JsonCodec().writeRequestEncode(w, fx.ctx(), op);

        assertThat(w.toString()).contains("encoding: :none,");
    }

    @Test
    void writeResponseDecodeEmitsJsonDecoding() {
        OperationShape op = fx.operation("test.json#GetItem");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new JsonCodec().writeResponseDecode(w, fx.ctx(), op);

        assertThat(w.toString()).contains("decoding: :json,");
    }

    @Test
    void writeErrorDecodeStandardModeUsesLocalParser() {
        OperationShape op = fx.operation("test.json#GetItem");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new JsonCodec().writeErrorDecode(w, fx.ctx(), op);

        assertThat(w.toString()).contains("parse_error_fn: &parse_error/2,");
    }

    @Test
    void writeErrorDecodeAwsModeUsesAwsParser() {
        OperationShape op = fx.operation("test.json#GetItem");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new JsonCodec(JsonCodec.AWS_FLAVOR).writeErrorDecode(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("parse_error_fn: &SmithyJson.parse_aws_error/2,");
    }

    @Test
    void contentTypeIsApplicationJson() {
        assertThat(new JsonCodec().contentType()).isEqualTo("application/json");
    }
}
