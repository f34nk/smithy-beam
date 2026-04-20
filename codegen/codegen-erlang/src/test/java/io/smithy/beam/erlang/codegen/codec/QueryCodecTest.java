package io.smithy.beam.erlang.codegen.codec;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.codegen.CodegenTestSupport;
import io.smithy.beam.erlang.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Unit tests for {@link QueryCodec} (used by the {@code awsQuery} protocol).
 */
class QueryCodecTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.query",
            "",
            "service Svc {",
            "    version: \"2024\"",
            "    operations: [DescribeThings, Heartbeat]",
            "}",
            "",
            "operation DescribeThings {",
            "    input: DescribeThingsInput",
            "    output: DescribeThingsOutput",
            "}",
            "",
            "operation Heartbeat {",
            "    input: HeartbeatInput",
            "    output: HeartbeatOutput",
            "}",
            "",
            "structure DescribeThingsInput {",
            "    name: String",
            "}",
            "",
            "structure DescribeThingsOutput {",
            "    count: Integer",
            "}",
            "",
            "structure HeartbeatInput {}",
            "",
            "structure HeartbeatOutput {}");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(MODEL, "test.query#Svc", "svc");
    }

    @Test
    void writeRequestEncodeEmitsSmithyQueryEncodeWithAction() {
        OperationShape op = fx.operation("test.query#DescribeThings");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new QueryCodec().writeRequestEncode(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("Body = smithy_query:encode(Input,")
                .contains("\"describe_things_input\"")
                .contains("\"DescribeThings\"");
    }

    @Test
    void writeRequestEncodeEmitsActionOnlyWhenInputEmpty() {
        OperationShape op = fx.operation("test.query#Heartbeat");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new QueryCodec().writeRequestEncode(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("Body = smithy_query:encode_action(\"Heartbeat\")");
    }

    @Test
    void writeResponseDecodeUnwrapsResultElement() {
        OperationShape op = fx.operation("test.query#DescribeThings");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new QueryCodec().writeResponseDecode(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("smithy_xml:decode(ResponseBody,")
                .contains("\"describe_things_output\"")
                .contains("\"DescribeThingsResult\"");
    }

    @Test
    void writeErrorDecodeEmitsXmlErrorParser() {
        OperationShape op = fx.operation("test.query#DescribeThings");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new QueryCodec().writeErrorDecode(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("try smithy_xml:decode_error(StatusCode, Body) of")
                .contains("end.");
    }

    @Test
    void contentTypeIsFormUrlEncoded() {
        assertThat(new QueryCodec().contentType())
                .isEqualTo("application/x-www-form-urlencoded");
    }
}
