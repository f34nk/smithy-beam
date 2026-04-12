package io.smithy.beam.core.ir;

import java.util.List;

public record BodySpec(BodyEncoding encoding, List<String> bodyMemberNames, String payloadMember) {}
