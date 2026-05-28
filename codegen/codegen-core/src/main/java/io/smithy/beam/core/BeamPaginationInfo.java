package io.smithy.beam.core;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.PaginatedIndex;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.Optional;

/**
 * Wraps {@link PaginatedIndex} for codegen callers.
 */
public final class BeamPaginationInfo {

    private final PaginatedIndex index;

    private BeamPaginationInfo(PaginatedIndex index) {
        this.index = index;
    }

    public static BeamPaginationInfo of(Model model) {
        return new BeamPaginationInfo(PaginatedIndex.of(model));
    }

    public Optional<PaginationInfo> forOperation(ServiceShape service, OperationShape op) {
        return index.getPaginationInfo(service, op);
    }

    public boolean isPaginated(ServiceShape service, OperationShape op) {
        return index.getPaginationInfo(service, op).isPresent();
    }
}
