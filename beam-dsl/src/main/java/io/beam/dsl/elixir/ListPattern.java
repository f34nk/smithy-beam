package io.beam.dsl.elixir;

import java.util.List;

public record ListPattern(List<Pattern> elements) implements Pattern {

  public static ListPattern of(List<Pattern> elements) {
    return new ListPattern(elements);
  }
}
