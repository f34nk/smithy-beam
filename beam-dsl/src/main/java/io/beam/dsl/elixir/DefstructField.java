package io.beam.dsl.elixir;

public record DefstructField(String nameOrNil, Expression defaultOrNull) {

  public static DefstructField field(String name) {
    return new DefstructField(name, null);
  }

  public static DefstructField field(String name, Expression defaultValue) {
    return new DefstructField(name, defaultValue);
  }

  public static DefstructField nilField() {
    return new DefstructField(null, null);
  }
}
