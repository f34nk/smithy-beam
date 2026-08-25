package io.beam.dsl.elixir;

import java.util.List;

public record AndGuard(List<Guard> guards) implements Guard {

  public static AndGuard of(List<Guard> guards) {
    return new AndGuard(guards);
  }
}
