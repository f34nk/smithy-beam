package io.beam.dsl.erlang;

import java.util.List;

public record ListPattern(List<Pattern> elements, Pattern tail) implements Pattern {

  public static ListPattern of(List<Pattern> elements) {
    return new ListPattern(elements, null);
  }

  public static ListPattern cons(Pattern head, Pattern tail) {
    return new ListPattern(List.of(head), tail);
  }

  public static ListPattern cons(List<Pattern> heads, Pattern tail) {
    return new ListPattern(heads, tail);
  }
}
