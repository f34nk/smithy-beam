package io.beam.dsl.elixir;

import java.util.List;

public record UseDirective(String module, List<UseOption> options) {

  public static UseDirective of(String module, List<UseOption> options) {
    return new UseDirective(module, options);
  }
}
