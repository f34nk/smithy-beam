package io.smithy.beam.core;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import software.amazon.smithy.aws.traits.protocols.AwsJson1_0Trait;
import software.amazon.smithy.aws.traits.protocols.AwsJson1_1Trait;
import software.amazon.smithy.aws.traits.protocols.AwsQueryTrait;
import software.amazon.smithy.aws.traits.protocols.Ec2QueryTrait;
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait;
import software.amazon.smithy.aws.traits.protocols.RestXmlTrait;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;

/**
 * Maps well-known Smithy protocol {@link ShapeId}s to their {@link Trait} classes.
 */
public final class ProtocolResolver {

    private static final Map<ShapeId, Class<? extends Trait>> KNOWN = Map.of(
            ShapeId.from("aws.protocols#restJson1"),  RestJson1Trait.class,
            ShapeId.from("aws.protocols#awsJson1_0"), AwsJson1_0Trait.class,
            ShapeId.from("aws.protocols#awsJson1_1"), AwsJson1_1Trait.class,
            ShapeId.from("aws.protocols#restXml"),    RestXmlTrait.class,
            ShapeId.from("aws.protocols#awsQuery"),   AwsQueryTrait.class,
            ShapeId.from("aws.protocols#ec2Query"),   Ec2QueryTrait.class
    );

    private ProtocolResolver() {}

    public static Optional<Class<? extends Trait>> resolve(ShapeId id) {
        return Optional.ofNullable(KNOWN.get(id));
    }

    public static boolean isKnown(ShapeId id) {
        return KNOWN.containsKey(id);
    }

    public static Set<ShapeId> knownProtocols() {
        return KNOWN.keySet();
    }
}
