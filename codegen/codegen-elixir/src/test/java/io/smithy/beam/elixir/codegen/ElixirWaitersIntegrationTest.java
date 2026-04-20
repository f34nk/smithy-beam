package io.smithy.beam.elixir.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.binding.WaiterHelper;
import io.smithy.beam.elixir.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.elixir.codegen.sections.WaiterSection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.waiters.Waiter;

/**
 * Unit tests for {@link ElixirWaitersIntegration}.
 *
 * <p>Drives the integration against a small {@code @waitable} model fixture
 * and asserts that the {@code wait_<name>/2}, {@code wait_<name>/3}, and
 * {@code evaluate_<name>_acceptors/1} helpers — together with the matching
 * {@code SMITHY_RETRY} runtime dependency — are emitted by the
 * {@link WaiterSection} interceptor.
 */
class ElixirWaitersIntegrationTest {

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
        fx = CodegenTestSupport.fixture(MODEL, "test.waiters#Svc", "Svc");
    }

    @Test
    void emitsWaitArity2AndArity3PerWaiter() {
        OperationShape op = fx.operation("test.waiters#DescribeBucket");
        ElixirWriter w = drive(op);

        String out = w.toString();

        assertThat(out)
                .contains("def wait_bucket_exists(client, %DescribeBucketInput{} = input) do")
                .contains("wait_bucket_exists(client, input, %{})")
                .contains("def wait_bucket_exists(client, %DescribeBucketInput{} = input, opts) do")
                .contains("def wait_bucket_not_exists(client, %DescribeBucketInput{} = input) do")
                .contains("def wait_bucket_not_exists(client, %DescribeBucketInput{} = input, opts) do");
    }

    @Test
    void delegatesPollingLoopToSmithyRetryWait3() {
        OperationShape op = fx.operation("test.waiters#DescribeBucket");
        String out = drive(op).toString();

        assertThat(out)
                .contains("SmithyRetry.wait(")
                .contains("fn -> evaluate_bucket_exists_acceptors(describe_bucket(client, input)) end,")
                .contains("Map.get(opts, :min_delay, 5),")
                .contains("Map.get(opts, :max_delay, 60)");
    }

    @Test
    void emitsAcceptorEvaluatorWithSuccessAndErrorTypeMatchers() {
        OperationShape op = fx.operation("test.waiters#DescribeBucket");
        String out = drive(op).toString();

        assertThat(out)
                .contains("defp evaluate_bucket_exists_acceptors(result) do")
                .contains("Acceptor 1: state=:success, matcher=success(true)")
                .contains("{:ok, _} ->")
                .contains("{:success, result}")
                .contains("Acceptor 2: state=:retry, matcher=errorType(\"NoSuchBucket\")")
                .contains("{:error, %NoSuchBucket{}} ->")
                .contains("{:retry, result}");
    }

    @Test
    void emitsSpecAnnotationsAndRegistersSmithyRetryDependency() {
        OperationShape op = fx.operation("test.waiters#DescribeBucket");
        ElixirWriter w = drive(op);

        assertThat(w.toString())
                .contains("@spec wait_bucket_exists(map(), DescribeBucketInput.t()) :: "
                        + "{:ok, term()} | {:error, term()}")
                .contains("@spec wait_bucket_exists(map(), DescribeBucketInput.t(), map()) :: "
                        + "{:ok, term()} | {:error, term()}");
        assertThat(w.getDependencies())
                .anyMatch(d -> d.getProperty("resourcePath", String.class)
                        .map(p -> p.endsWith("/smithy_retry.ex"))
                        .orElse(false));
    }

    @Test
    void emitsNothingForOperationsWithoutWaitableTrait() {
        OperationShape op = fx.operation("test.waiters#GetItem");
        ElixirWriter w = drive(op);

        String out = w.toString();
        assertThat(out)
                .doesNotContain("wait_")
                .doesNotContain("SmithyRetry.wait(");
        assertThat(w.getDependencies())
                .noneMatch(d -> d.getProperty("resourcePath", String.class)
                        .map(p -> p.endsWith("/smithy_retry.ex"))
                        .orElse(false));
    }

    /**
     * Drives every waiter section the operation declares through a fresh writer
     * with the integration's interceptor attached. Mirrors the per-section push
     * that {@code ElixirClientCodegen.generateService} performs.
     */
    private static ElixirWriter drive(OperationShape op) {
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");
        List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors =
                new ElixirWaitersIntegration().interceptors(fx.ctx());
        for (CodeInterceptor<? extends CodeSection, ElixirWriter> i : interceptors) {
            w.onSection(i);
        }
        Map<String, Waiter> waiters = WaiterHelper.waiters(fx.ctx().model(), op);
        if (waiters.isEmpty()) {
            return w;
        }
        waiters.forEach((name, waiter) -> w.injectSection(new WaiterSection(op, waiter)));
        return w;
    }
}
