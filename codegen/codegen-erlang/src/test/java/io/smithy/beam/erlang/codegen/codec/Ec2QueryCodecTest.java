package io.smithy.beam.erlang.codegen.codec;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.codegen.CodegenTestSupport;
import io.smithy.beam.erlang.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Unit tests for {@link Ec2QueryCodec} (used by the {@code ec2Query} protocol).
 *
 * <p>Differs from {@link QueryCodec} only in the request-encode helper name
 * ({@code smithy_query:encode_ec2/3}) and the response wrapper element name
 * ({@code <Op>Response} instead of {@code <Op>Result}).
 */
class Ec2QueryCodecTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.ec2",
            "",
            "service Svc {",
            "    version: \"2024\"",
            "    operations: [RunInstances, Heartbeat]",
            "}",
            "",
            "operation RunInstances {",
            "    input: RunInstancesInput",
            "    output: RunInstancesOutput",
            "}",
            "",
            "operation Heartbeat {",
            "    input: HeartbeatInput",
            "    output: HeartbeatOutput",
            "}",
            "",
            "structure RunInstancesInput {",
            "    imageId: String",
            "}",
            "",
            "structure RunInstancesOutput {",
            "    reservationId: String",
            "}",
            "",
            "structure HeartbeatInput {}",
            "",
            "structure HeartbeatOutput {}");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(MODEL, "test.ec2#Svc", "svc");
    }

    @Test
    void writeRequestEncodeEmitsEc2EncodeWithAction() {
        OperationShape op = fx.operation("test.ec2#RunInstances");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new Ec2QueryCodec().writeRequestEncode(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("Body = smithy_query:encode_ec2(Input,")
                .contains("\"run_instances_input\"")
                .contains("\"RunInstances\"");
    }

    @Test
    void writeRequestEncodeEmitsEc2ActionOnlyWhenInputEmpty() {
        OperationShape op = fx.operation("test.ec2#Heartbeat");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new Ec2QueryCodec().writeRequestEncode(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("Body = smithy_query:encode_ec2_action(\"Heartbeat\")");
    }

    @Test
    void writeResponseDecodeUnwrapsResponseElement() {
        OperationShape op = fx.operation("test.ec2#RunInstances");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new Ec2QueryCodec().writeResponseDecode(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("smithy_xml:decode(ResponseBody,")
                .contains("\"run_instances_output\"")
                .contains("\"RunInstancesResponse\"");
    }

    @Test
    void writeErrorDecodeEmitsXmlErrorParser() {
        OperationShape op = fx.operation("test.ec2#RunInstances");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new Ec2QueryCodec().writeErrorDecode(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("try smithy_xml:decode_error(StatusCode, Body) of")
                .contains("end.");
    }

    @Test
    void contentTypeIsFormUrlEncoded() {
        assertThat(new Ec2QueryCodec().contentType())
                .isEqualTo("application/x-www-form-urlencoded");
    }
}
