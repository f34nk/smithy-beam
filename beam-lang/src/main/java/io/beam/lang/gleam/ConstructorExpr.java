package io.beam.lang.gleam;

import java.util.List;

/**
 * Custom-type or prelude constructor such as {@code Ok(x)}, {@code None}, or {@code
 * Person(name: n, age: a)}.
 */
public record ConstructorExpr(String name, List<LabelledArg> args) implements Expression {

  public static ConstructorExpr of(String name) {
    return new ConstructorExpr(name, List.of());
  }

  public static ConstructorExpr of(String name, List<LabelledArg> args) {
    return new ConstructorExpr(name, List.copyOf(args));
  }

  public static ConstructorExpr positional(String name, List<Expression> args) {
    return new ConstructorExpr(
        name, args.stream().map(LabelledArg::positional).toList());
  }
}
