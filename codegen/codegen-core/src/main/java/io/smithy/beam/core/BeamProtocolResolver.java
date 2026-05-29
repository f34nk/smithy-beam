package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.ProtocolDefinitionTrait;
import software.amazon.smithy.model.traits.StreamingTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Selects the protocol trait {@link ShapeId} used for codegen for a single service.
 */
public final class BeamProtocolResolver {

    private BeamProtocolResolver() {}

    /**
     * Returns the sole protocol trait on the service, or empty when the service
     * declares none. Used by client and server plugins to decide wire emission.
     */
    public static Optional<ShapeId> resolveServiceProtocol(Model model, ServiceShape service) {
        List<ShapeId> traits = findProtocolTraitIds(model, service);
        if (traits.isEmpty()) {
            return Optional.empty();
        }
        if (traits.size() > 1) {
            throw new CodegenException(
                    "Service "
                            + service.getId()
                            + " declares multiple protocol traits: "
                            + traits
                            + ". Attach exactly one protocol trait to the service.");
        }
        return Optional.of(traits.get(0));
    }

    /**
     * Walks the service closure and fails when shapes are unsupported by the given protocol.
     *
     * <p>When unsupported shapes are found, throws {@link CodegenException} with one message
     * listing every offending shape id.
     */
    public static void assertClosureSupported(Model model, ServiceShape service, ShapeId protocol) {
        Walker walker = new Walker(model);
        Set<Shape> closure = walker.walkShapes(service);

        List<String> diagnostics = new ArrayList<>();
        for (Shape shape : closure) {
            if (shape.hasTrait(StreamingTrait.class) && shape instanceof BlobShape) {
                diagnostics.add(
                        shape.getId()
                                + ": streaming blob shapes are not supported by "
                                + protocol
                                + "; wire streaming is not implemented");
            }
            if (shape instanceof BigDecimalShape) {
                diagnostics.add(
                        shape.getId()
                                + ": bigDecimal is not supported by "
                                + protocol
                                + " without an explicit opt-in codec");
            }
            if (shape instanceof UnionShape && shape.hasTrait(StreamingTrait.class)) {
                diagnostics.add(
                        shape.getId()
                                + ": event-stream union shapes are not supported by "
                                + protocol
                                + "; event streams are not implemented");
            }
        }

        if (!diagnostics.isEmpty()) {
            throw new CodegenException(
                    "Service closure for "
                            + service.getId()
                            + " contains "
                            + diagnostics.size()
                            + " unsupported shape(s) for protocol "
                            + protocol
                            + ":\n"
                            + String.join("\n", diagnostics));
        }
    }

    private static List<ShapeId> findProtocolTraitIds(Model model, ServiceShape service) {
        List<ShapeId> result = new ArrayList<>();
        for (ShapeId traitId : service.getAllTraits().keySet()) {
            Optional<Shape> def = model.getShape(traitId);
            if (def.isPresent() && def.get().hasTrait(ProtocolDefinitionTrait.ID)) {
                result.add(traitId);
            }
        }
        return result;
    }
}
