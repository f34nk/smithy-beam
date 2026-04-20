package io.smithy.beam.elixir.codegen.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.codegen.codec.Ec2QueryCodec;
import io.smithy.beam.elixir.codegen.codec.JsonCodec;
import io.smithy.beam.elixir.codegen.codec.QueryCodec;
import io.smithy.beam.elixir.codegen.codec.XmlCodec;
import io.smithy.beam.elixir.codegen.http.RestTransport;
import io.smithy.beam.elixir.codegen.http.RpcTransport;
import org.junit.jupiter.api.Test;

/**
 * Slim wiring tests for each concrete Elixir protocol integration. Mirrors
 * the Erlang version: each protocol selects a codec / transport pair, while
 * the codec / transport behaviour itself is exercised by dedicated unit
 * tests under the {@code codec/} and {@code http/} sub-packages.
 */
class ElixirProtocolIntegrationTest {

    @Test
    void restJson1PairsJsonCodecWithRestTransport() {
        ElixirRestJson1Integration i = new ElixirRestJson1Integration();
        assertThat(i.codec()).isInstanceOf(JsonCodec.class);
        assertThat(i.transport()).isInstanceOf(RestTransport.class);
    }

    @Test
    void awsJson10PairsJsonCodecWithRpcTransport() {
        ElixirAwsJson10Integration i = new ElixirAwsJson10Integration();
        assertThat(i.codec()).isInstanceOf(JsonCodec.class);
        assertThat(i.transport()).isInstanceOf(RpcTransport.class);
    }

    @Test
    void awsJson11PairsJsonCodecWithRpcTransport() {
        ElixirAwsJson11Integration i = new ElixirAwsJson11Integration();
        assertThat(i.codec()).isInstanceOf(JsonCodec.class);
        assertThat(i.transport()).isInstanceOf(RpcTransport.class);
    }

    @Test
    void restXmlPairsXmlCodecWithRestTransport() {
        ElixirRestXmlIntegration i = new ElixirRestXmlIntegration();
        assertThat(i.codec()).isInstanceOf(XmlCodec.class);
        assertThat(i.transport()).isInstanceOf(RestTransport.class);
    }

    @Test
    void awsQueryPairsQueryCodecWithRpcTransport() {
        ElixirAwsQueryIntegration i = new ElixirAwsQueryIntegration();
        assertThat(i.codec()).isInstanceOf(QueryCodec.class);
        assertThat(i.transport()).isInstanceOf(RpcTransport.class);
    }

    @Test
    void ec2QueryPairsEc2CodecWithRpcTransport() {
        ElixirEc2QueryIntegration i = new ElixirEc2QueryIntegration();
        assertThat(i.codec()).isInstanceOf(Ec2QueryCodec.class);
        assertThat(i.transport()).isInstanceOf(RpcTransport.class);
    }

    @Test
    void allProtocolIdsAreRegistered() {
        assertThat(new ElixirRestJson1Integration().protocolId().toString())
                .isEqualTo("aws.protocols#restJson1");
        assertThat(new ElixirAwsJson10Integration().protocolId().toString())
                .isEqualTo("aws.protocols#awsJson1_0");
        assertThat(new ElixirAwsJson11Integration().protocolId().toString())
                .isEqualTo("aws.protocols#awsJson1_1");
        assertThat(new ElixirRestXmlIntegration().protocolId().toString())
                .isEqualTo("aws.protocols#restXml");
        assertThat(new ElixirAwsQueryIntegration().protocolId().toString())
                .isEqualTo("aws.protocols#awsQuery");
        assertThat(new ElixirEc2QueryIntegration().protocolId().toString())
                .isEqualTo("aws.protocols#ec2Query");
    }
}
