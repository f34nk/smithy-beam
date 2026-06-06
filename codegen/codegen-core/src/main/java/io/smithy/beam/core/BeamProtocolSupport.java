package io.smithy.beam.core;

import software.amazon.smithy.model.shapes.ShapeId;

import java.util.List;

public final class BeamProtocolSupport {

    private BeamProtocolSupport() {}

    public static boolean isBuiltinProtocol(ShapeId id) {
        return BeamRestJson1ProtocolCodegen.REST_JSON_1.equals(id)
                || BeamAwsJson10ProtocolCodegen.AWS_JSON_1_0.equals(id)
                || BeamAwsJson11ProtocolCodegen.AWS_JSON_1_1.equals(id)
                || BeamAwsQueryProtocolCodegen.AWS_QUERY.equals(id)
                || BeamEc2QueryProtocolCodegen.EC2_QUERY.equals(id)
                || BeamRestXmlProtocolCodegen.REST_XML.equals(id);
    }

    public static boolean hasWireCodegen(
            ShapeId protocol,
            BeamProtocolCodegen protocolCodegen,
            List<? extends BeamProtocolIntegration> integrations) {
        if (protocol == null) {
            return false;
        }
        if (protocolCodegen != null) {
            return true;
        }
        return integrations.stream().anyMatch(i -> i.emitsWireCodecs(protocol));
    }
}
