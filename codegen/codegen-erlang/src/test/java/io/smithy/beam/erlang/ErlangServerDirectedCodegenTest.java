package io.smithy.beam.erlang;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangServerDirectedCodegenTest {

    @Test
    void serverModuleComposedViaIr() {
        Model model = basicModel();
        MockManifest manifest = new MockManifest();
        new ErlangServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", "smithy.beam.demo.basic#BasicService")
                        .withMember("edition", "2026")
                        .build())
                .build());

        String server = manifest.expectFileString("basic_service_server.erl");
        assertThat(server.stripLeading()).startsWith("%% Generated Erlang server dispatcher for");
        assertThat(server).contains("-module(basic_service_server).");
        assertThat(server).contains("-behaviour(basic_service_behaviour).");
        assertThat(server).contains("-include(\"basic_service_types.hrl\").");
        assertThat(server).contains("-export([init_handlers/0");
        assertThat(server).contains("-define(DEFAULT_IMPL, basic_service_impl).");
        assertThat(server).contains("resolve_impl(Impl) ->");
        assertThat(server).contains("init_handlers() ->");
        assertThat(server).contains("dispatch_handler(Fun, Ctx, Input, Meta) ->");
        assertThat(server).contains("handle_get_type_closure(Ctx, Input, Meta) ->");
        int moduleIndex = server.indexOf("-module(basic_service_server).");
        int behaviourIndex = server.indexOf("-behaviour(basic_service_behaviour).");
        int exportIndex = server.indexOf("-export([init_handlers/0");
        assertThat(moduleIndex).isLessThan(behaviourIndex);
        assertThat(behaviourIndex).isLessThan(exportIndex);
    }

    private static Model basicModel() {
        String idl = """
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
