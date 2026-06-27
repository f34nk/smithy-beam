package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class HttpQueryParamsTest {

  private static final String MODEL =
      """
            $version: "2"
            namespace smithy.beam.test.httpqueryparams

            use aws.protocols#restJson1

            @restJson1
            service HttpQueryParamsService {
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
                @httpQuery("limit")
                limit: Integer

                @httpQueryParams
                extra: QueryStringMap
            }

            map QueryStringMap {
                key: String
                value: String
            }

            structure ListItemsOutput {
                items: StringList
            }

            list StringList {
                member: String
            }
            """;

  @Test
  void queryParamsMemberExpandsMapIntoQueryString() {
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
                        .withMember(
                            "service", "smithy.beam.test.httpqueryparams#HttpQueryParamsService")
                        .withMember("edition", "2026")
                        .build())
                .build());

    String codec = manifest.getFileString("http_query_params_service_rest_json_1.erl").orElse("");
    assertThat(codec).contains("maps:to_list(");
    assertThat(codec).contains("QueryExtra");
  }

  @Test
  void queryParamsMemberExpandsMapIntoQueryStringInElixir() {
    Model model =
        Model.assembler()
            .addUnparsedModel("test.smithy", MODEL)
            .discoverModels()
            .assemble()
            .unwrap();
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember(
                            "service", "smithy.beam.test.httpqueryparams#HttpQueryParamsService")
                        .withMember("edition", "2026")
                        .build())
                .build());

    String codec = manifest.getFileString("http_query_params_service_rest_json_1.ex").orElse("");
    assertThat(codec).contains("Map.to_list(");
    assertThat(codec).contains("query_extra");
    assertThat(codec).contains("Enum.concat(");
  }
}
