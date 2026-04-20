package io.smithy.beam.elixir.codegen;

import io.smithy.beam.elixir.codegen.sections.WaiterSection;
import java.util.List;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.waiters.Acceptor;
import software.amazon.smithy.waiters.AcceptorState;
import software.amazon.smithy.waiters.Matcher;
import software.amazon.smithy.waiters.Waiter;

/**
 * Elixir feature integration that emits waiter helpers
 * ({@code wait_<waiter_name>/2} and {@code wait_<waiter_name>/3}) for every
 * operation that carries a {@code @waitable} trait.
 *
 * <p>The integration appends to {@link WaiterSection} — that section is pushed
 * once per named waiter by {@code ElixirClientCodegen.generateService}, so this
 * interceptor never has to gate on the trait itself.
 *
 * <p>The polling loop with exponential backoff is provided by the runtime
 * helper {@code SmithyRetry.wait/3} (see {@code runtime-elixir/client/smithy_retry.ex}).
 * The acceptor evaluator is generated inline as a defp clause that nests one
 * {@code case} per acceptor, mirroring the Erlang integration.
 *
 * <p>Path-based matchers ({@code output} and {@code inputOutput}) require a
 * JMESPath evaluator that is out of scope for Phase 2; for those we emit a
 * TODO comment and treat the acceptor as "no match" so the waiter falls
 * through to the next acceptor (or retries).
 */
public final class ElixirWaitersIntegration implements ElixirIntegration {

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors(
            ElixirContext ctx) {
        return List.of(
                CodeInterceptor.appender(WaiterSection.class, (writer, section) ->
                        emit(writer, ctx, section.operation(), waiterName(section), section.waiter())));
    }

    private static String waiterName(WaiterSection section) {
        return section.operation()
                .expectTrait(software.amazon.smithy.waiters.WaitableTrait.class)
                .getWaiters()
                .entrySet()
                .stream()
                .filter(e -> e.getValue().equals(section.waiter()))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Waiter not found on operation " + section.operation().getId()));
    }

    private static void emit(ElixirWriter w,
                             ElixirContext ctx,
                             OperationShape op,
                             String waiterName,
                             Waiter waiter) {
        w.addDependency(ElixirDependency.SMITHY_RETRY);

        String opFn = CaseUtils.toSnakeCase(op.getId().getName());
        String wn = CaseUtils.toSnakeCase(waiterName);
        String waitFn = "wait_" + wn;
        String evalFn = "evaluate_" + wn + "_acceptors";

        StructureShape inputShape = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        String inputStruct = inputShape.getId().getName();

        emitDoc(w, waiter);
        emitWaitArity2(w, waitFn, inputStruct);
        emitWaitArity3(w, opFn, waitFn, evalFn, inputStruct, waiter);
        emitAcceptorEvaluator(w, evalFn, waiter);
    }

    private static void emitDoc(ElixirWriter w, Waiter waiter) {
        w.write("");
        waiter.getDocumentation().ifPresent(doc -> {
            w.write("@doc \"\"\"");
            w.write("$L", doc);
            w.write("\"\"\"");
        });
    }

    private static void emitWaitArity2(ElixirWriter w, String waitFn, String inputStruct) {
        w.write("@spec $L(map(), $L.t()) :: {:ok, term()} | {:error, term()}", waitFn, inputStruct);
        w.write("def $L(client, %$L{} = input) do", waitFn, inputStruct);
        w.indent();
        w.write("$L(client, input, %{})", waitFn);
        w.dedent();
        w.write("end");
        w.write("");
    }

    private static void emitWaitArity3(ElixirWriter w,
                                       String opFn,
                                       String waitFn,
                                       String evalFn,
                                       String inputStruct,
                                       Waiter waiter) {
        w.write("@spec $L(map(), $L.t(), map()) :: {:ok, term()} | {:error, term()}",
                waitFn, inputStruct);
        w.write("def $L(client, %$L{} = input, opts) do", waitFn, inputStruct);
        w.indent();
        w.write("SmithyRetry.wait(");
        w.indent();
        w.write("fn -> $L($L(client, input)) end,", evalFn, opFn);
        w.write("Map.get(opts, :min_delay, $L),", waiter.getMinDelay());
        w.write("Map.get(opts, :max_delay, $L)", waiter.getMaxDelay());
        w.dedent();
        w.write(")");
        w.dedent();
        w.write("end");
        w.write("");
    }

