package io.smithy.beam.core;

import java.util.Set;

/** Gleam prelude / common type and constructor names that user aliases must not shadow. */
public final class BeamGleamBuiltinTypes {
  private static final Set<String> BUILTIN_TYPE_NAMES =
      Set.of(
          "BitArray",
          "Bool",
          "Dict",
          "Dynamic",
          "Float",
          "Int",
          "List",
          "Nil",
          "Option",
          "Result",
          "String",
          "UtfCodepoint");

  private static final Set<String> BUILTIN_CONSTRUCTOR_NAMES =
      Set.of("Error", "False", "None", "Ok", "Some", "True");

  private BeamGleamBuiltinTypes() {}

  /**
   * Returns true when a generated type alias or custom type name would shadow a Gleam prelude or
   * common stdlib type name.
   */
  public static boolean shadowsBuiltinTypeName(String typeName) {
    if (typeName == null || typeName.isEmpty()) {
      return false;
    }
    return BUILTIN_TYPE_NAMES.contains(typeName);
  }

  /**
   * Returns true when a generated constructor name would shadow a Gleam prelude constructor such as
   * {@code Ok} or {@code None}.
   */
  public static boolean shadowsBuiltinConstructorName(String constructorName) {
    if (constructorName == null || constructorName.isEmpty()) {
      return false;
    }
    return BUILTIN_CONSTRUCTOR_NAMES.contains(constructorName);
  }

  /** Case-insensitive check used when normalizing escaped identifiers. */
  public static boolean shadowsBuiltinTypeNameIgnoreCase(String typeName) {
    if (typeName == null || typeName.isEmpty()) {
      return false;
    }
    for (String builtin : BUILTIN_TYPE_NAMES) {
      if (builtin.equalsIgnoreCase(typeName)) {
        return true;
      }
    }
    return false;
  }
}
