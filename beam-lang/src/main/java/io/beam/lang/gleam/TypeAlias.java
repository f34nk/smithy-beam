package io.beam.lang.gleam;

/** {@code pub type Name = Definition} (or private when {@code pub} is false). */
public record TypeAlias(String name, String definition, DocComment docOrNull, boolean pub) {

  public static TypeAlias pub(String name, String definition) {
    return new TypeAlias(name, definition, null, true);
  }

  public static TypeAlias pub(String name, String definition, DocComment doc) {
    return new TypeAlias(name, definition, doc, true);
  }
}
