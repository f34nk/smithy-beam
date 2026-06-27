package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.erlang.ErlFunction;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.UnionShape;

class ErlangUnionHelperIrTest {
  private static UnionShape eventUnion;
  private static ErlangSymbolProvider provider;

  @BeforeAll
  static void setup() {
    String idl =
        """
                $version: "2"
                namespace com.example

                service EventService {
                    operations: [SendEvent]
                }

                operation SendEvent {
                    input: SendEventInput
                    output: SendEventOutput
                }

                structure SendEventInput {
                    event: Event
                }

                structure SendEventOutput {}

                union Event {
                    message: String
                    code: Integer
                }
                """;
    Model model = Model.assembler().addUnparsedModel("event.smithy", idl).assemble().unwrap();
    ServiceShape service =
        model.expectShape(ShapeId.from("com.example#EventService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    provider =
        new ErlangSymbolProvider(
            settings, model, service, "event_types.hrl", BeamCodegenKind.TYPES);
    eventUnion = model.expectShape(ShapeId.from("com.example#Event"), UnionShape.class);
  }

  @Test
  void unionDecodeEncodeAsStringMatchesGolden() throws IOException {
    List<ErlFunction> functions = ErlangUnionHelperIr.unionDecodeEncode(eventUnion, provider);
    assertThat(functions).hasSize(2);
    assertStructural(functions.get(0));
    assertStructural(functions.get(1));
    String combined = functions.get(0).asString() + "\n\n" + functions.get(1).asString();
    assertThat(combined).isEqualTo(readExpectedString("ir/union_decode_encode_event.expected.erl"));
  }

  private static void assertStructural(ErlFunction fn) {
    assertThat(fn.name()).isNotBlank();
    assertThat(fn.clauses()).isNotEmpty();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ErlangUnionHelperIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
