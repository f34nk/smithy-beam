package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ElixirHandlerDiscoveryEmitterTest {

  private static Model basicModel() {
    return Model.assembler()
        .addUnparsedModel(
            "basic.smithy",
            """
                        $version: "2"
                        namespace smithy.beam.demo.basic

                        string BasicString

                        service BasicService {
                            version: "2026"
                            operations: [GetTypeClosure]
                        }

                        @readonly
                        operation GetTypeClosure {
                            output: TypeClosureOutput
                        }

                        structure TypeClosureOutput {
                            basicString: BasicString
                        }
                        """)
        .assemble()
        .unwrap();
  }

  @Test
  void discoveryHelpersAreSiblingModuleFunctions() {
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.basic#BasicService")
            .withMember("edition", "2026")
            .build();
    new ElixirServerPlugin()
        .execute(
            PluginContext.builder()
                .model(basicModel())
                .fileManifest(manifest)
                .settings(settings)
                .build());

    String source = manifest.expectFileString("basic_service_server.ex");
    int resolveStart = source.indexOf("defp resolve_impl");
    int initSpecStart = source.indexOf("@spec init_handlers()", resolveStart);
    int initStart = source.indexOf("def init_handlers do", initSpecStart);
    int dispatchStart = source.indexOf("defp dispatch_handler", initStart);
    assertThat(resolveStart).isGreaterThan(-1);
    assertThat(initSpecStart).isGreaterThan(resolveStart);
    assertThat(initStart).isGreaterThan(initSpecStart);
    assertThat(dispatchStart).isGreaterThan(initStart);

    String resolveBlock = source.substring(resolveStart, initSpecStart);
    assertThat(resolveBlock).contains("case Code.ensure_loaded(impl) do");
    assertThat(resolveBlock).contains("{:module, _} ->");
    assertThat(resolveBlock).contains("{:error, _} ->");
    assertThat(resolveBlock).contains("if function_exported?(impl, fun, 3) do");
    assertThat(resolveBlock).contains("Map.put(acc, fun, Function.capture(impl, fun, 3))");
    assertThat(resolveBlock).doesNotContain("when function_exported?");
    assertThat(resolveBlock.trim()).endsWith("end");

    assertThat(source.substring(initStart, dispatchStart).trim())
        .startsWith("def init_handlers do");
    assertThat(source.substring(dispatchStart)).contains("defp dispatch_handler");
    assertThat(source).doesNotContain("    def init_handlers do");
    assertThat(source).doesNotContain("      defp dispatch_handler");
  }
}
