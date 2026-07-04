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
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;


@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ErlangEnumHelperIrTest {
  private static EnumShape colorShape;
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
                    color: Color
                }

                structure GetColorOutput {}

                enum Color {
                    RED
                    BLUE
                }
                """;
    Model model = Model.assembler().addUnparsedModel("color.smithy", idl).assemble().unwrap();
    ServiceShape service =
        model.expectShape(ShapeId.from("com.example#ColorService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    provider =
        new ErlangSymbolProvider(
            settings, model, service, "color_types.hrl", BeamCodegenKind.TYPES);
    colorShape = model.expectShape(ShapeId.from("com.example#Color"), EnumShape.class);
  }

  @Test
  void decodeColorAsStringMatchesGolden() throws IOException {
    Function decode = ErlangEnumHelperIr.enumDecodeEncode(colorShape, provider).get(0);
    assertStructural(decode);
    IrGoldenAssertions.assertGolden(decode, "ir/enum_decode_color.expected.erl");
  }

  @Test
  void encodeColorAsStringMatchesGolden() throws IOException {
    Function encode = ErlangEnumHelperIr.enumDecodeEncode(colorShape, provider).get(1);
    assertStructural(encode);
    IrGoldenAssertions.assertGolden(encode, "ir/enum_encode_color.expected.erl");
  }

  @Test
  void decodeColorListMatchesGolden() throws IOException {
    Function decode = ErlangEnumHelperIr.enumDecodeEncode(colorShape, provider).get(2);
    assertStructural(decode);
    IrGoldenAssertions.assertGolden(decode, "ir/enum_decode_color_list.expected.erl");
  }

  @Test
  void encodeColorListMatchesGolden() throws IOException {
    Function encode = ErlangEnumHelperIr.enumDecodeEncode(colorShape, provider).get(3);
    assertStructural(encode);
    IrGoldenAssertions.assertGolden(encode, "ir/enum_encode_color_list.expected.erl");
  }

  private static void assertStructural(Function fn) {
    assertThat(fn.name()).isNotBlank();
    assertThat(fn.clauses()).isNotEmpty();
  }
}
