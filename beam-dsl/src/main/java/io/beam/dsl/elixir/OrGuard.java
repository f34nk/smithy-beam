package io.beam.dsl.elixir;

import java.util.List;

public record OrGuard(List<Guard> guards) implements Guard {

  public static OrGuard of(List<Guard> guards) {
    return new OrGuard(guards);
  }
}
