package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExCallbackSpec;
import java.util.ArrayList;
import java.util.List;

final class ElixirBehaviourModuleBuilder {
  private final List<ExCallbackSpec> callbacks = new ArrayList<>();

  void addCallback(ExCallbackSpec callback) {
    callbacks.add(callback);
  }

  List<ExCallbackSpec> callbacks() {
    return callbacks;
  }
}
