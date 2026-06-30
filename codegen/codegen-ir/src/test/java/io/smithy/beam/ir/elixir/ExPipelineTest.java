package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExPipelineTest {
  @Test
  void pipelineAsString() {
    ExPipeline pipeline =
        ExPipeline.pipeline(
            "query",
            ExMap.map(ExMapEntry.entry(ExString.string("verbose"), ExVar.var("input.verbose"))),
            ExCapturedBlock.capturedBlock("Enum.reject(fn {_, v} -> is_nil(v) end)"),
            ExCapturedBlock.capturedBlock("Map.new()"));
    assertThat(pipeline.asString())
        .contains("query =")
        .contains("|> Enum.reject(fn {_, v} -> is_nil(v) end)")
        .contains("|> Map.new()");
  }
}
