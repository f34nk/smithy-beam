package io.smithy.beam.core;

import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.Objects;

public final class BeamProtocolCodegenFactory {

    private BeamProtocolCodegenFactory() {}

    public static BeamProtocolCodegen create(Model model, ShapeId resolvedProtocolTraitId) {
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
        throw new CodegenException(
                "No BeamProtocolCodegen registered for protocol trait " + resolvedProtocolTraitId);
    }
}
