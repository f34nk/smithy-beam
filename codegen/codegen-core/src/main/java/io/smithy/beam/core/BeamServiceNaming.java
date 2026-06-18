package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.shapes.ServiceShape;

public final class BeamServiceNaming {

    private static final String SNAKE_STEM_PATTERN = "[a-z][a-z0-9_]*";

    private BeamServiceNaming() {}

    /**
     * Service-scoped shape name per Smithy rename maps and service context.
     */
    public static String effectiveServiceName(ServiceShape service) {
        return service.getId().getName(service);
    }

    public static String effectiveServiceSnakeName(ServiceShape service) {
        return BeamNameUtils.toSnakeCase(effectiveServiceName(service));
    }

    /**
     * Service-scoped module stem: settings "name" when set, otherwise derived name.
     */
    public static String effectiveServiceSnakeName(BeamSettings settings, ServiceShape service) {
        return resolveServiceSnakeStem(settings, effectiveServiceName(service));
    }

    /**
     * Same as {@link #effectiveServiceSnakeName(BeamSettings, ServiceShape)} when the
     * effective service name string is already known (layout string constructor path).
     */
    public static String effectiveServiceSnakeName(BeamSettings settings, String effectiveServiceName) {
        return resolveServiceSnakeStem(settings, effectiveServiceName);
    }

    private static String resolveServiceSnakeStem(BeamSettings settings, String effectiveServiceName) {
        String override = settings.name();
        if (override != null && !override.isBlank()) {
            validateSnakeStem(override);
            return override;
        }
        return BeamNameUtils.toSnakeCase(effectiveServiceName);
    }

    static void validateSnakeStem(String value) {
        if (!value.matches(SNAKE_STEM_PATTERN)) {
            throw new CodegenException(
                    "Invalid 'name' setting \""
                            + value
                            + "\". Expected a snake_case stem matching "
                            + SNAKE_STEM_PATTERN
                            + " without role suffixes such as \"_client\" or \"_types\".");
        }
    }
}
