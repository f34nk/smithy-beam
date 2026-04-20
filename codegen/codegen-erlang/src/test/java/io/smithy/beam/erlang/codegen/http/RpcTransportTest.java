package io.smithy.beam.erlang.codegen.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.codegen.CodegenTestSupport;
import io.smithy.beam.erlang.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Unit tests for {@link RpcTransport}.
 *
 * <p>Verifies the {@code POST /} envelope and the {@code X-Amz-Target} header
 * derived from the service + operation names, plus the
 * {@code smithy_sigv4:sign_request} response dispatch.
 */
class RpcTransportTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.rpc",
            "",
            "service Coffee {",
            "    version: \"2024\"",
            "    operations: [Brew]",
            "}",
            "",
            "operation Brew {",
            "    input: BrewInput",
            "    output: BrewOutput",
            "}",
            "",
            "structure BrewInput {",
            "    cup: String",
            "}",
            "",
            "structure BrewOutput {",
            "    ready: Boolean",
            "}");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(MODEL, "test.rpc#Coffee", "coffee");
    }

    @Test
    void writeRequestEmitsPostToRoot() {
        OperationShape op = fx.operation("test.rpc#Brew");
        ErlangWriter w = CodegenTestSupport.writer("coffee_client.erl");

        new RpcTransport().writeRequest(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("Method = <<\"POST\">>,");
        assertThat(out).contains("Uri = <<\"/\">>,");
        assertThat(out).contains("QueryString = <<>>,");
        assertThat(out).contains("Url = <<Endpoint/binary, Uri/binary, QueryString/binary>>,");
    }

    @Test
    void writeRequestEmitsXAmzTargetHeader() {
        OperationShape op = fx.operation("test.rpc#Brew");
        ErlangWriter w = CodegenTestSupport.writer("coffee_client.erl");

        new RpcTransport().writeRequest(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("{<<\"X-Amz-Target\">>, <<\"Coffee.Brew\">>}");
        assertThat(out).contains("{<<\"Content-Type\">>, <<\"application/x-amz-json-1.0\">>}");
    }

    @Test
    void writeResponseEmitsSigV4EnvelopeAndInvokesCallback() {
        OperationShape op = fx.operation("test.rpc#Brew");
        ErlangWriter w = CodegenTestSupport.writer("coffee_client.erl");

        boolean[] called = {false};
        new RpcTransport().writeResponse(w, fx.ctx(), op, () -> {
            called[0] = true;
            w.write("decode_emitted,");
        });

        String out = w.toString();
        assertThat(called[0]).isTrue();
        assertThat(out).contains("smithy_sigv4:sign_request(Method, Url, Headers, Body, Client)");
        assertThat(out).contains("httpc:request(post, Request,");
        assertThat(out).contains("decode_emitted,");
        assertThat(out).contains("parse_error(ErrStatusCode, ErrorBody);");
        assertThat(out).contains("end.");
    }
}
