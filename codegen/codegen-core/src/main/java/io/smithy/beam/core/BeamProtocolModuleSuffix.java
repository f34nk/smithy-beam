package io.smithy.beam.core;

import software.amazon.smithy.model.shapes.ShapeId;

import java.util.List;
import java.util.Optional;

public final class BeamProtocolModuleSuffix {

    private BeamProtocolModuleSuffix() {}

    public static String codecSuffix(ShapeId protocolTraitId) {
        return codecSuffix(protocolTraitId, List.of());
    }

    public static String codecSuffix(
            ShapeId protocolTraitId, List<? extends BeamProtocolIntegration> integrations) {
        if (BeamProtocolIds.REST_JSON_1.equals(protocolTraitId)) {
            return "rest_json_1";
        }
        if (BeamProtocolIds.AWS_JSON_1_0.equals(protocolTraitId)) {
            return "aws_json_1_0";
        }
        if (BeamProtocolIds.AWS_JSON_1_1.equals(protocolTraitId)) {
            return "aws_json_1_1";
        }
        if (BeamProtocolIds.AWS_QUERY.equals(protocolTraitId)) {
            return "aws_query";
        }
        if (BeamProtocolIds.EC2_QUERY.equals(protocolTraitId)) {
            return "ec2_query";
        }
        if (BeamProtocolIds.REST_XML.equals(protocolTraitId)) {
            return "rest_xml";
        }
        for (BeamProtocolIntegration integration : integrations) {
            Optional<String> suffix = integration.codecModuleSuffix(protocolTraitId);
            if (suffix.isPresent()) {
                return suffix.get();
            }
        }
        return BeamNameUtils.toSnakeCase(protocolTraitId.getName());
    }
}
