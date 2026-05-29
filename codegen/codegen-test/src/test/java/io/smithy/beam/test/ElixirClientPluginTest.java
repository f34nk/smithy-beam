package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.elixir.ElixirServerPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.codegen.core.CodegenException;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElixirClientPluginTest {

    private static final String TYPES_FILE = "basic_types.ex";
    private static final String CLIENT_FILE = "basic_service_client.ex";

    private static Model loadModel() {
        URL resource = ElixirClientPluginTest.class.getResource("/model/basic.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static PluginContext buildContext(Model model, FileManifest manifest) {
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.basic#BasicService")
                .withMember("edition", "2026")
                .build();
        return PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build();
    }

    @Test
    void emitsTypesModuleAndClientStubOnManifest() {
        Model model = loadModel();
        MockManifest manifest = new MockManifest();

        new ElixirClientPlugin().execute(buildContext(model, manifest));

        assertThat(manifest.expectFileString(TYPES_FILE)).contains("basic_string");
        assertClientStubHeaderOrder(manifest.expectFileString(CLIENT_FILE));
    }

    @Test
    void defaultsToOnlyServiceWhenServiceSettingOmitted() {
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder().withMember("edition", "2026").build();
        PluginContext context = PluginContext.builder()
                .model(loadModel())
                .fileManifest(manifest)
                .settings(settings)
                .build();
        new ElixirClientPlugin().execute(context);
        assertThat(manifest.expectFileString(TYPES_FILE)).contains("basic_string");
        assertClientStubHeaderOrder(manifest.expectFileString(CLIENT_FILE));
    }

    private static void assertClientStubHeaderOrder(String clientSource) {
        assertThat(clientSource).contains("defmodule BasicServiceClient do");
        assertThat(clientSource).contains("@moduledoc \"\"\"");
        assertThat(clientSource).contains("alias BasicTypes");
        assertThat(clientSource).contains("@spec get_type_closure(client_config(), BasicTypes.GetTypeClosureInput.t())");
        assertThat(clientSource).contains("def get_type_closure(_config, _input), do: {:error, :not_implemented}");
        assertThat(clientSource).contains("@type client_config :: map()");
        int moduleIndex = clientSource.indexOf("defmodule BasicServiceClient do");
        int moduledocIndex = clientSource.indexOf("@moduledoc \"\"\"");
        int aliasIndex = clientSource.indexOf("alias BasicTypes");
        int specIndex = clientSource.indexOf("@spec get_type_closure");
        assertThat(moduleIndex).isLessThan(moduledocIndex);
        assertThat(moduledocIndex).isLessThan(aliasIndex);
        assertThat(aliasIndex).isLessThan(specIndex);
        assertThat(clientSource.stripLeading()).startsWith("defmodule");
    }

    @Test
    void multipleServicesWithoutExplicitServiceSettingFails() {
        URL resource = ElixirClientPluginTest.class.getResource("/model/multi_service.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        ObjectNode settings = ObjectNode.builder().withMember("edition", "2026").build();
        PluginContext context = PluginContext.builder()
                .model(model)
                .fileManifest(new MockManifest())
                .settings(settings)
                .build();
        assertThatThrownBy(() -> new ElixirClientPlugin().execute(context))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("service");
    }

    @Test
    void missingEditionFails() {
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.basic#BasicService")
                .build();
        PluginContext context = PluginContext.builder()
                .model(loadModel())
                .fileManifest(manifest)
                .settings(settings)
                .build();
        assertThatThrownBy(() -> new ElixirClientPlugin().execute(context))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("edition");
    }

    @Test
    void relativeDateAndRelativeVersionWithoutProtocolDoNotChangeTypesOrClientStubOutput() {
        URL resource = ElixirClientPluginTest.class.getResource("/model/multi_service.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest baseline = new MockManifest();
        ObjectNode baselineSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.multi#ServiceA")
                .withMember("edition", "2026")
                .build();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(baseline)
                .settings(baselineSettings)
                .build());
        MockManifest extended = new MockManifest();
        ObjectNode extendedSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.multi#ServiceA")
                .withMember("edition", "2026")
                .withMember("relativeDate", "2026-01-01")
                .withMember("relativeVersion", "1.0.0")
                .build();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(extended)
                .settings(extendedSettings)
                .build());
        assertThat(extended.expectFileString("multi_types.ex"))
                .isEqualTo(baseline.expectFileString("multi_types.ex"));
        assertThat(extended.expectFileString("multi_service_client.ex"))
                .isEqualTo(baseline.expectFileString("multi_service_client.ex"));
    }

    @Test
    void explicitInvalidProtocolFailsWithCodegenException() {
        URL resource = ElixirClientPluginTest.class.getResource("/model/multi_service.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.multi#ServiceA")
                .withMember("edition", "2026")
                .withMember("protocol", "smithy.api#String")
                .withMember("relativeDate", "2026-01-01")
                .withMember("relativeVersion", "1.0.0")
                .build();
        assertThatThrownBy(() -> new ElixirClientPlugin().execute(PluginContext.builder()
                        .model(model)
                        .fileManifest(manifest)
                        .settings(settings)
                        .build()))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("protocol")
                .hasMessageContaining("smithy.api#String");
    }

    @Test
    void restJson1ProtocolEmitsStubModuleAndBindingComments() {
        URL resource = ElixirClientPluginTest.class.getResource("/model/protocol_rest_json_fixture.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
                .withMember("edition", "2026")
                .withMember("protocol", "aws.protocols#restJson1")
                .build();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        assertThat(manifest.expectFileString("protocoljson_service_rest_json_1.ex"))
                .contains("defmodule ProtocoljsonServiceRestJson1 do")
                .contains("REST JSON 1 codecs for smithy.beam.demo.protocoljson#DemoRestJson");
        assertThat(manifest.expectFileString("runtime_http.ex"))
                .contains("defmodule RuntimeHttp do")
                .contains("http_client = Map.get(config, :http_client, ReqClient)")
                .contains("def dispatch(http_client, config, %RuntimeTypes.HttpRequest{} = req) do")
                .contains("case http_client.request(req_opts) do");
        assertThat(manifest.expectFileString("protocoljson_service_client.ex"))
                .contains("# HTTP request bindings for smithy.beam.demo.protocoljson#DescribeItem:")
                .contains("#   id @ LABEL")
                .contains("#   requestTag @ HEADER")
                .contains("#   verbose @ QUERY");
    }

    @Test
    void restJson1ProtocolEmitsRealEncoderAndDecoder() {
        URL resource = ElixirClientPluginTest.class.getResource("/model/protocol_rest_json_fixture.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
                .withMember("edition", "2026")
                .withMember("protocol", "aws.protocols#restJson1")
                .build();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        String codec = manifest.expectFileString("protocoljson_service_rest_json_1.ex");
        assertThat(codec).contains("def encode_describe_item_request(");
        assertThat(codec).contains("def decode_describe_item_request(");
        assertThat(codec).contains("def decode_describe_item_response(");
        assertThat(codec).contains("%RuntimeTypes.HttpRequest{");
        assertThat(codec).contains("Jason.decode!");
        assertThat(codec).contains("uri_encode(");
        assertThat(codec).contains("uri_decode(");
        assertThat(codec).contains("decode_query_param(");
        assertThat(manifest.expectFileString("runtime_helpers.ex"))
                .contains("defmodule RuntimeHelpers do")
                .contains("def parse_labels(path, template)");
        assertThat(codec).contains("def decode_describe_item_request(");
        assertThat(codec).contains("label_map");
        assertThat(codec).doesNotContain("RuntimeHelpers.parse_labels(");
        assertThat(codec).doesNotContain("BeamPath.parse_labels");

        new ElixirServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        String router = manifest.expectFileString("demo_rest_json_router.ex");
        assertThat(router).contains("\" <> name_seg = path");
        assertThat(router).contains("parse_labels(path, \"/items/{id}\")");
        assertThat(router).contains("DemoRestJsonRestJson1.decode_describe_item_request");
        assertThat(router).contains("\"/items\"");
        assertThat(router).doesNotContain("path, path");
    }

    @Test
    void resourceLifecycleEmitsClientHelperModules() {
        URL resource = ElixirClientPluginTest.class.getResource("/model/resource_lifecycle.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.resource_lifecycle#ResourceLifecycleService")
                .withMember("edition", "2026")
                .build();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        String org = manifest.expectFileString("organization_resource.ex");
        assertThat(org).contains("defmodule OrganizationResource do");
        assertThat(org).contains("ResourceLifecycleServiceClient.get_organization(");
        assertThat(org).contains("org_id: org_id");
        assertThat(org).contains("ResourceLifecycleServiceClient.create_organization(config, input)");
        assertThat(org).doesNotContain("%{input | }");
        assertThat(org).contains("Top-level organization resource.");

        String employee = manifest.expectFileString("employee_resource.ex");
        assertThat(employee).contains("get_employee(");
        assertThat(employee).contains("org_id: org_id");
        assertThat(employee).contains("employee_id: employee_id");
        assertThat(employee).contains("list_employees_by_status(");
    }
}
