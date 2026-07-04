package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.Function;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangMapHelperIrTest {
  private static Model model;
  private static MapShape colorMapShape;
  private static ErlangSymbolProvider provider;

  @BeforeAll
  static void setup() {
    String idl =
        """
                $version: "2"
                namespace com.example

                service ColorService {
                    operations: [GetColor]
                }

                operation GetColor {
                    input: GetColorInput
                    output: GetColorOutput
                }

                structure GetColorInput {
                    labels: ColorLabels
                }

                structure GetColorOutput {}

                map ColorLabels {
                    key: Color
                    value: String
                }

                enum Color {
                    RED
                    BLUE
                }
                """;
    model = Model.assembler().addUnparsedModel("color.smithy", idl).assemble().unwrap();
    ServiceShape service =
        model.expectShape(ShapeId.from("com.example#ColorService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    provider =
        new ErlangSymbolProvider(
            settings, model, service, "color_types.hrl", BeamCodegenKind.TYPES);
    colorMapShape = model.expectShape(ShapeId.from("com.example#ColorLabels"), MapShape.class);
  }

  @Test
  void decodeColorLabelsMatchesGolden() throws IOException {
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    Function decode =
        ErlangMapHelperIr.mapDecodeEncode(model, httpIndex, colorMapShape, provider).get(0);
    assertStructural(decode);
    IrGoldenAssertions.assertGolden(decode, "ir/map_decode_color_labels.expected.erl");
  }

  @Test
  void encodeColorLabelsMatchesGolden() throws IOException {
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    Function encode =
        ErlangMapHelperIr.mapDecodeEncode(model, httpIndex, colorMapShape, provider).get(1);
    assertStructural(encode);
    IrGoldenAssertions.assertGolden(encode, "ir/map_encode_color_labels.expected.erl");
  }

  private static void assertStructural(Function fn) {
    assertThat(fn.name()).isNotBlank();
    assertThat(fn.clauses()).isNotEmpty();
  }
}
