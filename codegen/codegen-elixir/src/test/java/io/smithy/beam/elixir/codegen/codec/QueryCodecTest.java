package io.smithy.beam.elixir.codegen.codec;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.codegen.CodegenTestSupport;
import io.smithy.beam.elixir.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Unit tests for the Elixir {@link QueryCodec} (used by the {@code awsQuery} protocol).
 */
class QueryCodecTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.query",
            "",
            "service Svc {",
            "    version: \"2024\"",
            "    operations: [DescribeThings]",
            "}",
            "",
            "operation DescribeThings {",
            "    input: DescribeThingsInput",
            "    output: DescribeThingsOutput",
            "}",
            "",
            "structure DescribeThingsInput { name: String }",
            "structure DescribeThingsOutput { count: Integer }");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(MODEL, "test.query#Svc", "Test.Query");
    }

    @Test
    void writeRequestEncodeEmitsQueryEncodingAndAction() {
        OperationShape op = fx.operation("test.query#DescribeThings");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new QueryCodec().writeRequestEncode(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("content_type: \"application/x-www-form-urlencoded\",");
        assertThat(out).contains("encoding: :query,");
        assertThat(out).contains("action: \"DescribeThings\",");
    }

    @Test
    void writeResponseDecodeEmitsXmlDecoding() {
        OperationShape op = fx.operation("test.query#DescribeThings");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new QueryCodec().writeResponseDecode(w, fx.ctx(), op);

        assertThat(w.toString()).contains("decoding: :xml,");
    }

    @Test
    void writeErrorDecodeUsesXmlErrorParser() {
        OperationShape op = fx.operation("test.query#DescribeThings");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new QueryCodec().writeErrorDecode(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("parse_error_fn: &SmithyXml.parse_error/2,");
    }

    @Test
    void contentTypeIsFormUrlEncoded() {
        assertThat(new QueryCodec().contentType())
                .isEqualTo("application/x-www-form-urlencoded");
    }
}
