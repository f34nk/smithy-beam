package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExSpecTest {
  @Test
  void singleLineSpecLines() {
    ExSpec spec =
        ExSpec.functionSpec("decode_basic_item", "nil | map()", "nil | BasicItem.t()");
    assertThat(spec.lines(0))
        .containsExactly("@spec decode_basic_item(nil | map()) :: nil | BasicItem.t()");
  }

  @Test
  void singleLineSpecAsString() {
    ExSpec spec =
        ExSpec.functionSpec("decode_basic_item", "nil | map()", "nil | BasicItem.t()");
    assertThat(spec.asString())
        .isEqualTo("@spec decode_basic_item(nil | map()) :: nil | BasicItem.t()");
  }

  @Test
  void wrappedSpecAsString() {
    String longParams =
        "client_config(), BasicServiceTypes.GetTypeClosureInput.t()";
    String longReturn =
        "{:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}";
    ExSpec spec = ExSpec.functionSpec("get_type_closure", longParams, longReturn);
    assertThat(spec.asString())
        .isEqualTo(
            "@spec get_type_closure(client_config(), BasicServiceTypes.GetTypeClosureInput.t()) ::\n"
                + "        {:ok, BasicServiceTypes.GetTypeClosureOutput.t()} | {:error, term()}");
  }
}
