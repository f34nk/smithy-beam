package io.smithy.beam.erlang;

import io.beam.dsl.erlang.Callback;
import java.util.ArrayList;
import java.util.List;

final class ErlangBehaviourModuleBuilder {
  private final List<Callback> callbacks = new ArrayList<>();

  void addCallback(Callback callback) {
    callbacks.add(callback);
  }

  List<Callback> callbacks() {
    return callbacks;
  }
}
