package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenException;

/**
 * Generator editions that gate breaking behavior behind explicit smithy-build opt-in.
 */
public enum BeamEdition {
    V2026("2026");

    private final String label;

    BeamEdition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static BeamEdition fromSettings(BeamSettings settings) {
        String edition = settings.edition();
        for (BeamEdition value : values()) {
            if (value.label.equals(edition)) {
                return value;
            }
        }
        throw new CodegenException("Unknown edition '" + edition + "'. Supported editions: 2026");
    }

    public boolean supportsEventStreams() {
        return ordinal() >= V2026.ordinal();
    }
}
