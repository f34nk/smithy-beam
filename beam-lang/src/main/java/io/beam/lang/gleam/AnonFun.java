package io.beam.lang.gleam;

import java.util.List;

/** Anonymous function {@code fn(params) { body }}. */
public record AnonFun(List<Param> params, Expression body) implements Expression {

  public static AnonFun of(List<Param> params, Expression body) {
    return new AnonFun(List.copyOf(params), body);
  }

  public static AnonFun of(String paramName, Expression body) {
    return of(List.of(Param.of(paramName)), body);
  }
}
