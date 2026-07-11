package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class IdempotencyTokenTest {

  private static final String MODEL =
      """
            $version: "2"
            namespace smithy.beam.test.idempotencytoken

            use aws.protocols#restJson1

            @restJson1
            service IdempotencyTokenService {
                version: "2026"
                operations: [CreateResource]
            }

            @http(method: "POST", uri: "/resources")
            operation CreateResource {
                input: CreateResourceInput
                output: CreateResourceOutput
            }

            structure CreateResourceInput {
                name: String

                @idempotencyToken
                clientToken: String
            }

            structure CreateResourceOutput {
                id: String
            }
            """;

  private static Model loadModel() {
    return Model.assembler()
        .addUnparsedModel("test.smithy", MODEL)
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static MockManifest runErlangPlugin(Model model) {
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember(
                            "service", "smithy.beam.test.idempotencytoken#IdempotencyTokenService")
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest;
  }

  private static MockManifest runElixirPlugin(Model model) {
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember(
                            "service", "smithy.beam.test.idempotencytoken#IdempotencyTokenService")
                        .withMember("edition", "2026")
                        .build())
                .build());
    return manifest;
  }

  @Test
  void idempotencyTokenAutoFillInErlangRequestEncoder() {
    MockManifest manifest = runErlangPlugin(loadModel());
    String codec = manifest.getFileString("idempotency_token_service_rest_json_1.erl").orElse("");
    assertThat(codec).contains("encode_create_resource_request(");
    assertThat(codec).contains("Input1 =");
    assertThat(codec).contains("case Input#create_resource_input.client_token of");
    assertThat(codec).contains("Input#create_resource_input{client_token = generate_uuid()}");
    assertThat(codec).contains("ClientToken = Input1#create_resource_input.client_token");
    assertThat(codec).contains("generate_uuid() ->");
    assertThat(codec).contains("uuid:to_string(uuid:v4())");
  }

  @Test
  void idempotencyTokenAutoFillInElixirRequestEncoder() {
    MockManifest manifest = runElixirPlugin(loadModel());
    String codec = manifest.getFileString("idempotency_token_service_rest_json_1.ex").orElse("");
    assertThat(codec).contains("def encode_create_resource_request(input) do");
    assertThat(codec).contains("case input.client_token do");
    assertThat(codec).contains("nil -> %{input | client_token: generate_uuid()}");
    assertThat(codec).contains("defp generate_uuid do");
  }
}
