package io.smithy.beam.core;

/**
 * Smithy constraint traits ({@code length}, {@code range}, {@code pattern}, and similar)
 * are validated at runtime boundaries (protocol decode, server encode, optional helpers),
 * not by narrowing generated Dialyzer types, unless a future plugin setting explicitly
 * enables validation helper emission.
 */
public final class BeamConstraintPolicy {

    private BeamConstraintPolicy() {}
}
