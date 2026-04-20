package io.smithy.beam.elixir.codegen.codec;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.codegen.CodegenTestSupport;
import io.smithy.beam.elixir.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Unit tests for the Elixir {@link XmlCodec} (used by the {@code restXml} protocol).
 */
class XmlCodecTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.xml",
            "",
            "service Svc {",
            "    version: \"2024\"",
            "    operations: [PutObject, ListBuckets]",
            "}",
            "",
            "@http(method: \"PUT\", uri: \"/objects/{key}\")",
            "operation PutObject {",
            "    input: PutObjectInput",
            "    output: PutObjectOutput",
            "}",
            "",
            "@http(method: \"GET\", uri: \"/buckets\")",
            "operation ListBuckets {",
            "    input: ListBucketsInput",
            "    output: ListBucketsOutput",
            "}",
            "",
            "structure PutObjectInput {",
            "    @httpLabel @required key: String",
            "    payload: String",
            "}",
            "",
            "structure PutObjectOutput {}",
            "",
            "structure ListBucketsInput {}",
            "",
            "structure ListBucketsOutput {}");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(MODEL, "test.xml#Svc", "Test.Xml");
    }

    @Test
    void writeRequestEncodeEmitsXmlEncoding() {
        OperationShape op = fx.operation("test.xml#PutObject");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new XmlCodec().writeRequestEncode(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("content_type: \"application/xml\",");
        assertThat(out).contains("encoding: :xml,");
    }

    @Test
    void writeRequestEncodeEmitsNoneEncodingWhenNoMembers() {
        OperationShape op = fx.operation("test.xml#ListBuckets");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new XmlCodec().writeRequestEncode(w, fx.ctx(), op);

        assertThat(w.toString()).contains("encoding: :none,");
    }

    @Test
    void writeResponseDecodeEmitsXmlDecoding() {
        OperationShape op = fx.operation("test.xml#PutObject");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new XmlCodec().writeResponseDecode(w, fx.ctx(), op);

        assertThat(w.toString()).contains("decoding: :xml,");
    }

    @Test
    void writeErrorDecodeUsesXmlErrorParser() {
        OperationShape op = fx.operation("test.xml#PutObject");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new XmlCodec().writeErrorDecode(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("parse_error_fn: &SmithyXml.parse_error/2,");
    }

    @Test
    void contentTypeIsApplicationXml() {
        assertThat(new XmlCodec().contentType()).isEqualTo("application/xml");
    }
}
