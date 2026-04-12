package io.smithy.beam.core.protocol;

import io.smithy.beam.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.ProtocolDefinitionTrait;
import software.amazon.smithy.model.traits.Trait;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static registry of {@link ProtocolAnalyzer} instances keyed by protocol trait {@link ShapeId}.
 * Language modules register analyzers at class-load time (or via {@link ServiceLoader} wrappers).
 */
public final class ProtocolAnalyzerFactory {

    private static final Map<ShapeId, ProtocolAnalyzer> REGISTRY = new ConcurrentHashMap<>();

    private ProtocolAnalyzerFactory() {}

    public static void register(ProtocolAnalyzer analyzer) {
        REGISTRY.put(analyzer.getProtocol(), analyzer);
    }

    /**
     * Finds the first registered analyzer for a protocol trait declared on the service.
     * Traits are inspected in iteration order; the first {@code @protocolDefinition} trait that
     * has a registered analyzer wins.
     */
    public static ProtocolAnalyzer forService(ServiceShape service, Model model) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(model, "model");

        for (Trait trait : service.getAllTraits().values()) {
            ShapeId traitId = trait.toShapeId();
            if (model.getShape(traitId).isEmpty()) {
                continue;
            }
            if (!model.getShape(traitId).get().hasTrait(ProtocolDefinitionTrait.class)) {
                continue;
            }
            ProtocolAnalyzer analyzer = REGISTRY.get(traitId);
            if (analyzer != null) {
                return analyzer;
            }
        }

        throw new CodegenException(
                "No registered ProtocolAnalyzer for service " + service.getId() + "; registry: " + REGISTRY.keySet());
    }
}
