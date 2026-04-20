package io.smithy.beam.erlang.codegen.codec;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.codegen.CodegenTestSupport;
import io.smithy.beam.erlang.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Unit tests for {@link XmlCodec} (used by the {@code restXml} protocol).
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
            "structure PutObjectOutput {",
            "    etag: String",
            "}",
            "",
            "structure ListBucketsInput {}",
            "",
            "structure ListBucketsOutput {",
            "    names: BucketNames",
            "}",
            "",
            "list BucketNames {",
            "    member: String",
            "}");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(MODEL, "test.xml#Svc", "svc");
    }

    @Test
    void writeRequestEncodeEmitsSmithyXmlEncode() {
        OperationShape op = fx.operation("test.xml#PutObject");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new XmlCodec().writeRequestEncode(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("Body = smithy_xml:encode(Input,")
                .contains("\"put_object_input\"");
    }

    @Test
    void writeRequestEncodeEmitsEmptyBinaryWhenNoMembers() {
        OperationShape op = fx.operation("test.xml#ListBuckets");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new XmlCodec().writeRequestEncode(w, fx.ctx(), op);

        assertThat(w.toString()).contains("Body = <<>>,");
    }

    @Test
    void writeResponseDecodeEmitsSmithyXmlDecodeCase() {
        OperationShape op = fx.operation("test.xml#PutObject");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new XmlCodec().writeResponseDecode(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("case ResponseBody of");
        assertThat(out).contains("smithy_xml:decode(ResponseBody,");
        assertThat(out).contains("xml_decode_error");
        assertThat(out).contains("#put_object_output{}");
    }

    @Test
    void writeErrorDecodeEmitsSmithyXmlDecodeErrorBlock() {
        OperationShape op = fx.operation("test.xml#PutObject");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new XmlCodec().writeErrorDecode(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("try smithy_xml:decode_error(StatusCode, Body) of");
        assertThat(out).contains("end.");
    }

    @Test
    void contentTypeIsApplicationXml() {
        assertThat(new XmlCodec().contentType()).isEqualTo("application/xml");
    }
}
