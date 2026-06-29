package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExModule;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ElixirServerDirectedCodegenTest {

  @Test
  void serverModuleComposedViaIr() {
    Model model = basicModel();
    MockManifest manifest = new MockManifest();
    new ElixirServerPlugin()
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

    String server = manifest.expectFileString("basic_service_server.ex");
    org.assertj.core.api.Assertions.assertThat(server.stripLeading())
        .startsWith("defmodule BasicServiceServer do");
    org.assertj.core.api.Assertions.assertThat(server).contains("@behaviour BasicServiceBehaviour");
    org.assertj.core.api.Assertions.assertThat(server).contains("@default_impl BasicServiceImpl");
    org.assertj.core.api.Assertions.assertThat(server)
        .contains("@handlers_key {BasicServiceServer, :handlers}");
    org.assertj.core.api.Assertions.assertThat(server).contains("def init_handlers do");
    org.assertj.core.api.Assertions.assertThat(server).contains("defp resolve_impl(impl) do");
    org.assertj.core.api.Assertions.assertThat(server)
        .contains("defp dispatch_handler(fun, ctx, input, meta) do");
    org.assertj.core.api.Assertions.assertThat(server)
        .contains("dispatch_handler(:handle_get_type_closure, ctx, input, meta)");
    int moduleIndex = server.indexOf("defmodule BasicServiceServer do");
    int behaviourIndex = server.indexOf("@behaviour BasicServiceBehaviour");
    int handleIndex = server.indexOf("def handle_get_type_closure");
    org.assertj.core.api.Assertions.assertThat(moduleIndex).isLessThan(behaviourIndex);
    org.assertj.core.api.Assertions.assertThat(behaviourIndex).isLessThan(handleIndex);
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
