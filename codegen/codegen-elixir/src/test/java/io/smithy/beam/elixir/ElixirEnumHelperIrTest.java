package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExFunction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

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
  void enumDecodeEncodeAsStringMatchesGolden() throws IOException {
    List<ExFunction> functions = ElixirEnumHelperIr.enumDecodeEncode(basicStringShape, provider);
    assertThat(functions).hasSize(2);
    ElixirIrTestSupport.assertStructural(functions.get(0));
    ElixirIrTestSupport.assertStructural(functions.get(1));
    String combined = functions.get(0).asString() + "\n\n" + functions.get(1).asString();
    assertThat(combined)
        .isEqualTo(readExpectedString("ir/enum_decode_encode_basic_string.expected.ex"));
  }

  @Test
  void restJsonIrCollectsEnumHelpers() {
    Model model = model();
    ServiceShape service =
        model.expectShape(ShapeId.from("com.example#StringService"), ServiceShape.class);
    List<ExFunction> functions = ElixirRestJsonIr.enumHelperFunctions(model, service, provider);
    assertThat(functions).hasSize(2);
  }

  @Test
  void restXmlIrCollectsEnumHelpers() {
    Model model = model();
    ServiceShape service =
        model.expectShape(ShapeId.from("com.example#StringService"), ServiceShape.class);
    List<ExFunction> functions = ElixirRestXmlIr.enumHelperFunctions(model, service, provider);
    assertThat(functions).hasSize(2);
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

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirEnumHelperIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
