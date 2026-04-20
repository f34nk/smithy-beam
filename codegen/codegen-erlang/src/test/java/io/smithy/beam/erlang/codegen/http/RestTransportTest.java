package io.smithy.beam.erlang.codegen.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.codegen.CodegenTestSupport;
import io.smithy.beam.erlang.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Unit tests for {@link RestTransport}.
 *
 * <p>Verifies the {@code Method = …}, {@code Uri = …}, {@code Headers = …}
 * bindings emitted from {@code @http} / {@code @httpLabel} / {@code @httpQuery}
 * / {@code @httpHeader} traits, as well as the {@code smithy_sigv4:sign_request}
 * dispatch envelope produced by {@code writeResponse}.
 */
class RestTransportTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.rest",
            "",
            "service Svc {",
            "    version: \"2024\"",
            "    operations: [GetItem, ListItems, NoBindings]",
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
            "@http(method: \"POST\", uri: \"/items\")",
            "operation NoBindings {",
            "    input: NoBindingsInput",
            "    output: NoBindingsOutput",
            "}",
            "",
            "structure GetItemInput {",
            "    @httpLabel @required id: String",
            "    @httpHeader(\"X-Tag\") tag: String",
            "}",
            "",
            "structure GetItemOutput {}",
            "",
            "structure ListItemsInput {",
            "    @httpQuery(\"limit\") limit: Integer",
            "    @httpQuery(\"cursor\") cursor: String",
            "}",
            "",
            "structure ListItemsOutput {}",
            "",
            "structure NoBindingsInput {",
            "    body: String",
            "}",
            "",
            "structure NoBindingsOutput {}");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(MODEL, "test.rest#Svc", "svc");
    }

    @Test
    void writeRequestEmitsMethodAndUriWithLabelInterpolation() {
        OperationShape op = fx.operation("test.rest#GetItem");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new RestTransport().writeRequest(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("Method = <<\"GET\">>,");
        assertThat(out).contains("Uri0 = <<\"/items/{id}\">>,");
        assertThat(out).contains("binary:replace(Uri0, <<\"{id}\">>");
        assertThat(out).contains("Url = <<Endpoint/binary, Uri/binary, QueryString/binary>>,");
    }

    @Test
    void writeRequestEmitsHeaderCaseClauseForHttpHeaderMember() {
        OperationShape op = fx.operation("test.rest#GetItem");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new RestTransport().writeRequest(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("Headers0 = [{<<\"Content-Type\">>, <<\"application/json\">>}],");
        assertThat(out).contains("Headers1 = case Input#get_item_input.tag of");
        assertThat(out).contains("<<\"X-Tag\">>");
        assertThat(out).contains("Headers = Headers1,");
    }

    @Test
    void writeRequestEmitsQueryStringForHttpQueryMembers() {
        OperationShape op = fx.operation("test.rest#ListItems");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new RestTransport().writeRequest(w, fx.ctx(), op);

        String out = w.toString();
        assertThat(out).contains("QsParams = [");
        assertThat(out).contains("<<\"limit\">>");
        assertThat(out).contains("<<\"cursor\">>");
        assertThat(out).contains("uri_string:compose_query(QsFiltered)");
    }

    @Test
    void writeRequestUsesEmptyQueryStringWhenNoHttpQueryMembers() {
        OperationShape op = fx.operation("test.rest#NoBindings");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        new RestTransport().writeRequest(w, fx.ctx(), op);

        assertThat(w.toString()).contains("QueryString = <<>>,");
    }

    @Test
    void writeResponseEmitsSigV4DispatchEnvelopeAndInvokesCallback() {
        OperationShape op = fx.operation("test.rest#GetItem");
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");

        boolean[] called = {false};
        new RestTransport().writeResponse(w, fx.ctx(), op, () -> {
            called[0] = true;
            w.write("decode_body_emitted_here,");
        });

        String out = w.toString();
        assertThat(called[0]).isTrue();
        assertThat(out).contains("smithy_sigv4:sign_request(Method, Url, Headers, Body, Client)");
        assertThat(out).contains("httpc:request(");
        assertThat(out).contains("decode_body_emitted_here,");
        assertThat(out).contains("parse_error(ErrStatusCode, ErrorBody);");
        assertThat(out).contains("end.");
    }
}
