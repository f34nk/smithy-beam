package io.smithy.beam.core;

import java.util.List;
import java.util.Set;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.transform.ModelTransformer;

/**
 * Thin wrappers around {@link ModelTransformer} for transforms shared across
 * all smithy-beam language backends.
 */
public final class BeamModelTransforms {

    private BeamModelTransforms() {}

    /**
     * Removes all shapes not reachable in the transitive closure of {@code service}.
     */
    public static Model removeOutOfClosure(Model model, ServiceShape service) {
        Set<Shape> closure = new Walker(model).walkShapes(service);
        return ModelTransformer.create().removeShapesIf(model, shape -> !closure.contains(shape));
    }

    /**
     * Copies service-level errors onto every operation in the service that
     * does not already declare them.
     */
    public static Model copyServiceErrorsToOperations(Model model, ServiceShape service) {
        List<ShapeId> serviceErrors = service.getErrors();
        if (serviceErrors.isEmpty()) {
            return model;
        }
        return ModelTransformer.create().mapShapes(model, shape -> {
            if (!(shape instanceof OperationShape)) {
                return shape;
            }
            OperationShape operation = (OperationShape) shape;
            if (!service.getAllOperations().contains(operation.getId())) {
                return shape;
            }
            OperationShape.Builder builder = operation.toBuilder();
            for (ShapeId errorId : serviceErrors) {
                if (!operation.getErrors().contains(errorId)) {
                    builder.addError(errorId);
                }
            }
            return builder.build();
        });
    }

    /**
     * Flattens all mixin shapes in the model and removes the mixin definitions.
     */
    public static Model flattenMixins(Model model) {
        return ModelTransformer.create().flattenAndRemoveMixins(model);
    }

    /**
     * Adds default {@code message} and {@code code} members to error structures
     * that do not already declare them.
     */
    public static Model addDefaultErrorMembers(Model model) {
        return ModelTransformer.create().mapShapes(model, shape -> {
            if (!shape.isStructureShape() || !shape.hasTrait(ErrorTrait.class)) {
                return shape;
            }
            StructureShape struct = (StructureShape) shape;
            StructureShape.Builder builder = struct.toBuilder();
            boolean modified = false;

            if (!struct.getMember("message").isPresent()) {
                builder.addMember(MemberShape.builder()
                        .id(struct.getId().withMember("message"))
                        .target(ShapeId.from("smithy.api#String"))
                        .build());
                modified = true;
            }
            if (!struct.getMember("code").isPresent()) {
                builder.addMember(MemberShape.builder()
                        .id(struct.getId().withMember("code"))
                        .target(ShapeId.from("smithy.api#String"))
                        .build());
                modified = true;
            }
            return modified ? builder.build() : struct;
        });
    }
}
