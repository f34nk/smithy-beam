package io.smithy.beam.core;

import software.amazon.smithy.model.knowledge.NullableIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.InputTrait;

public final class BeamMemberNullability {

    private BeamMemberNullability() {}

    public static boolean isMemberNullable(
            NullableIndex index, StructureShape container, MemberShape member) {
        NullableIndex.CheckMode mode = container.hasTrait(InputTrait.class)
                ? NullableIndex.CheckMode.CLIENT
                : NullableIndex.CheckMode.SERVER;
        return index.isMemberNullable(member, mode);
    }
}
