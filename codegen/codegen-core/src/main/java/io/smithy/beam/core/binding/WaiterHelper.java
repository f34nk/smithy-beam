package io.smithy.beam.core.binding;

import java.util.Collections;
import java.util.Map;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.waiters.Waiter;
import software.amazon.smithy.waiters.WaitableTrait;

/**
 * Thin static facade over {@link WaitableTrait} for the language-specific
 * codegen modules. No language-specific logic is present here.
 */
public final class WaiterHelper {

    private WaiterHelper() {}

    /**
     * Returns all named {@link Waiter}s defined on the operation via the
     * {@code @waitable} trait, keyed by waiter name.
     *
     * <p>Returns an empty map when the operation has no {@code @waitable} trait.
     */
    public static Map<String, Waiter> waiters(Model model, OperationShape op) {
        return op.getTrait(WaitableTrait.class)
                .map(WaitableTrait::getWaiters)
                .map(Collections::unmodifiableMap)
                .orElse(Collections.emptyMap());
    }

    /**
     * Returns {@code true} when the operation has at least one waiter defined
     * via the {@code @waitable} trait.
     */
    public static boolean hasWaiters(Model model, OperationShape op) {
        return op.hasTrait(WaitableTrait.class)
                && !op.expectTrait(WaitableTrait.class).getWaiters().isEmpty();
    }
}
