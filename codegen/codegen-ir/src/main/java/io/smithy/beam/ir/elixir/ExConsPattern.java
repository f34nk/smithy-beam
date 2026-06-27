package io.smithy.beam.ir.elixir;

import java.util.List;

public final class ExConsPattern implements ExPattern {
  private final ExPattern head;
  private final ExPattern tail;

  public ExConsPattern(ExPattern head, ExPattern tail) {
    this.head = head;
    this.tail = tail;
  }

  public static ExConsPattern consPattern(ExPattern head, ExPattern tail) {
    return new ExConsPattern(head, tail);
  }

  public ExPattern head() {
    return head;
  }

  public ExPattern tail() {
    return tail;
  }

  @Override
  public List<String> lines() {
    if (tail instanceof ExNilPattern) {
      return List.of("[" + head.asString() + "]");
    }
    return List.of("[" + head.asString() + " | " + tail.asString() + "]");
  }
}
