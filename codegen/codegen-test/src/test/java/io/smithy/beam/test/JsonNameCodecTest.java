package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class JsonNameCodecTest {

  private static final String MODEL =
      """
            $version: "2"
            namespace smithy.beam.test.jsonname

            use aws.protocols#restJson1

            @restJson1
            service JsonNameService {
                version: "2026"
                operations: [GetItem]
            }

            @http(method: "GET", uri: "/items/{id}")
            @readonly
            operation GetItem {
                input: GetItemInput
                output: GetItemOutput
            }

            structure GetItemInput {
                @required @httpLabel
                id: String
            }

            structure GetItemOutput {
                @jsonName("displayName")
                name: String
            }
            """;

  @Test
  void jsonNameUsedAsWireKey() {
    Model model =
        Model.assembler()
            .addUnparsedModel("test.smithy", MODEL)
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", "smithy.beam.test.jsonname#JsonNameService")
                        .withMember("edition", "2026")
                        .build())
                .build());

    String codec = manifest.getFileString("json_name_service_rest_json_1.erl").orElse("");
    assertThat(codec).contains("<<\"displayName\">>");
    assertThat(codec).doesNotContain("<<\"name\">>");
  }
}
