package io.smithy.beam.core;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.traits.HostLabelTrait;

import java.util.List;

public final class BeamHostLabelIndex {

    private final HttpBindingIndex httpIndex;

    private BeamHostLabelIndex(HttpBindingIndex httpIndex) {
        this.httpIndex = httpIndex;
    }

    public static BeamHostLabelIndex of(Model model) {
        return new BeamHostLabelIndex(HttpBindingIndex.of(model));
    }

    public List<MemberShape> hostLabelMembers(OperationShape operation) {
        return httpIndex.getRequestBindings(operation, HttpBinding.Location.LABEL).stream()
                .map(HttpBinding::getMember)
                .filter(m -> m.hasTrait(HostLabelTrait.class))
                .toList();
    }
}
