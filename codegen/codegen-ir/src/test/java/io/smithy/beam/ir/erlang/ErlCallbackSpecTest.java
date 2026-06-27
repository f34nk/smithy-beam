package io.smithy.beam.ir.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErlCallbackSpecTest {
  @Test
  void singleLineCallbackLines() {
    ErlCallbackSpec spec =
        new ErlCallbackSpec(
            "handle_basic",
            "term(), basic_input(), term()",
            "{ok, basic_output()} | {error, term()}");
    assertThat(spec.lines(0))
        .containsExactly(
            "-callback handle_basic(term(), basic_input(), term()) -> {ok, basic_output()} | {error, term()}.");
  }

  @Test
  void singleLineCallbackAsString() {
    ErlCallbackSpec spec =
        new ErlCallbackSpec(
            "handle_basic",
            "term(), basic_input(), term()",
            "{ok, basic_output()} | {error, term()}");
    assertThat(spec.asString())
        .isEqualTo(
            "-callback handle_basic(term(), basic_input(), term()) -> {ok, basic_output()} | {error, term()}.");
  }

  @Test
  void wrappedCallbackAsString() {
    String longInput =
        "a() | b() | c() | d() | e() | f() | g() | h() | i() | j() | k() | l() | m() | n()";
    ErlCallbackSpec spec = new ErlCallbackSpec("very_long_callback_name", longInput, "ok()");
    assertThat(spec.asString())
        .isEqualTo("-callback very_long_callback_name(" + longInput + ") ->\n    ok().");
  }
}
