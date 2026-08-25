package io.beam.dsl.erlang;

import java.util.List;

public record TuplePattern(List<Pattern> elements) implements Pattern {

  public static TuplePattern of(List<Pattern> elements) {
    return new TuplePattern(elements);
  }
}
