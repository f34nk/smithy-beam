package io.beam.dsl.erlang;

public record WildcardPattern(String name) implements Pattern {

  public static WildcardPattern of() {
    return new WildcardPattern(null);
  }

  public static WildcardPattern of(String name) {
    return new WildcardPattern(name);
  }
}
