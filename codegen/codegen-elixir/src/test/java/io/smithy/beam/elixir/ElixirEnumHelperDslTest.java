package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.Function;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ElixirEnumHelperIrTest {
  private static EnumShape basicStringShape;
  private static ElixirSymbolProvider provider;

  @BeforeAll
  static void setup() {
    String idl =
        """
                $version: "2"
                namespace com.example

                service StringService {
                    operations: [GetString]
                }

                operation GetString {
                    input: GetStringInput
                    output: GetStringOutput
                }

                structure GetStringInput {
                    value: BasicString
                }

                structure GetStringOutput {}

                enum BasicString {
                    FOO
                    BAR
                }
                """;
    Model model = Model.assembler().addUnparsedModel("string.smithy", idl).assemble().unwrap();
    ServiceShape service =
        model.expectShape(ShapeId.from("com.example#StringService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    provider =
        new ElixirSymbolProvider(
            settings,
            model,
            service,
            layout.typesModuleFile(),
            ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
            BeamCodegenKind.TYPES);
    basicStringShape = model.expectShape(ShapeId.from("com.example#BasicString"), EnumShape.class);
  }

  @Test
  void enumDecodeEncodeIsStructural() {
    List<Function> functions = ElixirEnumHelperDsl.enumDecodeEncode(basicStringShape, provider);
    assertThat(functions).hasSizeGreaterThanOrEqualTo(4);
    for (Function fn : functions) {
      ElixirDslTestSupport.assertStructural(fn);
    }
  }

  @Test
  void restJsonIrCollectsEnumHelpers() {
    Model model = model();
    ServiceShape service =
        model.expectShape(ShapeId.from("com.example#StringService"), ServiceShape.class);
    List<Function> functions = ElixirRestJsonDsl.enumHelperFunctions(model, service, provider);
    assertThat(functions).isEmpty();
  }

  @Test
  void restXmlIrCollectsEnumHelpers() {
    Model model = model();
    ServiceShape service =
        model.expectShape(ShapeId.from("com.example#StringService"), ServiceShape.class);
    List<Function> functions = ElixirRestXmlDsl.enumHelperFunctions(model, service, provider);
    assertThat(functions).isEmpty();
  }

  private static Model model() {
    String idl =
        """
                $version: "2"
                namespace com.example

                service StringService {
                    operations: [GetString]
                }

                operation GetString {
                    input: GetStringInput
                    output: GetStringOutput
                }

                structure GetStringInput {
                    value: BasicString
                }

                structure GetStringOutput {}

                enum BasicString {
                    FOO
                    BAR
                }
                """;
    return Model.assembler().addUnparsedModel("string.smithy", idl).assemble().unwrap();
  }
}
