package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ErlangClientDirectedCodegenTest {

  @Test
  void clientModuleComposedViaIr() {
    Model model = basicModel();
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
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

    String client = manifest.expectFileString("basic_service_client.erl");
    assertThat(client.stripLeading()).startsWith("%% Generated Erlang client for");
    assertThat(client).contains("-module(basic_service_client).");
    assertThat(client).contains("-include(\"basic_service_types.hrl\").");
    assertThat(client).contains("-export([");
    assertThat(client).contains("-type client_config() :: #{binary() => term()}.");
    assertThat(client).contains("get_type_closure(Config, Input) ->");
    assertThat(client)
        .contains("-spec get_type_closure(client_config(), get_type_closure_input()) ->");
    int moduleIndex = client.indexOf("-module(basic_service_client).");
    int includeIndex = client.indexOf("-include(\"basic_service_types.hrl\").");
    int exportIndex = client.indexOf("-export([");
    assertThat(moduleIndex).isLessThan(exportIndex);
    assertThat(exportIndex).isLessThan(includeIndex);
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
