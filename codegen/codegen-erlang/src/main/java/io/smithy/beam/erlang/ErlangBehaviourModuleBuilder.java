package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlCallbackSpec;
import java.util.ArrayList;
import java.util.List;

final class ErlangBehaviourModuleBuilder {
  private final List<ErlCallbackSpec> callbacks = new ArrayList<>();

  void addCallback(ErlCallbackSpec callback) {
    callbacks.add(callback);
  }

  List<ErlCallbackSpec> callbacks() {
    return callbacks;
  }
}
