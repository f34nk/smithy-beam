package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.erlang.codegen.sections.PaginationHelperSection;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Unit tests for {@link ErlangPaginationIntegration}.
 *
 * <p>Drives the integration against a small {@code @paginated} model fixture
 * and asserts that the {@code <op>_stream/3} and {@code <op>_pages/2}
 * helpers — together with the matching {@code SMITHY_PAGINATION} runtime
 * dependency — are emitted by the {@link PaginationHelperSection} interceptor.
 */
class ErlangPaginationIntegrationTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.pagination",
            "",
            "service Svc {",
            "    version: \"2024\"",
            "    operations: [ListItems, GetItem]",
            "}",
            "",
            "@readonly",
            "@paginated(",
            "    inputToken: \"nextToken\"",
            "    outputToken: \"nextToken\"",
            "    pageSize: \"maxResults\"",
            "    items: \"items\"",
            ")",
            "operation ListItems {",
            "    input: ListItemsInput",
            "    output: ListItemsOutput",
            "}",
            "",
            "structure ListItemsInput {",
            "    nextToken: String",
            "    maxResults: Integer",
            "}",
            "",
            "structure ListItemsOutput {",
            "    items: StringList",
            "    nextToken: String",
            "}",
            "",
            "list StringList { member: String }",
            "",
            "@readonly",
            "operation GetItem {",
            "    input: GetItemInput",
            "    output: GetItemOutput",
            "}",
            "",
            "structure GetItemInput { id: String }",
            "structure GetItemOutput { result: String }");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(MODEL, "test.pagination#Svc", "svc");
    }

    @Test
    void emitsPagesAndStreamHelpersForPaginatedOperation() {
        OperationShape op = fx.operation("test.pagination#ListItems");
        ErlangWriter w = drive(op);

        String out = w.toString();

        assertThat(out).contains("list_items_pages(Client, Input) ->");
        assertThat(out).contains("smithy_pagination:pages(");
        assertThat(out).contains("list_items_stream(Client, Input, _Opts) ->");
        assertThat(out).contains("smithy_pagination:stream(");
    }

    @Test
    void emitsTokenAndItemsAccessorsBoundToGeneratedRecords() {
        OperationShape op = fx.operation("test.pagination#ListItems");
        String out = drive(op).toString();

        assertThat(out).contains(
                "set_input_token => fun(I, T) -> I#list_items_input{next_token = T} end");
        assertThat(out).contains(
                "output_token => fun(O) -> O#list_items_output.next_token end");
        assertThat(out).contains(
                "items => fun(O) -> O#list_items_output.items end");
    }

    @Test
    void emittedHelpersAreExportedAndDependencyIsRegistered() {
        OperationShape op = fx.operation("test.pagination#ListItems");
        ErlangWriter w = drive(op);

        assertThat(w.toString())
                .contains("list_items_pages/2")
                .contains("list_items_stream/3");
        assertThat(w.getDependencies())
                .anyMatch(d -> d.getProperty("resourcePath", String.class)
                        .map(p -> p.endsWith("/smithy_pagination.erl"))
                        .orElse(false));
    }

    @Test
    void emitsNothingForNonPaginatedOperation() {
        OperationShape op = fx.operation("test.pagination#GetItem");
        ErlangWriter w = drive(op);

        String out = w.toString();
        assertThat(out).doesNotContain("smithy_pagination:");
        assertThat(out).doesNotContain("get_item_pages");
        assertThat(out).doesNotContain("get_item_stream");
        assertThat(w.getDependencies())
                .noneMatch(d -> d.getProperty("resourcePath", String.class)
                        .map(p -> p.endsWith("/smithy_pagination.erl"))
                        .orElse(false));
    }

    private static ErlangWriter drive(OperationShape op) {
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");
        List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors =
                new ErlangPaginationIntegration().interceptors(fx.ctx());
        for (CodeInterceptor<? extends CodeSection, ErlangWriter> i : interceptors) {
            w.onSection(i);
        }
        w.injectSection(new PaginationHelperSection(op));
        return w;
    }
}
