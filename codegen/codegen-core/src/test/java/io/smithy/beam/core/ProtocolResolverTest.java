package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import software.amazon.smithy.aws.traits.protocols.AwsJson1_0Trait;
import software.amazon.smithy.aws.traits.protocols.AwsJson1_1Trait;
import software.amazon.smithy.aws.traits.protocols.AwsQueryTrait;
import software.amazon.smithy.aws.traits.protocols.Ec2QueryTrait;
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait;
import software.amazon.smithy.aws.traits.protocols.RestXmlTrait;
import software.amazon.smithy.model.shapes.ShapeId;

class ProtocolResolverTest {

    @Test
    void resolve_restJson1_returnsCorrectTraitClass() {
        assertThat(ProtocolResolver.resolve(ShapeId.from("aws.protocols#restJson1")))
                .hasValue(RestJson1Trait.class);
    }

    @Test
    void resolve_awsJson10_returnsCorrectTraitClass() {
        assertThat(ProtocolResolver.resolve(ShapeId.from("aws.protocols#awsJson1_0")))
                .hasValue(AwsJson1_0Trait.class);
    }

    @Test
    void resolve_awsJson11_returnsCorrectTraitClass() {
        assertThat(ProtocolResolver.resolve(ShapeId.from("aws.protocols#awsJson1_1")))
                .hasValue(AwsJson1_1Trait.class);
    }

    @Test
    void resolve_restXml_returnsCorrectTraitClass() {
        assertThat(ProtocolResolver.resolve(ShapeId.from("aws.protocols#restXml")))
                .hasValue(RestXmlTrait.class);
    }

    @Test
    void resolve_awsQuery_returnsCorrectTraitClass() {
        assertThat(ProtocolResolver.resolve(ShapeId.from("aws.protocols#awsQuery")))
                .hasValue(AwsQueryTrait.class);
    }

    @Test
    void resolve_ec2Query_returnsCorrectTraitClass() {
        assertThat(ProtocolResolver.resolve(ShapeId.from("aws.protocols#ec2Query")))
                .hasValue(Ec2QueryTrait.class);
    }

    @Test
    void resolve_unknownProtocol_returnsEmpty() {
        assertThat(ProtocolResolver.resolve(ShapeId.from("example#myProtocol"))).isEmpty();
    }

    @Test
    void isKnown_returnsTrueForKnownProtocol() {
        assertThat(ProtocolResolver.isKnown(ShapeId.from("aws.protocols#restJson1"))).isTrue();
    }

    @Test
    void isKnown_returnsFalseForUnknownProtocol() {
        assertThat(ProtocolResolver.isKnown(ShapeId.from("example#unknown"))).isFalse();
    }

    @Test
    void knownProtocols_containsAllSixProtocols() {
        assertThat(ProtocolResolver.knownProtocols()).hasSize(6);
    }
}
