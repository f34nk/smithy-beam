package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlConsPattern implements ErlPattern {
  private final ErlPattern head;
  private final ErlPattern tail;

  public ErlConsPattern(ErlPattern head, ErlPattern tail) {
    this.head = head;
    this.tail = tail;
  }

  public static ErlConsPattern consPattern(ErlPattern head, ErlPattern tail) {
    return new ErlConsPattern(head, tail);
  }

  public ErlPattern head() {
    return head;
  }

  public ErlPattern tail() {
    return tail;
  }

  @Override
  public List<String> lines() {
    if (tail instanceof ErlNilPattern) {
      return List.of("[" + head.asString() + "]");
    }
    return List.of("[" + head.asString() + " | " + tail.asString() + "]");
  }
}
