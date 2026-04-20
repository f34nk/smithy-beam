package io.smithy.beam.core.binding;

import java.util.Optional;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.PaginatedIndex;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Thin static facade over {@link PaginatedIndex} for the language-specific
 * codegen modules. No language-specific logic is present here.
 */
public final class PaginationHelper {

    private PaginationHelper() {}

    /**
     * Returns {@code true} when the operation has a {@code @paginated} trait
     * that is resolvable in the context of the given service.
     */
    public static boolean isPaginated(Model model, ServiceShape service, OperationShape op) {
        return PaginatedIndex.of(model).getPaginationInfo(service, op).isPresent();
    }

    /**
     * Returns the resolved {@link PaginationInfo} for the operation, or empty
     * when the operation is not paginated.
     */
    public static Optional<PaginationInfo> info(Model model, ServiceShape service, OperationShape op) {
        return PaginatedIndex.of(model).getPaginationInfo(service, op);
    }
}
