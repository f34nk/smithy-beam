package io.smithy.beam.ir.elixir;

public final class ExMapEntry {
  private final ExExpr key;
  private final ExExpr value;

  public ExMapEntry(ExExpr key, ExExpr value) {
    this.key = key;
    this.value = value;
  }

  public static ExMapEntry entry(ExExpr key, ExExpr value) {
    return new ExMapEntry(key, value);
  }

  public ExExpr key() {
    return key;
  }

  public ExExpr value() {
    return value;
  }

  public String asString() {
    if (key instanceof ExAtom atom) {
      String name = atom.value();
      if (name.startsWith(":")) {
        name = name.substring(1);
      }
      return name + ": " + value.asString();
    }
    return key.asString() + " => " + value.asString();
  }
}
