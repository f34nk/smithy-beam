package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlNilPattern implements ErlPattern {
  public static ErlNilPattern nilPattern() {
    return new ErlNilPattern();
  }

  @Override
  public List<String> lines() {
    return List.of("[]");
  }
}
