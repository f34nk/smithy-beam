package io.smithy.beam.core;

import java.util.Set;

/** Elixir typespec built-in type names that user aliases must not shadow. */
public final class BeamElixirBuiltinTypes {
  private static final Set<String> BUILTIN_TYPE_NAMES =
      Set.of(
          "any",
          "atom",
          "binary",
          "boolean",
          "charlist",
          "float",
          "fun",
          "integer",
          "keyword",
          "list",
          "map",
          "maybe_improper_list",
          "mfa",
          "module",
          "nil",
          "non_neg_integer",
          "no_return",
          "none",
          "nonempty_string",
          "number",
          "pid",
          "port",
          "pos_integer",
          "reference",
          "string",
          "struct",
          "term",
          "timeout",
          "tuple");

  private BeamElixirBuiltinTypes() {}

  /**
   * Returns true when a generated alias name would shadow an Elixir built-in typespec name, for
   * example {@code string} aliased to {@code String.t()}.
   */
  public static boolean shadowsBuiltinTypeName(String aliasName) {
    if (aliasName == null || aliasName.isEmpty()) {
      return false;
    }
    String normalized =
        aliasName.endsWith("()") ? aliasName.substring(0, aliasName.length() - 2) : aliasName;
    return BUILTIN_TYPE_NAMES.contains(normalized);
  }
}
