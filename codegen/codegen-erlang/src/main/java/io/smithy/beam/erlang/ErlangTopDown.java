package io.smithy.beam.erlang;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Lists operations bound under a service using {@link TopDownIndex}, sorted
 * deterministically for stable exports and dispatch tables.
 */
final class ErlangTopDown {

    private ErlangTopDown() {}

    static List<OperationShape> containedOperationsSorted(Model model, ServiceShape service) {
        TopDownIndex index = TopDownIndex.of(model);
        Set<OperationShape> contained = index.getContainedOperations(service);
        List<OperationShape> list = new ArrayList<>(contained);
        list.sort(Comparator.comparing(o -> o.getId().toString()));
        return list;
    }
}
