package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExMapEntry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

class ElixirJsonCodecIrTest {
  private static ElixirSymbolProvider provider;

  @BeforeAll
  static void setup() {
    String idl =
        """
                $version: "2"
                namespace com.example

                service ItemService {
                    operations: [ListItems]
                }

                operation ListItems {
                    input: ListItemsInput
                    output: ListItemsOutput
                }

                structure ListItemsInput {}

                structure ListItemsOutput {
                    items: BasicItemList
                }

                structure BasicItem {
                    name: String
                    count: Integer
                }

                list BasicItemList {
                    member: BasicItem
                }
                """;
    Model model = Model.assembler().addUnparsedModel("item.smithy", idl).assemble().unwrap();
    ServiceShape service =
        model.expectShape(ShapeId.from("com.example#ItemService"), ServiceShape.class);
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
  }

  @Test
  void decodedBodyPreludeMatchesGolden() throws IOException {
    String combined =
        ElixirJsonCodecIr.decodedBodyPrelude().stream()
            .flatMap(expr -> expr.lines().stream())
            .collect(Collectors.joining("\n"));
    assertThat(combined).isEqualTo(readExpectedString("ir/json_decoded_body_prelude.expected.ex"));
  }

  @Test
  void bodyMapEntriesMatchGolden() throws IOException {
    Model model = model();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    StructureShape basicItem =
        model.expectShape(ShapeId.from("com.example#BasicItem"), StructureShape.class);
    List<MemberShape> members =
        List.of(
            basicItem.getMember("name").orElseThrow(), basicItem.getMember("count").orElseThrow());
    List<ExMapEntry> entries =
        ElixirJsonCodecIr.bodyMapEntries(
            model, httpIndex, provider, "Types", members, "record", "event_stream");
    String combined = entries.stream().map(ExMapEntry::asString).collect(Collectors.joining(",\n"));
    assertThat(combined).isEqualTo(readExpectedString("ir/json_body_map_entries.expected.ex"));
  }

  private static Model model() {
    String idl =
        """
                $version: "2"
                namespace com.example

                service ItemService {
                    operations: [ListItems]
                }

                operation ListItems {
                    input: ListItemsInput
                    output: ListItemsOutput
                }

                structure ListItemsInput {}

                structure ListItemsOutput {
                    items: BasicItemList
                }

                structure BasicItem {
                    name: String
                    count: Integer
                }

                list BasicItemList {
                    member: BasicItem
                }
                """;
    return Model.assembler().addUnparsedModel("item.smithy", idl).assemble().unwrap();
  }

  private static String exprAsString(ExExpr expr) {
    return String.join("\n", expr.lines());
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirJsonCodecIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