    private static void emitAcceptorEvaluator(ElixirWriter w, String evalFn, Waiter waiter) {
        w.write("# Evaluates the waiter's acceptors against an operation result.");
        w.write("# Returns {:success | :failure | :retry, term()} as expected by SmithyRetry.wait/3.");
        w.write("defp $L(result) do", evalFn);
        w.indent();
        emitAcceptorChain(w, waiter.getAcceptors(), 0);
        w.dedent();
        w.write("end");
        w.write("");
    }

    /**
     * Emits a chain of nested {@code case} expressions, one per acceptor, in
     * the order the waiter declares them. The first acceptor whose matcher
     * matches the result wins; subsequent acceptors are skipped. If no
     * acceptor matches, the result is {@code {:retry, result}}.
     */
    private static void emitAcceptorChain(ElixirWriter w, List<Acceptor> acceptors, int index) {
        if (index >= acceptors.size()) {
            w.write("{:retry, result}");
            return;
        }
        Acceptor acceptor = acceptors.get(index);
        String pattern = matchPattern(acceptor.getMatcher());
        String stateAtom = stateAtom(acceptor.getState());
        String matcherDoc = matcherDescription(acceptor.getMatcher());

        w.write("# Acceptor $L: state=$L, matcher=$L", index + 1, stateAtom, matcherDoc);
        w.write("case result do");
        w.indent();
        if (pattern != null) {
            w.write("$L ->", pattern);
            w.indent();
            w.write("{$L, result}", stateAtom);
            w.dedent();
            w.write("");
        }
        w.write("_ ->");
        w.indent();
        emitAcceptorChain(w, acceptors, index + 1);
        w.dedent();
        w.dedent();
        w.write("end");
    }

    /**
     * Returns an Elixir pattern that matches the operation result when the
     * given matcher fires, or {@code null} when the matcher kind is not yet
     * supported (path-based matchers fall through to the next acceptor; the
     * caller emits a TODO comment).
     */
    private static String matchPattern(Matcher<?> matcher) {
        if (matcher instanceof Matcher.SuccessMember success) {
            return success.getValue() ? "{:ok, _}" : "{:error, _}";
        }
        if (matcher instanceof Matcher.ErrorTypeMember errorType) {
            return "{:error, %" + errorType.getValue() + "{}}";
        }
        // Path-based matchers (output/inputOutput) require a JMESPath evaluator
        // that lives outside Phase 2 scope. Skip the matching clause; the chain
        // will fall through to {:retry, result}.
        return null;
    }

    private static String matcherDescription(Matcher<?> matcher) {
        if (matcher instanceof Matcher.SuccessMember success) {
            return "success(" + success.getValue() + ")";
        }
        if (matcher instanceof Matcher.ErrorTypeMember errorType) {
            return "errorType(\"" + errorType.getValue() + "\")";
        }
        if (matcher instanceof Matcher.OutputMember out) {
            return "output(path=\"" + out.getValue().getPath()
                    + "\", expected=\"" + out.getValue().getExpected()
                    + "\", comparator=" + out.getValue().getComparator()
                    + ") - TODO: path-based matcher not yet implemented";
        }
        if (matcher instanceof Matcher.InputOutputMember io) {
            return "inputOutput(path=\"" + io.getValue().getPath()
                    + "\", expected=\"" + io.getValue().getExpected()
                    + "\", comparator=" + io.getValue().getComparator()
                    + ") - TODO: path-based matcher not yet implemented";
        }
        return matcher.getMemberName() + " - TODO: matcher kind not yet implemented";
    }

    private static String stateAtom(AcceptorState state) {
        return switch (state) {
            case SUCCESS -> ":success";
            case FAILURE -> ":failure";
            case RETRY -> ":retry";
        };
    }
}
