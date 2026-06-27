package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamServiceIndex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Lists operations bound under a service using {@link BeamServiceIndex}, sorted deterministically
 * for stable client and server stubs.
 */
final class ElixirTopDown {

  private ElixirTopDown() {}

  static List<OperationShape> containedOperationsSorted(Model model, ServiceShape service) {
    List<OperationShape> list =
        new ArrayList<>(BeamServiceIndex.of(model).containedOperations(service));
    list.sort(Comparator.comparing(o -> o.getId().toString()));
    return list;
  }

  static String structureSpecType(String typesModuleName, Symbol structureSym) {
    return typesModuleName + "." + structureSym.getName() + ".t()";
  }
}
