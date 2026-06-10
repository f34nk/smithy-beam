package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class HostLabelTest {

    private static final String MODEL = """
            $version: "2"
            namespace smithy.beam.test.hostlabel

            use aws.protocols#restJson1

            @restJson1
            service HostLabelService {
                version: "2026"
                operations: [GetTenantData]
            }

            @endpoint(hostPrefix: "{tenant}.")
            @http(method: "GET", uri: "/data/{tenant}")
            operation GetTenantData {
                input: GetTenantDataInput
                output: GetTenantDataOutput
            }

            structure GetTenantDataInput {
                @required
                @hostLabel
                @httpLabel
                tenant: String
            }

            structure GetTenantDataOutput {
                value: String
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
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service",
                                "smithy.beam.test.hostlabel#HostLabelService")
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    private static MockManifest runElixirPlugin(Model model) {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service",
                                "smithy.beam.test.hostlabel#HostLabelService")
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    @Test
    void hostLabelSubstitutionInErlangCodecAndDispatch() {
        MockManifest manifest = runErlangPlugin(loadModel());
        String codec = manifest.getFileString("host_label_service_rest_json_1.erl").orElse("");
        assertThat(codec).contains("encode_get_tenant_data_request(");
        assertThat(codec).contains("Config, Input = #get_tenant_data_input{");
        assertThat(codec).contains("Host = build_host(Input, Config)");
        assertThat(codec).contains("host = Host");
        assertThat(codec).contains("build_host(#get_tenant_data_input{");
        assertThat(codec).contains("uri_encode(to_binary(Tenant))");

        String http = manifest.getFileString("runtime_http.erl").orElse("");
        assertThat(http).contains("host = Host");
        assertThat(http).contains("split_base_url(BaseUrl)");

        String client = manifest.getFileString("host_label_service_client.erl").orElse("");
        assertThat(client).contains("encode_get_tenant_data_request(");
        assertThat(client).contains("Config, Input");
    }

    @Test
    void hostLabelSubstitutionInElixirCodecAndDispatch() {
        MockManifest manifest = runElixirPlugin(loadModel());
        String codec = manifest.getFileString("host_label_service_rest_json_1.ex").orElse("");
        assertThat(codec).contains("def encode_get_tenant_data_request(config, input)");
        assertThat(codec).contains("host = build_host(input, config)");
        assertThat(codec).contains("defp build_host(%Types.GetTenantDataInput{");
        assertThat(codec).contains("URI.encode(to_string(tenant))");

        String http = manifest.getFileString("runtime_http.ex").orElse("");
        assertThat(http).contains("req.host");
        assertThat(http).contains("split_base_url(base_url)");

        String client = manifest.getFileString("host_label_service_client.ex").orElse("");
        assertThat(client).contains("encode_get_tenant_data_request(config, input)");
    }
}
