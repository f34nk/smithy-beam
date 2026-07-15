package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.MapEntry;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
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
  void decodedBodyPreludeIsStructural() {
    String combined =
        ElixirJsonCodecIr.decodedBodyPrelude().stream()
            .map(ElixirRenderer::renderStatement)
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    assertThat(combined).contains("decoded");
  }

  @Test
  void bodyMapEntriesAreStructural() {
    Model model = model();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    StructureShape basicItem =
        model.expectShape(ShapeId.from("com.example#BasicItem"), StructureShape.class);
    List<MemberShape> members =
        List.of(
            basicItem.getMember("name").orElseThrow(), basicItem.getMember("count").orElseThrow());
    List<MapEntry> entries =
        ElixirJsonCodecIr.bodyMapEntries(
            model, httpIndex, provider, "Types", members, "record", "event_stream");
    assertThat(entries).hasSize(2);
    for (MapEntry entry : entries) {
      assertThat(ElixirRenderer.renderExpression(entry.key())).isNotBlank();
      assertThat(ElixirRenderer.renderExpression(entry.value())).isNotBlank();
    }
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
}
