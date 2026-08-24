package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ElixirClientDirectedCodegenTest {

  @Test
  void clientModuleComposedViaIr() {
    Model model = basicModel();
    MockManifest manifest = new MockManifest();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember("service", "smithy.beam.demo.basic#BasicService")
                        .withMember("edition", "2026")
                        .build())
                .build());

    String client = manifest.expectFileString("basic_service_client.ex");
    assertThat(client.stripLeading()).startsWith("defmodule");
    assertThat(client).contains("@type client_config :: map()");
    assertThat(client).contains("def get_type_closure(");
    assertThat(client).contains("@spec get_type_closure(");
    assertThat(client).contains("@spec get_type_closure(client_config(),");
    int typeIndex = client.indexOf("@type client_config :: map()");
    int firstDefIndex = client.indexOf("def get_type_closure(");
    assertThat(typeIndex).isLessThan(firstDefIndex);
  }

  private static Model basicModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.demo.basic

                use aws.protocols#restJson1

                @restJson1
                service BasicService {
                    version: "2026"
                    operations: [GetTypeClosure]
                }

                @readonly
                @http(method: "GET", uri: "/type-closure", code: 200)
                operation GetTypeClosure {
                    input: GetTypeClosureInput
                    output: GetTypeClosureOutput
                }

                structure GetTypeClosureInput {}
                structure GetTypeClosureOutput {}
                """;
    return Model.assembler()
        .addUnparsedModel("basic.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
  }
}
