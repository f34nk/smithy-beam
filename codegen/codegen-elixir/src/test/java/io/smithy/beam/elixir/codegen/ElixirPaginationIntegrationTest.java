package io.smithy.beam.elixir.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.elixir.codegen.sections.PaginationHelperSection;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Unit tests for {@link ElixirPaginationIntegration}.
 *
 * <p>Drives the integration against a small {@code @paginated} model fixture
 * and asserts that the {@code <op>_stream/3} helper — together with the
 * {@code SMITHY_CLIENT} runtime dependency — is emitted by the
 * {@link PaginationHelperSection} interceptor.
 */
class ElixirPaginationIntegrationTest {

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
    void emitsStreamHelperForPaginatedOperation() {
        OperationShape op = fx.operation("test.pagination#ListItems");
        ElixirWriter w = drive(op);

        String out = w.toString();

        assertThat(out)
                .contains("@spec list_items_stream(map(), ListItemsInput.t(), map()) :: Enumerable.t()")
                .contains("def list_items_stream(client, %ListItemsInput{} = input, opts \\\\ %{}) do")
                .contains("SmithyClient.stream(client, list_items_op(input), opts)")
                .contains("end");
    }

    @Test
    void registersSmithyClientRuntimeDependency() {
        OperationShape op = fx.operation("test.pagination#ListItems");
        ElixirWriter w = drive(op);

        assertThat(w.getDependencies())
                .anyMatch(d -> d.getProperty("resourcePath", String.class)
                        .map(p -> p.endsWith("/smithy_client.ex"))
                        .orElse(false));
    }

    @Test
    void emitsNothingForNonPaginatedOperation() {
        OperationShape op = fx.operation("test.pagination#GetItem");
        ElixirWriter w = drive(op);

        String out = w.toString();
        assertThat(out).doesNotContain("SmithyClient.stream");
        assertThat(out).doesNotContain("get_item_stream");
        assertThat(w.getDependencies())
                .noneMatch(d -> d.getProperty("resourcePath", String.class)
                        .map(p -> p.endsWith("/smithy_client.ex"))
                        .orElse(false));
    }

    private static ElixirWriter drive(OperationShape op) {
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");
        List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors =
                new ElixirPaginationIntegration().interceptors(fx.ctx());
        for (CodeInterceptor<? extends CodeSection, ElixirWriter> i : interceptors) {
            w.onSection(i);
        }
        w.injectSection(new PaginationHelperSection(op));
        return w;
    }
}
