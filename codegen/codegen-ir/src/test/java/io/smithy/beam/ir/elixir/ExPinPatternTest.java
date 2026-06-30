package io.smithy.beam.ir.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExPinPatternTest {
  @Test
  void pinPatternRendersBinaryMatch() {
    ExPinPattern pattern =
        ExPinPattern.pin(
            ExBinaryConcatPattern.concat(
                ExStringPattern.string("/types/"), ExVarPattern.var("name_seg")),
            ExVarPattern.var("path"));
    assertThat(pattern.asString()).isEqualTo("\"/types/\" <> name_seg = path");
  }
}
