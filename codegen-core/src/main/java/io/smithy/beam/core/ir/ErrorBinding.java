package io.smithy.beam.core.ir;

/**
 * Binding of a single Smithy error shape to its HTTP representation.
 *
 * @param smithyName        the unqualified Smithy shape name (e.g. {@code "NoSuchKey"})
 * @param httpCode          HTTP status code declared by {@code @httpError}; defaults to 400
 * @param strategy          how error codes are identified on the wire for this protocol
 * @param messageMemberName name of the body field that carries the human-readable error message
 *                          (e.g. {@code "Message"} for AWS-flavoured protocols); {@code null}
 *                          when the protocol has no defined message field — writers fall back
 *                          to placing the entire body under a {@code body} key
 */
public record ErrorBinding(
        String smithyName,
        int httpCode,
        ErrorCodeStrategy strategy,
        String messageMemberName) {

    /**
     * Backward-compatible constructor for call sites that do not yet carry message-member
     * information. Defaults {@code messageMemberName} to {@code null}.
     */
    public ErrorBinding(String smithyName, int httpCode, ErrorCodeStrategy strategy) {
        this(smithyName, httpCode, strategy, null);
    }
}
