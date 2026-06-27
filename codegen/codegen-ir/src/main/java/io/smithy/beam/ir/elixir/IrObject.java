package io.smithy.beam.ir.elixir;

import java.util.List;

public interface IrObject {
  String INDENT_STEP = "  ";
  int SPEC_LINE_LIMIT = 98;

  static String indent(int depth) {
    return INDENT_STEP.repeat(Math.max(0, depth));
  }

  List<String> lines();

  default List<String> lines(int indent) {
    return lines();
  }

  default String asString() {
    return String.join("\n", lines());
  }

  default String asString(int indent) {
    return String.join("\n", lines(indent));
  }
}
