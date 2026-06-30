package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExMapPattern implements ExPattern {
  private final List<ExMapFieldPattern> fields;

  public ExMapPattern(List<ExMapFieldPattern> fields) {
    this.fields = List.copyOf(fields);
  }

  public static ExMapPattern map(List<ExMapFieldPattern> fields) {
    return new ExMapPattern(fields);
  }

  public static ExMapPattern map(ExMapFieldPattern... fields) {
    return new ExMapPattern(List.of(fields));
  }

  public List<ExMapFieldPattern> fields() {
    return fields;
  }

  @Override
  public List<String> lines() {
    StringBuilder sb = new StringBuilder("%{");
    for (int i = 0; i < fields.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(fields.get(i).asString());
    }
    sb.append('}');
    return List.of(sb.toString());
  }
}
