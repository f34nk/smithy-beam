package io.smithy.beam.protocols;

import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.BodySpec;
import io.smithy.beam.core.ir.OperationSpec;
import software.amazon.smithy.aws.traits.protocols.Ec2QueryNameTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.XmlNameTrait;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code aws.protocols#ec2Query} protocol analyzer.
 *
 * <p>Nearly identical to {@link AwsQueryProtocolAnalyzer}: all operations are {@code POST /} with
 * form-encoded bodies and XML responses.  The key difference from plain awsQuery is that EC2
 * applies {@code @ec2QueryName} trait values as the wire name for members that carry the trait.
 * This analyzer reads those trait values at codegen time and records them as
 * {@link BodySpec#wireNameOverrides()}, so the Erlang writer can emit the correct wire key in the
 * generated {@code make_*_request} body-builder map.
 */
public final class Ec2QueryProtocolAnalyzer extends AwsQueryProtocolAnalyzer {

    private static final ShapeId PROTOCOL = ShapeId.from("aws.protocols#ec2Query");

    @Override
    protected ShapeId protocol() {
        return PROTOCOL;
    }

    @Override
    public OperationSpec analyzeClientOperation(OperationShape op, Model model, ServiceShape service) {
        OperationSpec base = super.analyzeClientOperation(op, model, service);

        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        Map<String, String> overrides = new HashMap<>();
        List<String> bodyMembers = new ArrayList<>();

        for (MemberShape member : input.getAllMembers().values()) {
            String smithyName = member.getMemberName();
            bodyMembers.add(smithyName);
            // ec2Query name resolution: @ec2QueryName takes precedence, then @xmlName as fallback.
            // AWS EC2 models frequently use @xmlName (e.g. "InstanceId" on the "InstanceIds" member)
            // instead of @ec2QueryName to specify the wire serialization key.
            if (member.hasTrait(Ec2QueryNameTrait.class)) {
                overrides.put(smithyName, member.expectTrait(Ec2QueryNameTrait.class).getValue());
            } else if (member.hasTrait(XmlNameTrait.class)) {
                overrides.put(smithyName, member.expectTrait(XmlNameTrait.class).getValue());
            }
        }

        BodySpec body = new BodySpec(
                BodyEncoding.FORM_URLENCODED,
                bodyMembers,
                null,
                overrides.isEmpty() ? Map.of() : Map.copyOf(overrides));

        return new OperationSpec(
                base.operationName(),
                base.serviceName(),
                base.role(),
                base.http(),
                base.labels(),
                base.queries(),
                base.headers(),
                body,
                base.errors(),
                base.auth(),
                base.retry(),
                base.pagination(),
                base.outputTypeName(),
                base.inputTypeName(),
                base.responseEncoding(),
                base.protocolContentType(),
                base.protocolErrorStrategy(),
                base.apiVersion());
    }
}
