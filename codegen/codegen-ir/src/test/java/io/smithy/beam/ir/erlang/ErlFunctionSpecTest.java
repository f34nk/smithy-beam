package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlFunctionSpecTest {
  @Test
  void singleLineSpecLines() {
    ErlFunctionSpec spec =
        new ErlFunctionSpec(
            "decode_basic_item", "undefined | null | map()", "undefined | #basic_item{}");
    assertThat(spec.lines(0))
        .containsExactly(
            "-spec decode_basic_item(undefined | null | map()) -> undefined | #basic_item{}.");
  }

  @Test
  void singleLineSpecAsString() {
    ErlFunctionSpec spec =
        new ErlFunctionSpec(
            "decode_basic_item", "undefined | null | map()", "undefined | #basic_item{}");
    assertThat(spec.asString())
        .isEqualTo(
            "-spec decode_basic_item(undefined | null | map()) -> undefined | #basic_item{}.");
  }

  @Test
  void wrappedSpecAsString() {
    String longInput =
        "a() | b() | c() | d() | e() | f() | g() | h() | i() | j() | k() | l() | m() | n()";
    ErlFunctionSpec spec = new ErlFunctionSpec("very_long_function_name", longInput, "ok()");
    assertThat(spec.asString())
        .isEqualTo("-spec very_long_function_name(" + longInput + ") ->\n    ok().");
  }
}
