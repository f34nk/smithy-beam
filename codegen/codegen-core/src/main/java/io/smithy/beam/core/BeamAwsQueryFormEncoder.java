package io.smithy.beam.core;

import software.amazon.smithy.aws.traits.protocols.Ec2QueryNameTrait;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.traits.XmlNameTrait;

public final class BeamAwsQueryFormEncoder {

    private BeamAwsQueryFormEncoder() {}

    public static String operationAction(OperationShape operation, ServiceShape service) {
        return operation.getId().getName(service);
    }

    public static String serviceVersion(ServiceShape service) {
        return service.getVersion();
    }

    public static String awsQueryFormKey(MemberShape member) {
        return member.getTrait(XmlNameTrait.class)
                .map(XmlNameTrait::getValue)
                .orElseGet(() -> capitalizeFirst(member.getMemberName()));
    }

    public static String ec2QueryFormKey(MemberShape member) {
        return member.getTrait(Ec2QueryNameTrait.class)
                .map(Ec2QueryNameTrait::getValue)
                .orElseGet(() -> member.getTrait(XmlNameTrait.class)
                        .map(trait -> capitalizeFirst(trait.getValue()))
                        .orElseGet(() -> capitalizeFirst(member.getMemberName())));
    }

    private static String capitalizeFirst(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
