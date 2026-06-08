package io.smithy.beam.elixir;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

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
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.basic#BasicService")
                .withMember("edition", "2026")
                .build();
        new ElixirServerPlugin().execute(PluginContext.builder()
                .model(basicModel())
                .fileManifest(manifest)
                .settings(settings)
                .build());

        String source = manifest.expectFileString("basic_service_server.ex");
        int resolveStart = source.indexOf("  defp resolve_impl(impl) do");
        int initSpecStart = source.indexOf("  @spec init_handlers()", resolveStart);
        int initStart = source.indexOf("  def init_handlers do", initSpecStart);
        int dispatchStart = source.indexOf("  defp dispatch_handler(fun, ctx, input, meta) do", initStart);
        assertThat(resolveStart).isGreaterThan(-1);
        assertThat(initSpecStart).isGreaterThan(resolveStart);
        assertThat(initStart).isGreaterThan(initSpecStart);
        assertThat(dispatchStart).isGreaterThan(initStart);

        String resolveBlock = source.substring(resolveStart, initSpecStart);
        assertThat(resolveBlock).contains("case Code.ensure_loaded(impl) do");
        assertThat(resolveBlock).contains("{:module, _} ->");
        assertThat(resolveBlock).contains("{:error, _} ->");
        assertThat(resolveBlock.trim()).endsWith("end");

        assertThat(source.substring(initStart, dispatchStart).trim()).startsWith("def init_handlers do");
        assertThat(source.substring(dispatchStart)).contains("defp dispatch_handler(fun, ctx, input, meta) do");
        assertThat(source).doesNotContain("    def init_handlers do");
        assertThat(source).doesNotContain("      defp dispatch_handler");
    }
}
