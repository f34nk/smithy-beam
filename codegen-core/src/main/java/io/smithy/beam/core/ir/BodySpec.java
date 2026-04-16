package io.smithy.beam.core.ir;

import java.util.List;
import java.util.Map;

/**
 * Describes how an operation's input members are serialised into the HTTP request body.
 *
 * <p>{@code wireNameOverrides} — present only for ec2Query operations — maps each Smithy member
 * name to its {@code @ec2QueryName} / {@code @xmlName} wire name for the top-level input
 * structure. An empty map means no overrides are needed; null is treated the same as an empty
 * map by consumers.
 *
 * <p>{@code nestedWireNameOverrides} — present only for ec2Query operations — maps each
 * top-level Smithy member name to the rename map that must be applied to the elements of that
 * member before query encoding.  This covers {@code @xmlName} annotations on the members of the
 * nested structure (e.g. {@code TagSpecification.Tags} → {@code "Tag"}).  An empty map means
 * no nested renames are needed.
 */
public record BodySpec(
        BodyEncoding encoding,
        List<String> bodyMemberNames,
        String payloadMember,
        Map<String, String> wireNameOverrides,
        Map<String, Map<String, String>> nestedWireNameOverrides) {

    /** Convenience constructor for protocols that do not use any ec2QueryName overrides. */
    public BodySpec(BodyEncoding encoding, List<String> bodyMemberNames, String payloadMember) {
        this(encoding, bodyMemberNames, payloadMember, Map.of(), Map.of());
    }

    /** Convenience constructor for protocols with top-level overrides but no nested overrides. */
    public BodySpec(BodyEncoding encoding, List<String> bodyMemberNames, String payloadMember,
                    Map<String, String> wireNameOverrides) {
        this(encoding, bodyMemberNames, payloadMember, wireNameOverrides, Map.of());
    }
}
