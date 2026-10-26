package io.beam.lang.gleam;

import java.util.List;

/** {@code pub type Name { Variant... }} (or private when {@code pub} is false). */
public record CustomType(String name, List<Variant> variants, DocComment docOrNull, boolean pub) {

  public static CustomType pub(String name, List<Variant> variants) {
    return new CustomType(name, List.copyOf(variants), null, true);
  }

  public static CustomType pub(String name, List<Variant> variants, DocComment doc) {
    return new CustomType(name, List.copyOf(variants), doc, true);
  }
}
