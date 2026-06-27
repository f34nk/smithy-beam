package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExStructAccess implements ExExpr {
  private final ExExpr struct;
  private final String fieldName;

  public ExStructAccess(ExExpr struct, String fieldName) {
    this.struct = struct;
    this.fieldName = fieldName;
  }

  public static ExStructAccess structAccess(ExExpr struct, String fieldName) {
    return new ExStructAccess(struct, fieldName);
  }

  public ExExpr struct() {
    return struct;
  }

  public String fieldName() {
    return fieldName;
  }

  @Override
  public List<String> lines() {
    return List.of(struct.asString() + "." + fieldName);
  }
}
