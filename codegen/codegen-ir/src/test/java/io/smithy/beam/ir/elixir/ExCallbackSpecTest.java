package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExCallbackSpecTest {
  @Test
  void callbackSpecLines() {
    ExCallbackSpec spec =
        ExCallbackSpec.callbackSpec(
            "handle_get_type_closure",
            List.of("term()", "BasicServiceTypes.GetTypeClosureInput.t()", "term()"),
            "{:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}");
    assertThat(spec.lines(1))
        .containsExactly(
            "  @callback handle_get_type_closure(",
            "              term(),",
            "              BasicServiceTypes.GetTypeClosureInput.t(),",
            "              term()",
            "  ) ::",
            "    {:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}");
  }

  @Test
  void callbackSpecAsString() {
    ExCallbackSpec spec =
        ExCallbackSpec.callbackSpec(
            "handle_get_type_closure",
            List.of("term()", "BasicServiceTypes.GetTypeClosureInput.t()", "term()"),
            "{:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}");
    assertThat(spec.asString(1))
        .isEqualTo(
            "  @callback handle_get_type_closure(\n"
                + "              term(),\n"
                + "              BasicServiceTypes.GetTypeClosureInput.t(),\n"
                + "              term()\n"
                + "  ) ::\n"
                + "    {:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}");
  }
}
