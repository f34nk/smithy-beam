package io.smithy.beam.erlang.codegen.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.codegen.codec.Ec2QueryCodec;
import io.smithy.beam.erlang.codegen.codec.JsonCodec;
import io.smithy.beam.erlang.codegen.codec.QueryCodec;
import io.smithy.beam.erlang.codegen.codec.XmlCodec;
import io.smithy.beam.erlang.codegen.http.RestTransport;
import io.smithy.beam.erlang.codegen.http.RpcTransport;
import org.junit.jupiter.api.Test;

/**
 * Slim wiring tests for each concrete Erlang protocol integration.
 *
 * <p>Each protocol simply selects a {@link io.smithy.beam.erlang.codegen.codec.ErlangCodec}
 * and {@link io.smithy.beam.erlang.codegen.codec.ErlangTransport} pair. The
 * codec / transport behaviour itself is exercised by the dedicated unit
 * tests under the {@code codec/} and {@code http/} sub-packages. Snapshot
 * coverage of the full generated module lives in {@code codegen-test}.
 */
class ErlangProtocolIntegrationTest {

    @Test
    void restJson1PairsJsonCodecWithRestTransport() {
        ErlangRestJson1Integration i = new ErlangRestJson1Integration();
        assertThat(i.codec()).isInstanceOf(JsonCodec.class);
        assertThat(i.transport()).isInstanceOf(RestTransport.class);
    }

    @Test
    void awsJson10PairsJsonCodecWithRpcTransport() {
        ErlangAwsJson10Integration i = new ErlangAwsJson10Integration();
        assertThat(i.codec()).isInstanceOf(JsonCodec.class);
        assertThat(i.transport()).isInstanceOf(RpcTransport.class);
    }

    @Test
    void awsJson11PairsJsonCodecWithRpcTransport() {
        ErlangAwsJson11Integration i = new ErlangAwsJson11Integration();
        assertThat(i.codec()).isInstanceOf(JsonCodec.class);
        assertThat(i.transport()).isInstanceOf(RpcTransport.class);
    }

    @Test
    void restXmlPairsXmlCodecWithRestTransport() {
        ErlangRestXmlIntegration i = new ErlangRestXmlIntegration();
        assertThat(i.codec()).isInstanceOf(XmlCodec.class);
        assertThat(i.transport()).isInstanceOf(RestTransport.class);
    }

    @Test
    void awsQueryPairsQueryCodecWithRpcTransport() {
        ErlangAwsQueryIntegration i = new ErlangAwsQueryIntegration();
        assertThat(i.codec()).isInstanceOf(QueryCodec.class);
        assertThat(i.transport()).isInstanceOf(RpcTransport.class);
    }

    @Test
    void ec2QueryPairsEc2CodecWithRpcTransport() {
        ErlangEc2QueryIntegration i = new ErlangEc2QueryIntegration();
        assertThat(i.codec()).isInstanceOf(Ec2QueryCodec.class);
        assertThat(i.transport()).isInstanceOf(RpcTransport.class);
    }

    @Test
    void allProtocolIdsAreRegistered() {
        assertThat(new ErlangRestJson1Integration().protocolId().toString())
                .isEqualTo("aws.protocols#restJson1");
        assertThat(new ErlangAwsJson10Integration().protocolId().toString())
                .isEqualTo("aws.protocols#awsJson1_0");
        assertThat(new ErlangAwsJson11Integration().protocolId().toString())
                .isEqualTo("aws.protocols#awsJson1_1");
        assertThat(new ErlangRestXmlIntegration().protocolId().toString())
                .isEqualTo("aws.protocols#restXml");
        assertThat(new ErlangAwsQueryIntegration().protocolId().toString())
                .isEqualTo("aws.protocols#awsQuery");
        assertThat(new ErlangEc2QueryIntegration().protocolId().toString())
                .isEqualTo("aws.protocols#ec2Query");
    }
}
