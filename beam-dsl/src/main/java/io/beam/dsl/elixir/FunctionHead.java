package io.beam.dsl.elixir;

import java.util.List;

public record FunctionHead(List<Pattern> params, Guard guardOrNull) {

  public static FunctionHead of(List<Pattern> params) {
    return new FunctionHead(params, null);
  }

  public static FunctionHead of(List<Pattern> params, Guard guard) {
    return new FunctionHead(params, guard);
  }
}
