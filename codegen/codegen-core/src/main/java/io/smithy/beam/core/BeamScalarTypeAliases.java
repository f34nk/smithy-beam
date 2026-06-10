package io.smithy.beam.core;

/**
 * Helpers for named Smithy scalar aliases in generated BEAM type files.
 */
public final class BeamScalarTypeAliases {
    private BeamScalarTypeAliases() {}

    /**
     * Returns true when a named scalar alias would repeat a built-in type name with no
     * additional meaning, for example {@code float()} aliased to {@code float()}.
     *
     * @param aliasName generated alias name ({@code float} or {@code float()})
     * @param baseType underlying BEAM type ({@code float()})
     */
    public static boolean isRedundant(String aliasName, String baseType) {
        if (aliasName == null || baseType == null || baseType.isEmpty()) {
            return false;
        }
        String normalizedAlias = aliasName.endsWith("()") ? aliasName : aliasName + "()";
        return normalizedAlias.equals(baseType);
    }
}
