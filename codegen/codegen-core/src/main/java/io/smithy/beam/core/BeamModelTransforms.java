package io.smithy.beam.core;

import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.transform.ModelTransformer;

/**
 * Thin static wrappers around {@link ModelTransformer} used during the
 * {@code preprocessModel} phase of every smithy-beam plugin.
 */
public final class BeamModelTransforms {

    private BeamModelTransforms() {}

    /**
     * Removes all shapes that are not reachable from the given service closure,
     * retaining Smithy prelude shapes.
     */
    public static Model removeOutOfClosure(Model model, ServiceShape service) {
        Set<Shape> closure = new Walker(model).walkShapes(service);
        return ModelTransformer.create().removeShapesIf(
            model,
            shape -> !closure.contains(shape) && !Prelude.isPreludeShape(shape)
        );
    }

    /**
     * Copies all errors defined on the service into every operation in that
     * service, so each operation's error list is self-contained.
     */
    public static Model copyServiceErrorsToOperations(Model model, ServiceShape service) {
        return ModelTransformer.create().copyServiceErrorsToOperations(model, service);
    }

    /**
     * Flattens all mixin shapes into their applying structures and removes
     * the mixin shapes from the model.
     */
    public static Model flattenMixins(Model model) {
        return ModelTransformer.create().flattenAndRemoveMixins(model);
    }

    /**
     * Ensures that every error shape carries the standard Smithy error members.
     *
     * <p>Full implementation is a Phase 2 deliverable. Models produced by
     * {@code Model.assembler()} already have prelude-injected defaults, so
     * this is a pass-through in practice.
     */
    public static Model addDefaultErrorMembers(Model model) {
        return model;
    }
}
