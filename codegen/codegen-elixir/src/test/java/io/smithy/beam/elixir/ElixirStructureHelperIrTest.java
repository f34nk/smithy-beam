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
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

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
  void structureDecodeEncodeAsStringMatchesGolden() throws IOException {
    Model model = model();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    List<ExFunction> functions =
        ElixirStructureHelperIr.structureDecodeEncode(model, httpIndex, basicItem, provider);
    assertThat(functions).hasSize(2);
    ElixirIrTestSupport.assertStructural(functions.get(0));
    ElixirIrTestSupport.assertStructural(functions.get(1));
    String combined = functions.get(0).asString() + "\n\n" + functions.get(1).asString();
    assertThat(combined)
        .isEqualTo(readExpectedString("ir/structure_decode_encode_basic_item.expected.ex"));
  }

  @Test
  void structureListDecodeEncodeAsStringMatchesGolden() throws IOException {
    List<ExFunction> functions = ElixirStructureHelperIr.structureListDecodeEncode(basicItem);
    assertThat(functions).hasSize(2);
    ElixirIrTestSupport.assertStructural(functions.get(0));
    ElixirIrTestSupport.assertStructural(functions.get(1));
    String combined = functions.get(0).asString() + "\n\n" + functions.get(1).asString();
    assertThat(combined)
        .isEqualTo(readExpectedString("ir/structure_list_decode_encode_item.expected.ex"));
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

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirStructureHelperIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
