package io.smithy.beam.elixir.codegen.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.codegen.CodegenTestSupport;
import io.smithy.beam.elixir.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Unit tests for {@link RpcTransport}. Asserts the {@code POST /} envelope
 * and {@code X-Amz-Target} static header for AWS-style RPC protocols.
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
            "structure BrewInput { cup: String }",
            "structure BrewOutput { ready: Boolean }");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(MODEL, "test.rpc#Coffee", "Test.Rpc");
    }

    @Test
    void writeRequestEmitsPostToRoot() {
        OperationShape op = fx.operation("test.rpc#Brew");
        ElixirWriter w = CodegenTestSupport.writer("coffee_client.ex");

        new RpcTransport().writeRequest(w, fx.ctx(), op);

        assertThat(w.toString())
                .contains("http: %{method: \"POST\", uri: \"/\"},");
    }

    @Test
    void writeRequestEmitsXAmzTargetHeader() {
        OperationShape op = fx.operation("test.rpc#Brew");
        ElixirWriter w = CodegenTestSupport.writer("coffee_client.ex");

        new RpcTransport().writeRequest(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("static_headers: [");
        assertThat(out).contains("{\"X-Amz-Target\", \"Coffee.Brew\"}");
    }

    @Test
    void writeResponseInvokesDecodeBodyCallback() {
        OperationShape op = fx.operation("test.rpc#Brew");
        ElixirWriter w = CodegenTestSupport.writer("coffee_client.ex");

        boolean[] called = {false};
        new RpcTransport().writeResponse(w, fx.ctx(), op, () -> {
            called[0] = true;
            w.write("decoding: :stub,");
        });

        assertThat(called[0]).isTrue();
        assertThat(w.toString()).contains("decoding: :stub,");
    }
}
