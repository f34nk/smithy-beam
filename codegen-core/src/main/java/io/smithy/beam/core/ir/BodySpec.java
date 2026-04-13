package io.smithy.beam.core.ir;

import java.util.List;
import java.util.Map;

/**
 * Describes how an operation's input members are serialised into the HTTP request body.
 *
 * <p>{@code wireNameOverrides} — present only for ec2Query operations — maps each Smithy member
 * name to its {@code @ec2QueryName} wire name. An empty map means no overrides are needed;
 * null is treated the same as an empty map by consumers.
 */
public record BodySpec(
        BodyEncoding encoding,
        List<String> bodyMemberNames,
        String payloadMember,
        Map<String, String> wireNameOverrides) {

    /** Convenience constructor for protocols that do not use ec2QueryName overrides. */
    public BodySpec(BodyEncoding encoding, List<String> bodyMemberNames, String payloadMember) {
        this(encoding, bodyMemberNames, payloadMember, Map.of());
    }
}
