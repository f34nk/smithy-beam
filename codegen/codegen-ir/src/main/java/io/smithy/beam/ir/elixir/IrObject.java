package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public interface IrObject {
  String INDENT_STEP = "  ";
  int SPEC_LINE_LIMIT = 98;

  static String indent(int depth) {
    return INDENT_STEP.repeat(Math.max(0, depth));
  }

  List<String> lines();

  default List<String> lines(int indent) {
    if (indent == 0) {
      return lines();
    }
    List<String> raw = lines();
    List<String> out = new ArrayList<>(raw.size());
    String prefix = indent(indent);
    for (String line : raw) {
      out.add(prefix + line);
    }
    return out;
  }

  default String asString() {
    return String.join("\n", lines());
  }

  default String asString(int indent) {
    return String.join("\n", lines(indent));
  }
}
