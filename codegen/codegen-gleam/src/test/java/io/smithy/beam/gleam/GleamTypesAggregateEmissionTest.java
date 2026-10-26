package io.smithy.beam.gleam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class GleamTypesAggregateEmissionTest {

  @Test
  void emitsEnumsUnionsStructuresAndErrors() {
    Model model =
        Model.assembler()
            .addUnparsedModel(
                "test.smithy",
                """
                $version: "2"
                namespace com.example

                use smithy.api#error

                service AggregateService {
                    operations: [GetAggregate]
                }

                operation GetAggregate {
                    input: GetAggregateInput
                    output: GetAggregateOutput
                    errors: [NotFound]
                }

                structure GetAggregateInput {}

                structure GetAggregateOutput {
                    status: Status
                    priority: Priority
                    choice: Choice
                    item: Item
                }

                enum Status {
                    ACTIVE
                    INACTIVE
                }

                intEnum Priority {
                    LOW = 1
                    HIGH = 2
                }

                union Choice {
                    text: NamedString
                    count: NamedInt
                }

                structure Item {
                    @required
                    name: NamedString
                    count: NamedInt
                }

                @error("client")
                structure NotFound {
                    message: NamedString
                }

                string NamedString
                integer NamedInt
                """)
            .assemble()
            .unwrap();

    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "com.example#AggregateService")
            .withMember("edition", "2026")
            .build();
    PluginContext context =
        PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build();

    new GleamTypesPlugin().execute(context);

    String content = manifest.expectFileString("aggregate_service_types.gleam");
    assertThat(content)
        .contains("import gleam/option.{type Option}")
        .contains("pub type NamedString = String")
        .contains("pub type NamedInt = Int")
        .contains("pub type Status {")
        .contains("  Active")
        .contains("  Inactive")
        .contains("  StatusUnknown(String)")
        .contains("pub type Priority {")
        .contains("  Low")
        .contains("  High")
        .contains("  PriorityUnknown(Int)")
        .contains("pub type Choice {")
        .contains("  Text(NamedString)")
        .contains("  Count(NamedInt)")
        .contains("  ChoiceUnknown(String)")
        .contains("pub type Item {")
        .contains("  Item(name: NamedString, count: Option(NamedInt))")
        .contains("pub type NotFound {")
        .contains("  NotFound(message: Option(NamedString))");
  }
}
