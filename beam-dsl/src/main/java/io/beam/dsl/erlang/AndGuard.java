package io.beam.dsl.erlang;

import java.util.List;

public record AndGuard(List<Guard> guards) implements Guard {

  public static AndGuard of(Guard left, Guard right) {
    return new AndGuard(List.of(left, right));
  }

  public static AndGuard of(List<Guard> guards) {
    return new AndGuard(guards);
  }
}
