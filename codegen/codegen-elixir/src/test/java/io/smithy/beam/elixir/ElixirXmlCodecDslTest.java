package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Function;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ElixirXmlCodecIrTest {
  @Test
  void decodeSparseMapIsStructural() {
    for (Function fn : ElixirCodecHelperDsl.decodeSparseMap()) {
      ElixirDslTestSupport.assertStructural(fn);
    }
  }

  @Test
  void elementTextIsStructural() {
    List<Function> functions =
        ElixirXmlCodecDsl.restXmlDecodeHelpers().stream()
            .filter(function -> function.name().equals("element_text"))
            .toList();
    assertThat(functions).isNotEmpty();
    for (Function fn : functions) {
      ElixirDslTestSupport.assertStructural(fn);
    }
  }

  @Test
  void xmlChildListIsStructural() {
    for (Function fn : ElixirXmlCodecDsl.xmlChildList()) {
      ElixirDslTestSupport.assertStructural(fn);
    }
  }

  @Test
  void restXmlDecodeHelpersAreStructural() {
    for (Function fn : ElixirXmlCodecDsl.restXmlDecodeHelpers()) {
      ElixirDslTestSupport.assertStructural(fn);
    }
  }

  @Test
  void renderedElementTextContainsFlatMap() {
    List<Function> functions =
        ElixirXmlCodecDsl.restXmlDecodeHelpers().stream()
            .filter(function -> function.name().equals("element_text"))
            .toList();
    assertThat(functions).isNotEmpty();
    assertThat(ElixirRenderer.renderFunction(functions.get(0))).contains("flat_map");
  }
}
