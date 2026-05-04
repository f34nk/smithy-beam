package io.smithy.beam.core;

import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.List;

/**
 * Settings deserialized from the smithy-build.json plugin configuration block
 * by CodegenDirector#settings.
 *
 * Supported properties:
 *   "service"  -- shape ID of the service to generate (required unless model has exactly one service).
 *   "edition"  -- required explicit opt-in to generator behavior, e.g. "2026".
 *   "module"   -- output module/file name prefix; defaults to the last segment of the service namespace.
 *   "protocol" -- accepted for future protocol-aware type generation; no-op in the initial type-only generator.
 *   "relativeDate" -- accepted for future deprecation-based filtering; no-op in the initial type-only generator.
 *   "relativeVersion" -- accepted for future deprecation-based filtering; no-op in the initial type-only generator.
 */
public final class BeamSettings {

    private ShapeId service;
    private String module;
    private ShapeId protocol;
    private String edition;
    private String relativeDate;
    private String relativeVersion;

    public BeamSettings() {}

    public void service(ShapeId service) {
        this.service = service;
    }

    public ShapeId service() {
        return service;
    }

    public void module(String module) {
        this.module = module;
    }

    public String module() {
        return module;
    }

    public void protocol(ShapeId protocol) {
        this.protocol = protocol;
    }

    public ShapeId protocol() {
        return protocol;
    }

    public void edition(String edition) {
        this.edition = edition;
    }

    public String edition() {
        return edition;
    }

    public void relativeDate(String relativeDate) {
        this.relativeDate = relativeDate;
    }

    public String relativeDate() {
        return relativeDate;
    }

    public void relativeVersion(String relativeVersion) {
        this.relativeVersion = relativeVersion;
    }

    public String relativeVersion() {
        return relativeVersion;
    }

    public ShapeId resolveService(Model model) {
        requireEdition();
        if (service() != null) {
            return service();
        }

        List<ServiceShape> services = model.getServiceShapes().stream()
            .sorted((a, b) -> a.getId().toString().compareTo(b.getId().toString()))
            .toList();
        if (services.size() == 1) {
            return services.get(0).getId();
        }
        if (services.isEmpty()) {
            throw new CodegenException("No service shape found. Configure the 'service' setting.");
        }
        throw new CodegenException("Multiple service shapes found. Configure the 'service' setting explicitly.");
    }

    private void requireEdition() {
        if (edition() == null || edition().isBlank()) {
            throw new CodegenException("Missing required 'edition' setting. Set edition to opt in to generator behavior.");
        }
    }

    /**
     * Derives the module name from the settings or, if not set, from the
     * last dot-separated segment of the given namespace.
     *
     * Example: "smithy.beam.demo.basic" -> "basic"
     */
    public String resolveModule(String namespace) {
        if (module() != null && !module().isEmpty()) {
            return module();
        }
        String[] parts = namespace.split("\\.");
        return parts[parts.length - 1];
    }
}
