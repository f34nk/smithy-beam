package io.beam.dsl.elixir;

public record IsTypeGuard(String type, String variable) implements Guard {

  public static IsTypeGuard of(String type, String variable) {
    return new IsTypeGuard(type, variable);
  }
}
