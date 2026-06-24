package io.smithy.beam.erlang;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangHandlerDiscoveryEmitterTest {

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
        new ErlangServerPlugin().execute(PluginContext.builder()
                .model(basicModel())
                .fileManifest(manifest)
                .settings(settings)
                .build());

        String source = manifest.expectFileString("basic_service_server.erl");
        int resolveStart = source.indexOf("resolve_impl(Impl) ->");
        int initSpecStart = source.indexOf("-spec init_handlers()", resolveStart);
        int initStart = source.indexOf("init_handlers() ->", initSpecStart);
        int dispatchStart = source.indexOf("dispatch_handler(Fun, Ctx, Input, Meta) ->", initStart);
        assertThat(resolveStart).isGreaterThan(-1);
        assertThat(initSpecStart).isGreaterThan(resolveStart);
        assertThat(initStart).isGreaterThan(initSpecStart);
        assertThat(dispatchStart).isGreaterThan(initStart);

        String resolveBlock = source.substring(resolveStart, initSpecStart);
        assertThat(resolveBlock).contains("case code:ensure_loaded(Impl) of");
        assertThat(resolveBlock).contains("{module, Impl} ->");
        assertThat(resolveBlock).contains("{error, _} ->");
        assertThat(resolveBlock.trim()).endsWith("end.");

        assertThat(source.substring(initStart, dispatchStart).trim()).startsWith("init_handlers() ->");
        assertThat(source.substring(dispatchStart)).contains("dispatch_handler(Fun, Ctx, Input, Meta) ->");
    }
}
