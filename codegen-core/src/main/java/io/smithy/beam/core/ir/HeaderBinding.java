package io.smithy.beam.core.ir;

/**
 * Describes a single HTTP header binding for an operation.
 *
 * <p>When {@code literalValue} is non-null the header value is a hard-coded
 * string (e.g. the {@code X-Amz-Target} value for AWS JSON protocols) and
 * must NOT be read from the caller's {@code Input} map.
 */
public record HeaderBinding(
        String smithyMemberName,
        String headerName,
        boolean required,
        String literalValue   // non-null → emit as a string literal, not a maps:get
) {
    /** Convenience constructor for headers that are read from the Input map. */
    public HeaderBinding(String smithyMemberName, String headerName, boolean required) {
        this(smithyMemberName, headerName, required, null);
    }
}
