package io.smithy.beam.ir.erlang;

import java.util.List;

public interface IrObject {
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
