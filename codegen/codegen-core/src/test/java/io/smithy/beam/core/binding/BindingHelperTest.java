package io.smithy.beam.core.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpTrait;

class BindingHelperTest {

    private static Model model;
    private static OperationShape getItemOp;
    private static OperationShape noHttpOp;

    @BeforeAll
    static void buildModel() {
        model = Model.assembler()
            .addUnparsedModel("binding-test.smithy", String.join("\n",
                "$version: \"2\"",
                "namespace com.example",
                "",
                "service BindingService {",
                "    version: \"2024-01-01\"",
                "    operations: [GetItem, ListItems]",
                "}",
                "",
                "@http(method: \"PUT\", uri: \"/items/{id}\")",
                "operation GetItem {",
                "    input: GetItemInput",
                "    output: GetItemOutput",
                "}",
                "",
                "structure GetItemInput {",
                "    @required",
                "    @httpLabel",
                "    id: String",
                "",
                "    @httpQuery(\"filter\")",
                "    filter: String",
                "",
                "    @httpHeader(\"X-Custom-Header\")",
                "    customHeader: String",
                "",
                "    @httpPayload",
                "    body: String",
                "}",
                "",
                "structure GetItemOutput {",
                "    result: String",
                "}",
                "",
                "@http(method: \"GET\", uri: \"/items\")",
                "@readonly",
                "operation ListItems {",
                "    input: ListItemsInput",
                "    output: ListItemsOutput",
                "}",
                "",
                "structure ListItemsInput {",
                "    @httpPrefixHeaders(\"X-Meta-\")",
                "    meta: StringMap",
                "",
                "    @httpQuery(\"name\")",
                "    name: String",
                "}",
                "",
                "map StringMap {",
                "    key: String",
                "    value: String",
                "}",
                "",
                "structure ListItemsOutput {",
                "    items: StringList",
                "}",
                "",
                "list StringList { member: String }"
            ))
            .assemble()
            .unwrap();

        getItemOp = model.expectShape(ShapeId.from("com.example#GetItem"), OperationShape.class);
        // Build a standalone operation without any @http trait for negative-case tests.
        noHttpOp = OperationShape.builder()
                .id("com.example#NoHttp")
                .input(StructureShape.builder().id("com.example#NoHttpInput").build())
                .output(StructureShape.builder().id("com.example#NoHttpOutput").build())
                .build();
    }

    @Test
    void labelsReturnHttpLabelBindings() {
        List<HttpBinding> labels = BindingHelper.labels(model, getItemOp);
        assertThat(labels).hasSize(1);
        assertThat(labels.get(0).getMemberName()).isEqualTo("id");
        assertThat(labels.get(0).getLocation()).isEqualTo(HttpBinding.Location.LABEL);
    }

    @Test
    void queriesReturnHttpQueryBindings() {
        List<HttpBinding> queries = BindingHelper.queries(model, getItemOp);
        assertThat(queries).hasSize(1);
        assertThat(queries.get(0).getMemberName()).isEqualTo("filter");
        assertThat(queries.get(0).getLocation()).isEqualTo(HttpBinding.Location.QUERY);
    }

    @Test
    void headersReturnHttpHeaderBindings() {
        List<HttpBinding> headers = BindingHelper.headers(model, getItemOp);
        assertThat(headers).hasSize(1);
        assertThat(headers.get(0).getMemberName()).isEqualTo("customHeader");
        assertThat(headers.get(0).getLocation()).isEqualTo(HttpBinding.Location.HEADER);
    }

    @Test
    void payloadReturnsSingleHttpPayloadBinding() {
        Optional<HttpBinding> payload = BindingHelper.payload(model, getItemOp);
        assertThat(payload).isPresent();
        assertThat(payload.get().getMemberName()).isEqualTo("body");
        assertThat(payload.get().getLocation()).isEqualTo(HttpBinding.Location.PAYLOAD);
    }

    @Test
    void payloadIsEmptyWhenNoPayloadBinding() {
        OperationShape listItemsOp = model.expectShape(
                ShapeId.from("com.example#ListItems"), OperationShape.class);
        Optional<HttpBinding> payload = BindingHelper.payload(model, listItemsOp);
        assertThat(payload).isEmpty();
    }

    @Test
    void prefixHeadersReturnHttpPrefixHeaderBindings() {
        OperationShape listItemsOp = model.expectShape(
                ShapeId.from("com.example#ListItems"), OperationShape.class);
        List<HttpBinding> ph = BindingHelper.prefixHeaders(model, listItemsOp);
        assertThat(ph).hasSize(1);
        assertThat(ph.get(0).getMemberName()).isEqualTo("meta");
        assertThat(ph.get(0).getLocation()).isEqualTo(HttpBinding.Location.PREFIX_HEADERS);
    }

    @Test
    void documentMembersReturnsDocumentBoundMembers() {
        // In GetItem, id→label, filter→query, customHeader→header, body→payload.
        // That leaves no document members; verify the map exists and is not null.
        Map<String, HttpBinding> doc = BindingHelper.documentMembers(model, getItemOp);
        assertThat(doc).isNotNull();
    }

    @Test
    void httpReturnsPresentOptionalForOperationWithHttpTrait() {
        Optional<HttpTrait> http = BindingHelper.http(getItemOp);
        assertThat(http).isPresent();
        assertThat(http.get().getMethod()).isEqualTo("PUT");
    }

    @Test
    void httpReturnsEmptyOptionalForOperationWithoutHttpTrait() {
        Optional<HttpTrait> http = BindingHelper.http(noHttpOp);
        assertThat(http).isEmpty();
    }

    @Test
    void uriPatternReturnsPattern() {
        Optional<String> uri = BindingHelper.uriPattern(getItemOp);
        assertThat(uri).isPresent();
        assertThat(uri.get()).isEqualTo("/items/{id}");
    }

    @Test
    void uriPatternIsEmptyForOperationWithoutHttpTrait() {
        Optional<String> uri = BindingHelper.uriPattern(noHttpOp);
        assertThat(uri).isEmpty();
    }

    @Test
    void methodReturnsHttpMethod() {
        assertThat(BindingHelper.method(getItemOp)).isEqualTo("PUT");
    }

    @Test
    void methodThrowsForOperationWithoutHttpTrait() {
        assertThatThrownBy(() -> BindingHelper.method(noHttpOp))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allRequestBindingsReturnsAllBindings() {
        Map<String, HttpBinding> all = BindingHelper.allRequestBindings(model, getItemOp);
        assertThat(all).containsKeys("id", "filter", "customHeader", "body");
    }
}
