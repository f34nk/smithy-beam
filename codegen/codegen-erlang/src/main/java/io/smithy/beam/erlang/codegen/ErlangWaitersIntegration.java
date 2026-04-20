package io.smithy.beam.erlang.codegen;

import io.smithy.beam.erlang.codegen.sections.WaiterSection;
import java.util.List;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.waiters.Acceptor;
import software.amazon.smithy.waiters.AcceptorState;
import software.amazon.smithy.waiters.Matcher;
import software.amazon.smithy.waiters.Waiter;

/**
 * Erlang feature integration that emits waiter helpers
 * ({@code wait_<waiter_name>/2} and {@code wait_<waiter_name>/3}) for every
 * operation that carries a {@code @waitable} trait.
 *
 * <p>The integration appends to {@link WaiterSection} — that section is pushed
 * once per named waiter by {@code ErlangClientCodegen.generateService}, so this
 * interceptor never has to gate on the trait itself.
 *
 * <p>The polling loop with exponential backoff is provided by the runtime
 * helper {@code smithy_retry:wait/3} (see {@code runtime-erlang/client/smithy_retry.erl}).
 * The acceptor evaluator is generated inline: each acceptor is emitted as a
 * nested {@code case} clause that maps the operation result to one of
 * {@code {success, _}}, {@code {failure, _}}, or {@code {retry, _}} based on
 * the matcher kind ({@code success}, {@code errorType}, {@code output},
 * {@code inputOutput}). Path-based matchers ({@code output} and
 * {@code inputOutput}) require a JMESPath evaluator that is out of scope for
 * Phase 2; for those we emit a TODO comment and treat the acceptor as
 * "no match" so the waiter falls through to the next acceptor (or retries).
 */
public final class ErlangWaitersIntegration implements ErlangIntegration {

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext ctx) {
        return List.of(
                CodeInterceptor.appender(WaiterSection.class, (writer, section) ->
                        emit(writer, section.operation(), waiterName(section), section.waiter())));
    }

    private static String waiterName(WaiterSection section) {
        // The Smithy `@waitable` trait keys the waiter map by its declared name,
        // but `WaiterSection` only carries the `Waiter` value. The codegen pushes
        // one section per named waiter; the name lives on the section's debug-only
        // identity. Recover it from the trait by matching the Waiter value to its
        // entry in the operation's @waitable trait map.
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

    private static void emit(ErlangWriter w, OperationShape op, String waiterName, Waiter waiter) {
        w.addDependency(ErlangDependency.SMITHY_RETRY);

        String opFn = CaseUtils.toSnakeCase(op.getId().getName());
        String wn = CaseUtils.toSnakeCase(waiterName);
        String waitFn = "wait_" + wn;
        String evalFn = "evaluate_" + wn + "_acceptors";

        emitDoc(w, waiter);
        emitWaitArity2(w, opFn, waitFn);
        emitWaitArity3(w, opFn, waitFn, evalFn, waiter);
        emitAcceptorEvaluator(w, evalFn, waiter);

        w.addExport(waitFn, 2);
        w.addExport(waitFn, 3);
    }

    private static void emitDoc(ErlangWriter w, Waiter waiter) {
        w.write("");
        waiter.getDocumentation().ifPresent(doc -> w.write("%% @doc $L", doc));
    }

    private static void emitWaitArity2(ErlangWriter w, String opFn, String waitFn) {
        w.write("-spec $L(Client :: map(), Input :: $L_input()) -> {ok, term()} | {error, term()}.",
                waitFn, opFn);
        w.write("$L(Client, Input) ->", waitFn);
        w.indent();
        w.write("$L(Client, Input, #{}).", waitFn);
        w.dedent();
        w.write("");
    }

    private static void emitWaitArity3(ErlangWriter w,
                                       String opFn,
                                       String waitFn,
                                       String evalFn,
                                       Waiter waiter) {
        w.write("-spec $L(Client :: map(), Input :: $L_input(), Opts :: map()) -> "
                + "{ok, term()} | {error, term()}.", waitFn, opFn);
        w.write("$L(Client, Input, Opts) ->", waitFn);
        w.indent();
        w.write("smithy_retry:wait(");
        w.indent();
        w.write("fun() -> $L($L(Client, Input)) end,", evalFn, opFn);
        w.write("maps:get(min_delay, Opts, $L),", waiter.getMinDelay());
        w.write("maps:get(max_delay, Opts, $L)", waiter.getMaxDelay());
        w.dedent();
        w.write(").");
        w.dedent();
        w.write("");
    }

    private static void emitAcceptorEvaluator(ErlangWriter w, String evalFn, Waiter waiter) {
        w.write("%% @private");
        w.write("%% Evaluates the waiter's acceptors against an operation result.");
        w.write("%% Returns {success | failure | retry, term()} as expected by smithy_retry:wait/3.");
        w.write("$L(Result) ->", evalFn);
        w.indent();
        emitAcceptorChain(w, waiter.getAcceptors(), 0);
        w.dedent();
        w.write("");
    }

    /**
     * Emits a chain of nested {@code case} expressions, one per acceptor, in
     * the order the waiter declares them. The first acceptor whose matcher
     * matches the result wins; subsequent acceptors are skipped. If no
     * acceptor matches, the result is {@code {retry, Result}}.
     */
    private static void emitAcceptorChain(ErlangWriter w, List<Acceptor> acceptors, int index) {
        if (index >= acceptors.size()) {
            w.write("{retry, Result}");
            return;
        }
        Acceptor acceptor = acceptors.get(index);
        String pattern = matchPattern(acceptor.getMatcher());
        String stateAtom = stateAtom(acceptor.getState());
        String matcherDoc = matcherDescription(acceptor.getMatcher());

        w.write("%% Acceptor $L: state=$L, matcher=$L", index + 1, stateAtom, matcherDoc);
        w.write("case Result of");
        w.indent();
        if (pattern != null) {
            w.write("$L ->", pattern);
            w.indent();
            w.write("{$L, Result};", stateAtom);
            w.dedent();
        }
        w.write("_ ->");
        w.indent();
        emitAcceptorChain(w, acceptors, index + 1);
        w.dedent();
        w.dedent();
        w.write("end");
    }

    /**
     * Returns an Erlang pattern that matches the operation result when the
     * given matcher fires, or {@code null} when the matcher kind is not yet
     * supported (path-based matchers fall through to the next acceptor; the
     * caller emits a TODO comment).
     */
    private static String matchPattern(Matcher<?> matcher) {
        if (matcher instanceof Matcher.SuccessMember success) {
            return success.getValue() ? "{ok, _}" : "{error, _}";
        }
        if (matcher instanceof Matcher.ErrorTypeMember errorType) {
            String recordName = CaseUtils.toSnakeCase(errorType.getValue());
            return "{error, #" + recordName + "{}}";
        }
        // Path-based matchers (output/inputOutput) require a JMESPath evaluator
        // that lives outside Phase 2 scope. Skip the matching clause; the chain
        // will fall through to {retry, Result}.
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
            case SUCCESS -> "success";
            case FAILURE -> "failure";
            case RETRY -> "retry";
        };
    }
}
