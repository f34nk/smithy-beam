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
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ElixirStructureHelperIrTest {
  private static StructureShape basicItem;
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
    basicItem = model.expectShape(ShapeId.from("com.example#BasicItem"), StructureShape.class);
  }

  @Test
  void structureDecodeEncodeIsStructural() {
    Model model = model();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    List<Function> functions =
        ElixirStructureHelperDsl.structureDecodeEncode(model, httpIndex, basicItem, provider);
    assertThat(functions).hasSizeGreaterThanOrEqualTo(4);
    for (Function fn : functions) {
      ElixirDslTestSupport.assertStructural(fn);
    }
  }

  @Test
  void structureListDecodeEncodeIsStructural() {
    List<Function> functions = ElixirStructureHelperDsl.structureListDecodeEncode(basicItem);
    assertThat(functions).hasSizeGreaterThanOrEqualTo(4);
    for (Function fn : functions) {
      ElixirDslTestSupport.assertStructural(fn);
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
