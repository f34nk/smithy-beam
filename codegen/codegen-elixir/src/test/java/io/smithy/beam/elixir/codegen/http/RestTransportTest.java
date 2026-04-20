package io.smithy.beam.elixir.codegen.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.codegen.CodegenTestSupport;
import io.smithy.beam.elixir.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Unit tests for {@link RestTransport}.
 *
 * <p>Verifies the {@code http: %{method:…, uri:…}} and {@code static_headers:}
 * fields the transport emits into the {@code %SmithyClient.Operation{}} struct
 * for REST-style protocols.
 */
class RestTransportTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.rest",
            "",
            "service Svc {",
            "    version: \"2024\"",
            "    operations: [GetItem, ListItems]",
            "}",
            "",
            "@http(method: \"GET\", uri: \"/items/{id}\")",
            "operation GetItem {",
            "    input: GetItemInput",
            "    output: GetItemOutput",
            "}",
            "",
            "@http(method: \"GET\", uri: \"/items\")",
            "operation ListItems {",
            "    input: ListItemsInput",
            "    output: ListItemsOutput",
            "}",
            "",
            "structure GetItemInput {",
            "    @httpLabel @required id: String",
            "    @httpHeader(\"X-Tag\") tag: String",
            "}",
            "",
            "structure GetItemOutput {}",
            "",
            "structure ListItemsInput {}",
            "",
            "structure ListItemsOutput {}");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(MODEL, "test.rest#Svc", "Test.Rest");
    }

    @Test
    void writeRequestEmitsMethodAndInterpolatedUriForLabels() {
        OperationShape op = fx.operation("test.rest#GetItem");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new RestTransport().writeRequest(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("http: %{method: \"GET\", uri: \"/items/#{URI.encode_www_form(id || \"\")}\"},");
    }

    @Test
    void writeRequestEmitsLiteralUriWhenNoLabels() {
        OperationShape op = fx.operation("test.rest#ListItems");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new RestTransport().writeRequest(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("http: %{method: \"GET\", uri: \"/items\"},");
        assertThat(out).contains("static_headers: [],");
    }

    @Test
    void writeRequestEmitsStaticHeadersForHttpHeaderMembers() {
        OperationShape op = fx.operation("test.rest#GetItem");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        new RestTransport().writeRequest(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("static_headers: [");
        assertThat(out).contains("{\"X-Tag\", input.tag}");
    }

    @Test
    void writeResponseInvokesDecodeBodyCallback() {
        OperationShape op = fx.operation("test.rest#GetItem");
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");

        boolean[] called = {false};
        new RestTransport().writeResponse(w, fx.ctx(), op, () -> {
            called[0] = true;
            w.write("decoding: :stub,");
        });

        assertThat(called[0]).isTrue();
        assertThat(w.toString()).contains("decoding: :stub,");
    }
}
