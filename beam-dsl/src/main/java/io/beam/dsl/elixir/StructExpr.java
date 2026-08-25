package io.beam.dsl.elixir;

import java.util.List;

public record StructExpr(String moduleName, Expression baseOrNull, List<StructField> fields)
    implements Expression {

  public static StructExpr of(String moduleName, List<StructField> fields) {
    return new StructExpr(moduleName, null, fields);
  }

  public static StructExpr update(Expression base, String moduleName, List<StructField> fields) {
    return new StructExpr(moduleName, base, fields);
  }
}
