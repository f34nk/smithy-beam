package io.smithy.beam.protocols;

import io.smithy.beam.core.ir.BodyEncoding;
import io.smithy.beam.core.ir.BodySpec;
import io.smithy.beam.core.ir.OperationSpec;
import software.amazon.smithy.aws.traits.protocols.Ec2QueryNameTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
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
 *
 * <p>It also collects {@link BodySpec#nestedWireNameOverrides()} — the shallow {@code @xmlName}
 * rename maps for the members of each top-level member's target structure (or list element
 * structure).  The Erlang writer uses these to emit {@code smithy_query:rename_map_keys/2} calls
 * that apply the nested renames before the query encoder sees them.
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
        Map<String, Map<String, String>> nestedOverrides = new HashMap<>();
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

            // Collect nested @xmlName/@ec2QueryName overrides for the direct members of the
            // structure that this member targets (or the element structure of a list).
            Map<String, String> nested = collectNestedMemberRenames(member, model);
            if (!nested.isEmpty()) {
                nestedOverrides.put(smithyName, nested);
            }
        }

        BodySpec body = new BodySpec(
                BodyEncoding.FORM_URLENCODED,
                bodyMembers,
                null,
                overrides.isEmpty() ? Map.of() : Map.copyOf(overrides),
                nestedOverrides.isEmpty() ? Map.of() : Map.copyOf(nestedOverrides));

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

    /**
     * For a given member of the top-level input structure, resolves the target structure (or the
     * element structure of a list), then returns a map of {@code smithyMemberName → wireName} for
     * any members of that structure that carry {@code @ec2QueryName} or {@code @xmlName} with a
     * value different from the member name.
     *
     * <p>Returns an empty map when the member's target is not a structure or list-of-structures,
     * or when none of the nested members have overriding names.
     */
    private static Map<String, String> collectNestedMemberRenames(MemberShape member, Model model) {
        Shape target = model.expectShape(member.getTarget());

        StructureShape structure = null;
        if (target instanceof StructureShape s) {
            structure = s;
        } else if (target instanceof ListShape list) {
            Shape elementShape = model.expectShape(list.getMember().getTarget());
            if (elementShape instanceof StructureShape s) {
                structure = s;
            }
        }

        if (structure == null) {
            return Map.of();
        }

        Map<String, String> renames = new HashMap<>();
        for (MemberShape nested : structure.getAllMembers().values()) {
            String nestedName = nested.getMemberName();
            if (nested.hasTrait(Ec2QueryNameTrait.class)) {
                String wireName = nested.expectTrait(Ec2QueryNameTrait.class).getValue();
                if (!wireName.equals(nestedName)) {
                    renames.put(nestedName, wireName);
                }
            } else if (nested.hasTrait(XmlNameTrait.class)) {
                String wireName = nested.expectTrait(XmlNameTrait.class).getValue();
                if (!wireName.equals(nestedName)) {
                    renames.put(nestedName, wireName);
                }
            }
        }
        return renames.isEmpty() ? Map.of() : Map.copyOf(renames);
    }
}
