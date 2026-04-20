package io.smithy.beam.core.binding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.waiters.Waiter;

class WaiterHelperTest {

    private static Model model;
    private static OperationShape waitableOp;
    private static OperationShape noWaiterOp;

    @BeforeAll
    static void buildModel() {
        model = Model.assembler()
            .discoverModels(WaiterHelperTest.class.getClassLoader())
            .addUnparsedModel("waiter-test.smithy", String.join("\n",
                "$version: \"2\"",
                "namespace com.example",
                "",
                "use smithy.waiters#waitable",
                "",
                "service WaiterService {",
                "    version: \"2024-01-01\"",
                "    operations: [DescribeBucket, GetItem]",
                "}",
                "",
                "@waitable(",
                "    BucketExists: {",
                "        acceptors: [",
                "            {",
                "                state: \"success\"",
                "                matcher: {",
                "                    success: true",
                "                }",
                "            }",
                "        ]",
                "    }",
                "    BucketNotExists: {",
                "        acceptors: [",
                "            {",
                "                state: \"success\"",
                "                matcher: {",
                "                    errorType: \"NoSuchBucket\"",
                "                }",
                "            }",
                "        ]",
                "    }",
                ")",
                "operation DescribeBucket {",
                "    input: DescribeBucketInput",
                "    output: DescribeBucketOutput",
                "    errors: [NoSuchBucket]",
                "}",
                "",
                "structure DescribeBucketInput { name: String }",
                "structure DescribeBucketOutput { exists: Boolean }",
                "",
                "@error(\"client\")",
                "structure NoSuchBucket { message: String }",
                "",
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

        waitableOp = model.expectShape(ShapeId.from("com.example#DescribeBucket"), OperationShape.class);
        noWaiterOp = model.expectShape(ShapeId.from("com.example#GetItem"),        OperationShape.class);
    }

    @Test
    void waitersReturnsBothWaitersForWaitableOperation() {
        Map<String, Waiter> waiters = WaiterHelper.waiters(model, waitableOp);
        assertThat(waiters).containsKeys("BucketExists", "BucketNotExists");
    }

    @Test
    void waitersReturnsEmptyMapForOperationWithoutWaitable() {
        Map<String, Waiter> waiters = WaiterHelper.waiters(model, noWaiterOp);
        assertThat(waiters).isEmpty();
    }

    @Test
    void hasWaitersReturnsTrueForWaitableOperation() {
        assertThat(WaiterHelper.hasWaiters(model, waitableOp)).isTrue();
    }

    @Test
    void hasWaitersReturnsFalseForOperationWithoutWaitable() {
        assertThat(WaiterHelper.hasWaiters(model, noWaiterOp)).isFalse();
    }

    @Test
    void waiterAcceptorsArePresent() {
        Waiter bucketExists = WaiterHelper.waiters(model, waitableOp).get("BucketExists");
        assertThat(bucketExists).isNotNull();
        assertThat(bucketExists.getAcceptors()).isNotEmpty();
    }
}
