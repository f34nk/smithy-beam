package io.smithy.beam.core.binding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class PaginationHelperTest {

    private static Model model;
    private static ServiceShape service;
    private static OperationShape listItemsOp;
    private static OperationShape getItemOp;

    @BeforeAll
    static void buildModel() {
        model = Model.assembler()
            .addUnparsedModel("pagination-test.smithy", String.join("\n",
                "$version: \"2\"",
                "namespace com.example",
                "",
                "service PaginatedService {",
                "    version: \"2024-01-01\"",
                "    operations: [ListItems, GetItem]",
                "}",
                "",
                "@paginated(",
                "    inputToken: \"nextToken\"",
                "    outputToken: \"nextToken\"",
                "    pageSize: \"maxResults\"",
                "    items: \"items\"",
                ")",
                "@readonly",
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
                "structure GetItemOutput { result: String }"
            ))
            .assemble()
            .unwrap();

        service    = model.expectShape(ShapeId.from("com.example#PaginatedService"), ServiceShape.class);
        listItemsOp = model.expectShape(ShapeId.from("com.example#ListItems"), OperationShape.class);
        getItemOp   = model.expectShape(ShapeId.from("com.example#GetItem"),   OperationShape.class);
    }

    @Test
    void isPaginatedReturnsTrueForPaginatedOperation() {
        assertThat(PaginationHelper.isPaginated(model, service, listItemsOp)).isTrue();
    }

    @Test
    void isPaginatedReturnsFalseForNonPaginatedOperation() {
        assertThat(PaginationHelper.isPaginated(model, service, getItemOp)).isFalse();
    }

    @Test
    void infoReturnsPresentForPaginatedOperation() {
        Optional<PaginationInfo> info = PaginationHelper.info(model, service, listItemsOp);
        assertThat(info).isPresent();
    }

    @Test
    void infoInputTokenMatchesTrait() {
        PaginationInfo info = PaginationHelper.info(model, service, listItemsOp).orElseThrow();
        assertThat(info.getInputTokenMember().getMemberName()).isEqualTo("nextToken");
    }

    @Test
    void infoOutputTokenMatchesTrait() {
        PaginationInfo info = PaginationHelper.info(model, service, listItemsOp).orElseThrow();
        assertThat(info.getOutputTokenMemberPath())
                .anySatisfy(m -> assertThat(m.getMemberName()).isEqualTo("nextToken"));
    }

    @Test
    void infoPageSizeMemberPresent() {
        PaginationInfo info = PaginationHelper.info(model, service, listItemsOp).orElseThrow();
        assertThat(info.getPageSizeMember()).isPresent();
        assertThat(info.getPageSizeMember().get().getMemberName()).isEqualTo("maxResults");
    }

    @Test
    void infoItemsMemberPresent() {
        PaginationInfo info = PaginationHelper.info(model, service, listItemsOp).orElseThrow();
        assertThat(info.getItemsMemberPath()).isNotEmpty();
        assertThat(info.getItemsMemberPath().get(0).getMemberName()).isEqualTo("items");
    }

    @Test
    void infoReturnsEmptyForNonPaginatedOperation() {
        Optional<PaginationInfo> info = PaginationHelper.info(model, service, getItemOp);
        assertThat(info).isEmpty();
    }
}
