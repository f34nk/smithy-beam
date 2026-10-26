package io.beam.lang.gleam;

public record WildcardPattern() implements Pattern {

  public static final WildcardPattern INSTANCE = new WildcardPattern();

  public static WildcardPattern of() {
    return INSTANCE;
  }
}
