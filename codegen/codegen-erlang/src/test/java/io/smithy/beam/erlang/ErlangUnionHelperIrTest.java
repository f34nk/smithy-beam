package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Function;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.UnionShape;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
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
    List<Function> functions = ErlangUnionHelperIr.unionDecodeEncode(eventUnion, provider);
    assertThat(functions).hasSize(2);
    assertStructural(functions.get(0));
    assertStructural(functions.get(1));
    String combined =
        ErlangRenderer.renderFunction(functions.get(0))
            + "\n\n"
            + ErlangRenderer.renderFunction(functions.get(1));
    assertThat(combined)
        .isEqualTo(
            IrGoldenAssertions.readExpectedString("ir/union_decode_encode_event.expected.erl"));
  }

  private static void assertStructural(Function fn) {
    assertThat(fn.name()).isNotBlank();
    assertThat(fn.clauses()).isNotEmpty();
  }
}
