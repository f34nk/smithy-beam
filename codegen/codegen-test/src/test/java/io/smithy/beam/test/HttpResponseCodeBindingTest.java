package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class HttpResponseCodeBindingTest {

  private static final String MODEL =
      """
            $version: "2"
            namespace smithy.beam.test.httpresponsecode

            use aws.protocols#restJson1

            @restJson1
            service HttpResponseCodeService {
                version: "2026"
                operations: [GetWidget]
            }

            @http(method: "GET", uri: "/widgets/{id}")
            @readonly
            operation GetWidget {
                input: GetWidgetInput
                output: GetWidgetOutput
            }

            structure GetWidgetInput {
                @required @httpLabel
                id: String
            }

            structure GetWidgetOutput {
                @httpResponseCode
                statusCode: Integer

                name: String
            }
            """;

  @Test
  void responseCodeMemberReceivesHttpStatusInErlangCodec() {
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
                            "service", "smithy.beam.test.httpresponsecode#HttpResponseCodeService")
                        .withMember("edition", "2026")
                        .build())
                .build());

    String codec = manifest.getFileString("http_response_code_service_rest_json_1.erl").orElse("");
    assertThat(codec).contains("HttpStatus");
    assertThat(codec).contains("status_code = HttpStatus");
  }

  @Test
  void responseCodeMemberReceivesHttpStatusInElixirCodec() {
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
                            "service", "smithy.beam.test.httpresponsecode#HttpResponseCodeService")
                        .withMember("edition", "2026")
                        .build())
                .build());

    String codec = manifest.getFileString("http_response_code_service_rest_json_1.ex").orElse("");
    assertThat(codec).contains("http_status");
    assertThat(codec).contains("status_code: http_status");
  }
}
