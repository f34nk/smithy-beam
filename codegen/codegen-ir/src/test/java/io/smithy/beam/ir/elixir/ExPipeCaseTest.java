package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExPipeCaseTest {
  @Test
  void pipeCaseAsString() {
    ExPipeCase pipeCase =
        ExPipeCase.pipeCase(
            ExCall.call("List", "keyfind", ExVar.var("headers"), ExString.string("X-Request-Tag"), ExInteger.integer(0)),
            ExCaseBranch.branch(ExAtomPattern.atom("ok"), ExVar.var("v")),
            ExCaseBranch.branch(ExAtomPattern.atom("nil"), ExAtom.atom("nil")));
    assertThat(pipeCase.asString())
        .contains("List.keyfind(headers, \"X-Request-Tag\", 0)")
        .contains("|> case do")
        .contains(":ok -> v")
        .contains(":nil -> :nil");
  }
}
