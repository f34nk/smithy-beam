package io.smithy.beam.elixir;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class ElixirHttpDispatchEmitterTest {

    private static Model basicModel() {
        return Model.assembler()
                .addUnparsedModel(
                        "basic.smithy",
                        """
                        $version: "2"
                        namespace smithy.beam.demo.basic

                        string BasicString
                        integer BasicInteger

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
                            basicInteger: BasicInteger
                        }
                        """)
                .assemble()
                .unwrap();
    }

    @Test
    void dispatchSignedBaseUrlCaseClausesAreSiblingsAtModuleScope() {
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.basic#BasicService")
                .withMember("edition", "2026")
                .build();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(basicModel())
                .fileManifest(manifest)
                .settings(settings)
                .build());

        String source = manifest.expectFileString("runtime_http.ex");
        int dispatchStart = source.indexOf("defp dispatch_signed(");
        int splitStart = source.indexOf("defp split_base_url", dispatchStart);
        int reqClientStart = source.indexOf("defmodule ReqClient", dispatchStart);
        assertThat(dispatchStart).isGreaterThan(-1);
        assertThat(splitStart).isGreaterThan(dispatchStart);
        assertThat(reqClientStart).isGreaterThan(splitStart);

        String dispatchBody = source.substring(dispatchStart, splitStart);
        assertThat(dispatchBody).contains("case Map.get(config, :base_url) do");
        assertThat(dispatchBody).contains("url ->");
        assertThat(dispatchBody).contains("_ -> RuntimeHelpers.resolve_base_url(config)");
        assertThat(dispatchBody).doesNotContain("url -> url");
        assertThat(dispatchBody).contains("{scheme, default_authority} = split_base_url(base_url)");
        assertThat(dispatchBody).contains("case http_client.request(req_opts) do");
        assertThat(dispatchBody.trim()).endsWith("end");
        assertThat(source.substring(splitStart)).contains("defp split_base_url(\"\")");
        assertThat(source).contains("defmodule ReqClient do");
    }
}
