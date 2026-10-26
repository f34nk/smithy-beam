package io.smithy.beam.gleam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class GleamRestJsonDslTest {

  @Test
  void emitsRestJsonCodecWithEnumAndStructureHelpers() {
    Model model =
        Model.assembler()
            .addImport(getClass().getResource("/model/protocol_rest_json_fixture.smithy"))
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
            .withMember("edition", "2026")
            .build();
    new GleamClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

    String codec = manifest.expectFileString("demo_rest_json_rest_json_1.gleam");
    assertThat(codec)
        .contains("import gleam/json")
        .contains("pub fn item_status_to_json(")
        .contains("pub fn json_to_item_status(")
        .contains("Unknown(value) -> json.string(value)")
        .contains("pub fn item_payload_to_json(")
        .contains("pub fn json_to_item_payload(")
        .contains("pub fn item_payload_decoder(")
        .doesNotContain("panic")
        .doesNotContain("let assert");
  }

  @Test
  void enumUnknownRoundTripHelpersPresent() {
    Model model =
        Model.assembler()
            .addUnparsedModel(
                "enum.smithy",
                """
                $version: "2"
                namespace com.example
                use aws.protocols#restJson1

                @restJson1
                service S {
                    operations: [Op]
                }

                @http(method: "GET", uri: "/x", code: 200)
                operation Op {
                    output := {
                        status: Status
                    }
                }

                enum Status {
                    ACTIVE
                    INACTIVE
                }
                """)
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "com.example#S")
            .withMember("edition", "2026")
            .build();
    new GleamClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

    String codec = manifest.expectFileString("s_rest_json_1.gleam");
    assertThat(codec)
        .contains("Active -> json.string(\"ACTIVE\")")
        .contains("Inactive -> json.string(\"INACTIVE\")")
        .contains("Unknown(value) -> json.string(value)")
        .contains("\"ACTIVE\" -> Active")
        .contains("other -> Unknown(other)");
  }
}
