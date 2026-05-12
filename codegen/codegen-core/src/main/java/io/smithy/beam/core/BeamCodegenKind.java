package io.smithy.beam.core;

/**
 * Distinguishes DirectedCodegen passes that share {@link BeamSettings} and the same
 * service but emit different artifacts (types header versus client or server module).
 */
public enum BeamCodegenKind {
    TYPES,
    CLIENT,
    SERVER
}
