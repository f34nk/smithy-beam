package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.aws.traits.protocols.AwsJson1_0Trait;
import software.amazon.smithy.aws.traits.protocols.AwsJson1_1Trait;
import software.amazon.smithy.aws.traits.protocols.AwsQueryTrait;
import software.amazon.smithy.aws.traits.protocols.Ec2QueryTrait;
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait;
import software.amazon.smithy.aws.traits.protocols.RestXmlTrait;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.Trait;

class ProtocolResolverTest {

    @Test
    void resolveRestJson1() {
        Optional<Class<? extends Trait>> result =
            ProtocolResolver.resolve(ShapeId.from("aws.protocols#restJson1"));
        assertThat(result).isPresent().contains(RestJson1Trait.class);
    }

    @Test
    void resolveAwsJson10() {
        Optional<Class<? extends Trait>> result =
            ProtocolResolver.resolve(ShapeId.from("aws.protocols#awsJson1_0"));
        assertThat(result).isPresent().contains(AwsJson1_0Trait.class);
    }

    @Test
    void resolveAwsJson11() {
        Optional<Class<? extends Trait>> result =
            ProtocolResolver.resolve(ShapeId.from("aws.protocols#awsJson1_1"));
        assertThat(result).isPresent().contains(AwsJson1_1Trait.class);
    }

    @Test
    void resolveRestXml() {
        Optional<Class<? extends Trait>> result =
            ProtocolResolver.resolve(ShapeId.from("aws.protocols#restXml"));
        assertThat(result).isPresent().contains(RestXmlTrait.class);
    }

    @Test
    void resolveAwsQuery() {
        Optional<Class<? extends Trait>> result =
            ProtocolResolver.resolve(ShapeId.from("aws.protocols#awsQuery"));
        assertThat(result).isPresent().contains(AwsQueryTrait.class);
    }

    @Test
    void resolveEc2Query() {
        Optional<Class<? extends Trait>> result =
            ProtocolResolver.resolve(ShapeId.from("aws.protocols#ec2Query"));
        assertThat(result).isPresent().contains(Ec2QueryTrait.class);
    }

    @Test
    void resolveReturnsEmptyForUnknownProtocol() {
        Optional<Class<? extends Trait>> result =
            ProtocolResolver.resolve(ShapeId.from("unknown#protocol"));
        assertThat(result).isEmpty();
    }

    @Test
    void isKnownReturnsTrueForAllSupportedProtocols() {
        assertThat(ProtocolResolver.isKnown(ShapeId.from("aws.protocols#restJson1"))).isTrue();
        assertThat(ProtocolResolver.isKnown(ShapeId.from("aws.protocols#awsJson1_0"))).isTrue();
        assertThat(ProtocolResolver.isKnown(ShapeId.from("aws.protocols#awsJson1_1"))).isTrue();
        assertThat(ProtocolResolver.isKnown(ShapeId.from("aws.protocols#restXml"))).isTrue();
        assertThat(ProtocolResolver.isKnown(ShapeId.from("aws.protocols#awsQuery"))).isTrue();
        assertThat(ProtocolResolver.isKnown(ShapeId.from("aws.protocols#ec2Query"))).isTrue();
    }

    @Test
    void isKnownReturnsFalseForUnknownProtocol() {
        assertThat(ProtocolResolver.isKnown(ShapeId.from("unknown#protocol"))).isFalse();
    }

    @Test
    void knownProtocolsContainsExactlySixEntries() {
        assertThat(ProtocolResolver.knownProtocols()).hasSize(6);
    }

    @Test
    void knownProtocolsContainsAllExpectedIds() {
        assertThat(ProtocolResolver.knownProtocols()).contains(
            ShapeId.from("aws.protocols#restJson1"),
            ShapeId.from("aws.protocols#awsJson1_0"),
            ShapeId.from("aws.protocols#awsJson1_1"),
            ShapeId.from("aws.protocols#restXml"),
            ShapeId.from("aws.protocols#awsQuery"),
            ShapeId.from("aws.protocols#ec2Query")
        );
    }
}
