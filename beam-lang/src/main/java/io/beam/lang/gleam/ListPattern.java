package io.beam.lang.gleam;

import java.util.List;

public record ListPattern(List<Pattern> elements) implements Pattern {

  public static ListPattern of(List<Pattern> elements) {
    return new ListPattern(List.copyOf(elements));
  }
}
