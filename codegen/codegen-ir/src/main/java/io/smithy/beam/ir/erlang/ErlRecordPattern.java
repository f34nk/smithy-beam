package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlRecordPattern implements ErlPattern {
  private final String name;
  private final List<ErlRecordFieldPattern> fields;
  private final String aliasOrNull;

  public ErlRecordPattern(String name, List<ErlRecordFieldPattern> fields, String aliasOrNull) {
    this.name = name;
    this.fields = List.copyOf(fields);
    this.aliasOrNull = aliasOrNull;
  }

  public ErlRecordPattern(String name, List<ErlRecordFieldPattern> fields) {
    this(name, fields, null);
  }

  public static ErlRecordPattern recordPattern(String name, ErlRecordFieldPattern... fields) {
    return new ErlRecordPattern(name, List.of(fields));
  }

  /** Input = #input_record{ field_a, field_b, ... } */
  public static ErlRecordPattern recordFunctionHead(
      String alias, String recordName, List<String> fieldNames) {
    List<ErlRecordFieldPattern> fields =
        fieldNames.stream().map(ErlRecordFieldPattern::field).toList();
    return new ErlRecordPattern(recordName, fields, alias);
  }

  public String name() {
    return name;
  }

  public List<ErlRecordFieldPattern> fields() {
    return fields;
  }

  public String aliasOrNull() {
    return aliasOrNull;
  }

  @Override
  public List<String> lines() {
    StringBuilder sb = new StringBuilder();
    if (aliasOrNull != null) {
      sb.append(aliasOrNull).append(" = ");
    }
    sb.append('#').append(name).append('{');
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
