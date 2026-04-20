package io.smithy.beam.core;

import java.util.Map;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.ServiceIndex;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AuthTrait;
import software.amazon.smithy.model.traits.Trait;
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
     * <p>Models produced by {@code Model.assembler()} already have
     * prelude-injected defaults, so this is currently a pass-through.
     */
    public static Model addDefaultErrorMembers(Model model) {
        return model;
    }

    /**
     * Removes all shapes that are not reachable from the given service closure,
     * retaining Smithy prelude shapes.
     *
     * <p>This is equivalent to {@link #removeOutOfClosure} and is provided as
     * an alias for the Phase 2 helper surface. Enable this transform via the
     * {@code removeUnreferencedShapes} setting in {@code smithy-build.json}.
     */
    public static Model removeUnreferencedShapes(Model model, ServiceShape service) {
        return removeOutOfClosure(model, service);
    }

    /**
     * Ensures that every operation in the service carries an explicit
     * {@code @auth} trait resolving to the service's effective auth schemes.
     *
     * <p>Operations that already declare {@code @auth} are left unchanged.
     * Operations without {@code @auth} inherit the service-level effective
     * schemes; this transform makes that implicit resolution explicit so
     * downstream generators can rely on each operation having a non-empty
     * {@code @auth} list.
     */
    public static Model applyAuthSchemes(Model model, ServiceShape service) {
        ServiceIndex serviceIndex = ServiceIndex.of(model);
        Model.Builder builder = model.toBuilder();
        boolean modified = false;

        for (ShapeId operationId : service.getAllOperations()) {
            OperationShape op = model.expectShape(operationId, OperationShape.class);
            if (op.hasTrait(AuthTrait.class)) {
                continue;
            }
            Map<ShapeId, Trait> effectiveSchemes =
                    serviceIndex.getEffectiveAuthSchemes(service, op);
            if (effectiveSchemes.isEmpty()) {
                continue;
            }
            AuthTrait authTrait = new AuthTrait(effectiveSchemes.keySet());
            builder.addShape(op.toBuilder().addTrait(authTrait).build());
            modified = true;
        }

        return modified ? builder.build() : model;
    }
}
