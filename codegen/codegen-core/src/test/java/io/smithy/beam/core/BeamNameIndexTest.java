package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.ReservedWords;
import software.amazon.smithy.codegen.core.ReservedWordsBuilder;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class BeamNameIndexTest {

  private static final ShapeId SERVICE_ID = ShapeId.from("example.com#Catalog");
  private static final ShapeId ITEM_ID = ShapeId.from("example.com#Item");
  private static final ShapeId NAME_MEMBER = ShapeId.from("example.com#Item$name");
  private static final ShapeId STATUS_ID = ShapeId.from("example.com#Status");
  private static final ShapeId GET_ITEM_ID = ShapeId.from("example.com#GetItem");
  private static final ShapeId CHOICE_ID = ShapeId.from("example.com#Choice");
  private static final ShapeId CHOICE_A = ShapeId.from("example.com#Choice$a");

  @Test
  void indexesTypeFieldTagEnumAndFunctionNamesInOneWalk() {
    Model model = sampleModel();
    ServiceShape service = model.expectShape(SERVICE_ID, ServiceShape.class);
    ReservedWords keywords = keywords();

    BeamNameIndex index =
        BeamNameIndex.of(
            model, service, BeamNameIndex.Escapers.of(keywords, keywords, keywords, keywords));

    assertThat(index.typeNames().get(ITEM_ID)).isEqualTo("item");
    assertThat(index.fieldNames().get(NAME_MEMBER)).isEqualTo("name");
    assertThat(index.unionTagNames().get(CHOICE_A)).isEqualTo("a");
    assertThat(index.enumAtomNames().get(STATUS_ID)).containsEntry("ACTIVE", "active");
    assertThat(index.serviceFunctionNames().get(GET_ITEM_ID)).isEqualTo("get_item");
    assertThat(index.moduleNames()).isEmpty();
  }

  @Test
  void indexesModuleNamesWhenModuleEscaperProvided() {
    Model model = sampleModel();
    ServiceShape service = model.expectShape(SERVICE_ID, ServiceShape.class);
    ReservedWords keywords = keywords();

    BeamNameIndex index =
        BeamNameIndex.of(
            model,
            service,
            BeamNameIndex.Escapers.withModuleNames(
                keywords, keywords, keywords, keywords, keywords));

    assertThat(index.moduleNames().get(ITEM_ID)).isEqualTo("item");
    assertThat(index.moduleNames().get(STATUS_ID)).isEqualTo("status");
    assertThat(index.moduleNames()).doesNotContainKey(CHOICE_ID);
  }

  @Test
  void escapesReservedFieldNames() {
    Model model =
        Model.assembler()
            .addUnparsedModel(
                "test.smithy",
                """
                $version: "2"
                namespace example.com

                service Catalog {
                    operations: [GetItem]
                }

                operation GetItem {
                    input := {
                        end: String
                    }
                    output := {}
                }
                """)
            .assemble()
            .unwrap();
    ServiceShape service = model.expectShape(SERVICE_ID, ServiceShape.class);
    ReservedWords keywords = keywords();
    ShapeId endMember = ShapeId.from("example.com#GetItemInput$end");

    BeamNameIndex index =
        BeamNameIndex.of(
            model, service, BeamNameIndex.Escapers.of(keywords, keywords, keywords, keywords));

    assertThat(index.fieldNames().get(endMember)).isEqualTo("end_");
  }

  private static ReservedWords keywords() {
    return new ReservedWordsBuilder().put("end", "end_").put("case", "case_").build();
  }

  private static Model sampleModel() {
    return Model.assembler()
        .addUnparsedModel(
            "test.smithy",
            """
            $version: "2"
            namespace example.com

            service Catalog {
                operations: [GetItem]
            }

            operation GetItem {
                input := {
                    id: String
                }
                output := {
                    item: Item
                    choice: Choice
                    status: Status
                }
            }

            structure Item {
                name: String
            }

            union Choice {
                a: String
                b: Integer
            }

            enum Status {
                ACTIVE
                INACTIVE
            }
            """)
        .assemble()
        .unwrap();
  }
}
