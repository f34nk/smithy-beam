package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ErlangVariableNamingTest {

  private static final String MODEL =
      """
            $version: "2"
            namespace smithy.beam.test.erlangvars

            use aws.protocols#restJson1

            @restJson1
            service ErlangVarService {
                version: "2026"
                operations: [ListItems]
            }

            @http(method: "GET", uri: "/items")
            @readonly
            operation ListItems {
                input: ListItemsInput
                output: ListItemsOutput
            }

            structure ListItemsInput {
                @httpQuery("nextToken")
                next_token: String
                @httpQuery("pageSize")
                page_size: Integer
            }

            structure ListItemsOutput {
                items: StringList
            }

            list StringList {
                member: String
            }
            """;

  @Test
  void multiWordFieldsBindAsCamelCaseVariables() {
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
                        .withMember("service", "smithy.beam.test.erlangvars#ErlangVarService")
                        .withMember("edition", "2026")
                        .build())
                .build());
    String codec = manifest.getFileString("erlang_var_service_rest_json_1.erl").orElse("");
    assertThat(codec).contains("next_token = NextToken");
    assertThat(codec).contains("page_size = PageSize");
    assertThat(codec).doesNotContain("Next_token");
    assertThat(codec).doesNotContain("Page_size");
  }

  @Test
  void responseDecoderUsesHeadersNotIgnoredHeaders() {
    Model model =
        Model.assembler()
            .addImport(
                ErlangRestJson1CodecTest.class.getResource(
                    "/model/protocol_rest_json_fixture.smithy"))
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
                        .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
                        .withMember("edition", "2026")
                        .build())
                .build());
    String codec = manifest.getFileString("demo_rest_json_rest_json_1.erl").orElse("");
    assertThat(codec).contains("headers = Headers, body = Body");
    assertThat(codec).contains("proplists:get_value(<<\"ETag\">>, Headers, undefined)");
    assertThat(codec).doesNotContain("headers = _Headers");
  }
}
