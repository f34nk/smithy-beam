package io.smithy.beam.core.model;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;

import java.util.Set;

/**
 * Collects shapes reachable from a service (BFS via Smithy {@link Walker}).
 */
public final class ShapeIndex {

    private ShapeIndex() {}

    public static Set<Shape> reachable(ServiceShape service, Model model) {
        return new Walker(model).walkShapes(service);
    }
}
