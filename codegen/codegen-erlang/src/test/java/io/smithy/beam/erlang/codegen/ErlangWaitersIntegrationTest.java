package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.binding.WaiterHelper;
import io.smithy.beam.erlang.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.erlang.codegen.sections.WaiterSection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.waiters.Waiter;

/**
 * Unit tests for {@link ErlangWaitersIntegration}.
 *
 * <p>Drives the integration against a small {@code @waitable} model fixture
 * and asserts that the {@code wait_<name>/2}, {@code wait_<name>/3}, and
 * {@code evaluate_<name>_acceptors/1} helpers — together with the matching
 * {@code SMITHY_RETRY} runtime dependency — are emitted by the
 * {@link WaiterSection} interceptor.
 */
class ErlangWaitersIntegrationTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.waiters",
            "",
            "use smithy.waiters#waitable",
            "",
            "service Svc {",
            "    version: \"2024\"",
            "    operations: [DescribeBucket, GetItem]",
            "}",
            "",
            "@waitable(",
            "    BucketExists: {",
            "        acceptors: [",
            "            { state: \"success\", matcher: { success: true } }",
            "            { state: \"retry\",   matcher: { errorType: \"NoSuchBucket\" } }",
            "        ]",
            "        minDelay: 5",
            "        maxDelay: 60",
            "    }",
            "    BucketNotExists: {",
            "        acceptors: [",
            "            { state: \"success\", matcher: { errorType: \"NoSuchBucket\" } }",
            "            { state: \"failure\", matcher: { success: true } }",
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
        fx = CodegenTestSupport.fixture(MODEL, "test.waiters#Svc", "svc");
    }

    @Test
    void emitsWaitArity2AndArity3PerWaiter() {
        OperationShape op = fx.operation("test.waiters#DescribeBucket");
        ErlangWriter w = drive(op);

        String out = w.toString();

        assertThat(out)
                .contains("wait_bucket_exists(Client, Input) ->")
                .contains("wait_bucket_exists(Client, Input, #{}).")
                .contains("wait_bucket_exists(Client, Input, Opts) ->")
                .contains("wait_bucket_not_exists(Client, Input) ->")
                .contains("wait_bucket_not_exists(Client, Input, Opts) ->");
    }

    @Test
    void delegatesPollingLoopToSmithyRetryWait3() {
        OperationShape op = fx.operation("test.waiters#DescribeBucket");
        String out = drive(op).toString();

        assertThat(out)
                .contains("smithy_retry:wait(")
                .contains("fun() -> evaluate_bucket_exists_acceptors(describe_bucket(Client, Input)) end,")
                .contains("maps:get(min_delay, Opts, 5),")
                .contains("maps:get(max_delay, Opts, 60)");
    }

    @Test
    void emitsAcceptorEvaluatorWithSuccessAndErrorTypeMatchers() {
        OperationShape op = fx.operation("test.waiters#DescribeBucket");
        String out = drive(op).toString();

        assertThat(out)
                .contains("evaluate_bucket_exists_acceptors(Result) ->")
                .contains("Acceptor 1: state=success, matcher=success(true)")
                .contains("{ok, _} ->")
                .contains("{success, Result};")
                .contains("Acceptor 2: state=retry, matcher=errorType(\"NoSuchBucket\")")
                .contains("{error, #no_such_bucket{}} ->")
                .contains("{retry, Result};")
                .contains("{retry, Result}");
    }

    @Test
    void exportsWaitFunctionsAndRegistersSmithyRetryDependency() {
        OperationShape op = fx.operation("test.waiters#DescribeBucket");
        ErlangWriter w = drive(op);

        assertThat(w.toString())
                .contains("wait_bucket_exists/2")
                .contains("wait_bucket_exists/3")
                .contains("wait_bucket_not_exists/2")
                .contains("wait_bucket_not_exists/3");
        assertThat(w.getDependencies())
                .anyMatch(d -> d.getProperty("resourcePath", String.class)
                        .map(p -> p.endsWith("/smithy_retry.erl"))
                        .orElse(false));
    }

    @Test
    void emitsNothingForOperationsWithoutWaitableTrait() {
        OperationShape op = fx.operation("test.waiters#GetItem");
        ErlangWriter w = drive(op);

        String out = w.toString();
        assertThat(out)
                .doesNotContain("wait_")
                .doesNotContain("smithy_retry:wait(");
        assertThat(w.getDependencies())
                .noneMatch(d -> d.getProperty("resourcePath", String.class)
                        .map(p -> p.endsWith("/smithy_retry.erl"))
                        .orElse(false));
    }

    /**
     * Drives every waiter section the operation declares through a fresh writer
     * with the integration's interceptor attached. Mirrors the per-section push
     * that {@code ErlangClientCodegen.generateService} performs.
     */
    private static ErlangWriter drive(OperationShape op) {
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");
        List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors =
                new ErlangWaitersIntegration().interceptors(fx.ctx());
        for (CodeInterceptor<? extends CodeSection, ErlangWriter> i : interceptors) {
            w.onSection(i);
        }
        Map<String, Waiter> waiters = WaiterHelper.waiters(fx.ctx().model(), op);
        if (waiters.isEmpty()) {
            // Mirror the codegen: no waiters → no section pushed.
            return w;
        }
        waiters.forEach((name, waiter) -> w.injectSection(new WaiterSection(op, waiter)));
        return w;
    }
}
