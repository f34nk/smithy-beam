package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.elixir.ElixirRenderer;
import io.beam.ir.elixir.Function;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ElixirXmlCodecIrTest {
  @Test
  void decodeSparseMapIsStructural() {
    for (Function fn : ElixirCodecHelperIr.decodeSparseMap()) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  @Test
  void elementTextIsStructural() {
    List<Function> functions =
        ElixirXmlCodecIr.restXmlDecodeHelpers().stream()
            .filter(function -> function.name().equals("element_text"))
            .toList();
    assertThat(functions).isNotEmpty();
    for (Function fn : functions) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  @Test
  void xmlChildListIsStructural() {
    for (Function fn : ElixirXmlCodecIr.xmlChildList()) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  @Test
  void restXmlDecodeHelpersAreStructural() {
    for (Function fn : ElixirXmlCodecIr.restXmlDecodeHelpers()) {
      ElixirIrTestSupport.assertStructural(fn);
    }
  }

  @Test
  void renderedElementTextContainsFlatMap() {
    List<Function> functions =
        ElixirXmlCodecIr.restXmlDecodeHelpers().stream()
            .filter(function -> function.name().equals("element_text"))
            .toList();
    assertThat(functions).isNotEmpty();
    assertThat(ElixirRenderer.renderFunction(functions.get(0))).contains("flat_map");
  }
}
