package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.ProtocolDefinitionTrait;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Selects the protocol trait {@link ShapeId} used for codegen for a single service.
 */
public final class BeamProtocolResolver {

    private BeamProtocolResolver() {}

    /**
     * Returns the chosen protocol trait id.
     *
     * <p>If {@link BeamSettings#protocol()} is set, it must match one of the protocol
     * traits present on the service. If it is not set, exactly one protocol trait on
     * the service must exist, otherwise this method throws {@link CodegenException}.
     */
    public static ShapeId resolve(Model model, ServiceShape service, BeamSettings settings) {
        List<ShapeId> protocolTraits = findProtocolTraitIds(model, service);
        ShapeId explicit = settings.protocol();
        if (explicit != null) {
            if (!protocolTraits.contains(explicit)) {
                throw new CodegenException(
                        "Configured protocol \""
                                + explicit
                                + "\" is not attached to service "
                                + service.getId()
                                + ". Attached protocol traits: "
                                + protocolTraits);
            }
            return explicit;
        }
        if (protocolTraits.isEmpty()) {
            throw new CodegenException(
                    "No protocol trait found on service "
                            + service.getId()
                            + ". Add a supported protocol trait to the service or set \"protocol\" in the plugin settings.");
        }
        if (protocolTraits.size() > 1) {
            throw new CodegenException(
                    "Multiple protocol traits found on service "
                            + service.getId()
                            + ": "
                            + protocolTraits
                            + ". Set \"protocol\" in the plugin settings to select one.");
        }
        return protocolTraits.get(0);
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
