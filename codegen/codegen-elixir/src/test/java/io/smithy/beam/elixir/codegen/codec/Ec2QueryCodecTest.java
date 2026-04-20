package io.smithy.beam.elixir.codegen.codec;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.codegen.CodegenTestSupport;
import io.smithy.beam.elixir.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Unit tests for the Elixir {@link Ec2QueryCodec} (used by the {@code ec2Query}
 * protocol). Differs from {@link QueryCodec} only in the {@code encoding:}
 * atom emitted ({@code :ec2_query}).
 */
class Ec2QueryCodecTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.ec2",
            "",
            "service Svc {",
            "    version: \"2024\"",
            "    operations: [RunInstances]",
            "}",
            "",
            "operation RunInstances {",
            "    input: RunInstancesInput",
            "    output: RunInstancesOutput",
            "}",
            "",
            "structure RunInstancesInput { imageId: String }",
            "structure RunInstancesOutput { reservationId: String }");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(MODEL, "test.ec2#Svc", "Test.Ec2");
    }

    @Test
    void writeRequestEncodeEmitsEc2QueryEncoding() {
        OperationShape op = fx.operation("test.ec2#RunInstances");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new Ec2QueryCodec().writeRequestEncode(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("encoding: :ec2_query,");
        assertThat(out).contains("action: \"RunInstances\",");
    }

    @Test
    void writeResponseDecodeEmitsXmlDecoding() {
        OperationShape op = fx.operation("test.ec2#RunInstances");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new Ec2QueryCodec().writeResponseDecode(w, fx.ctx(), op);

        assertThat(w.toString()).contains("decoding: :xml,");
    }

    @Test
    void writeErrorDecodeUsesXmlErrorParser() {
        OperationShape op = fx.operation("test.ec2#RunInstances");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new Ec2QueryCodec().writeErrorDecode(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("parse_error_fn: &SmithyXml.parse_error/2,");
    }

    @Test
    void contentTypeIsFormUrlEncoded() {
        assertThat(new Ec2QueryCodec().contentType())
                .isEqualTo("application/x-www-form-urlencoded");
    }
}
