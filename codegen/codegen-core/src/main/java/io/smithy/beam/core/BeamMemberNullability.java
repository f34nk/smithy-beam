package io.smithy.beam.core;

import software.amazon.smithy.model.knowledge.NullableIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.InputTrait;

/**
 * Resolves member nullability for generated types using Smithy {@link NullableIndex}.
 *
 * <p>Dedicated operation input shapes carry {@code @input} and use
 * {@link NullableIndex.CheckMode#CLIENT}. Smithy honors {@code @clientOptional} in CLIENT mode
 * and {@code @default} according to the active check mode. No custom trait handling is required
 * beyond selecting the correct check mode.
 */
public final class BeamMemberNullability {

    private BeamMemberNullability() {}

    /**
     * Returns whether a structure member is nullable in generated client or server types.
     *
     * <p>Input shapes use CLIENT mode so {@code @clientOptional} and {@code @default} flow
     * through {@link NullableIndex} as defined by the Smithy specification.
     */
    public static boolean isMemberNullable(
            NullableIndex index, StructureShape container, MemberShape member) {
        NullableIndex.CheckMode mode = container.hasTrait(InputTrait.class)
                ? NullableIndex.CheckMode.CLIENT
                : NullableIndex.CheckMode.SERVER;
        return index.isMemberNullable(member, mode);
    }
}
