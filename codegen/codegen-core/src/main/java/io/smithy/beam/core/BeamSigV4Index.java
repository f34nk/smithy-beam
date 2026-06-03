package io.smithy.beam.core;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Precomputes SigV4 signing metadata for operations bound under a service.
 */
public final class BeamSigV4Index {

    private final Map<ShapeId, Boolean> operationUnsignedPayload;

    private BeamSigV4Index(Map<ShapeId, Boolean> operationUnsignedPayload) {
        this.operationUnsignedPayload = operationUnsignedPayload;
    }

    public static BeamSigV4Index of(Model model, ServiceShape service) {
        TopDownIndex topDown = TopDownIndex.of(model);
        List<OperationShape> operations = new ArrayList<>(topDown.getContainedOperations(service));
        operations.sort(Comparator.comparing(o -> o.getId().toString()));

        Map<ShapeId, Boolean> flags = new LinkedHashMap<>();
        for (OperationShape operation : operations) {
            flags.put(operation.getId(), BeamSigV4Metadata.operationUsesUnsignedPayload(model, operation));
        }
        return new BeamSigV4Index(Collections.unmodifiableMap(flags));
    }

    public boolean operationUsesUnsignedPayload(OperationShape operation) {
        return operationUnsignedPayload.getOrDefault(operation.getId(), false);
    }

    public List<OperationShape> operationsWithUnsignedPayload(Model model, ServiceShape service) {
        TopDownIndex topDown = TopDownIndex.of(model);
        List<OperationShape> operations = new ArrayList<>(topDown.getContainedOperations(service));
        operations.sort(Comparator.comparing(o -> o.getId().toString()));
        List<OperationShape> unsigned = new ArrayList<>();
        for (OperationShape operation : operations) {
            if (operationUsesUnsignedPayload(operation)) {
                unsigned.add(operation);
            }
        }
        return unsigned;
    }
}
