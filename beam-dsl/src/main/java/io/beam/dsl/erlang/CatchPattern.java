package io.beam.dsl.erlang;

public record CatchPattern(Pattern classPattern, Pattern reasonPattern) implements Pattern {

  public static CatchPattern of(Pattern classPattern, Pattern reasonPattern) {
    return new CatchPattern(classPattern, reasonPattern);
  }

  public static CatchPattern anyAny() {
    return of(WildcardPattern.of(), WildcardPattern.of());
  }

  public static CatchPattern anyReason(String name) {
    return of(WildcardPattern.of(), VariablePattern.of(name));
  }
}
