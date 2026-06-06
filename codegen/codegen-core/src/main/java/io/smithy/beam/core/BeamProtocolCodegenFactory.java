package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class BeamProtocolCodegenFactory {

    private BeamProtocolCodegenFactory() {}

    public static BeamProtocolCodegen create(Model model, ShapeId resolvedProtocolTraitId) {
        return create(model, resolvedProtocolTraitId, List.of());
    }

    public static BeamProtocolCodegen create(
            Model model,
            ShapeId resolvedProtocolTraitId,
            List<? extends BeamProtocolIntegration> integrations) {
        Objects.requireNonNull(resolvedProtocolTraitId, "resolvedProtocolTraitId");
        BeamHttpBindings bindings = BeamHttpBindings.from(model);
        if (BeamRestJson1ProtocolCodegen.REST_JSON_1.equals(resolvedProtocolTraitId)) {
            return new BeamRestJson1ProtocolCodegen(bindings);
        }
        if (BeamAwsJson10ProtocolCodegen.AWS_JSON_1_0.equals(resolvedProtocolTraitId)) {
            return new BeamAwsJson10ProtocolCodegen();
        }
        if (BeamAwsJson11ProtocolCodegen.AWS_JSON_1_1.equals(resolvedProtocolTraitId)) {
            return new BeamAwsJson11ProtocolCodegen();
        }
        if (BeamAwsQueryProtocolCodegen.AWS_QUERY.equals(resolvedProtocolTraitId)) {
            return new BeamAwsQueryProtocolCodegen();
        }
        if (BeamEc2QueryProtocolCodegen.EC2_QUERY.equals(resolvedProtocolTraitId)) {
            return new BeamEc2QueryProtocolCodegen();
        }
        if (BeamRestXmlProtocolCodegen.REST_XML.equals(resolvedProtocolTraitId)) {
            return new BeamRestXmlProtocolCodegen(bindings);
        }
        for (BeamProtocolIntegration integration : integrations) {
            Optional<BeamProtocolCodegen> custom =
                    integration.createProtocolCodegen(model, resolvedProtocolTraitId);
            if (custom.isPresent()) {
                return custom.get();
            }
        }
        throw new CodegenException(
                "No BeamProtocolCodegen registered for protocol trait "
                        + resolvedProtocolTraitId);
    }
}
