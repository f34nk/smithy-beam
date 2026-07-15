package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Callback;
import java.util.ArrayList;
import java.util.List;

final class ElixirBehaviourModuleBuilder {
  private final List<Callback> callbacks = new ArrayList<>();

  void addCallback(Callback callback) {
    callbacks.add(callback);
  }

  List<Callback> callbacks() {
    return callbacks;
  }
}
