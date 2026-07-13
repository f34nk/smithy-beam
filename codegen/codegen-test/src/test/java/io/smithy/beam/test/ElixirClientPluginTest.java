package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.elixir.ElixirServerPlugin;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class ElixirClientPluginTest {

  private static final String TYPES_FILE = "basic_service_types.ex";
  private static final String CLIENT_FILE = "basic_service_client.ex";

  private static Model loadModel() {
    URL resource = ElixirClientPluginTest.class.getResource("/model/basic.smithy");
    assertThat(resource).isNotNull();
    return Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
  }

  private static PluginContext buildContext(Model model, FileManifest manifest) {
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.basic#BasicService")
            .withMember("edition", "2026")
            .build();
    return PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build();
  }

  @Test
  void emitsOnlyRequiredStaticRuntimeModulesForBasicClient() {
    Model model = loadModel();
    MockManifest manifest = new MockManifest();

    new ElixirClientPlugin().execute(buildContext(model, manifest));

    assertThat(manifest.getFileString("runtime_http.ex")).isPresent();
    assertThat(manifest.getFileString("runtime_types.ex")).isPresent();
    assertThat(manifest.expectFileString("runtime_http.ex")).contains("defmodule RuntimeHttp");
    assertThat(manifest.getFileString("aws_sigv4.ex")).isEmpty();
    assertThat(manifest.getFileString("http_checksum.ex")).isEmpty();
  }

  @Test
  void emitsTypesModuleAndClientStubOnManifest() {
    Model model = loadModel();
    MockManifest manifest = new MockManifest();

    new ElixirClientPlugin().execute(buildContext(model, manifest));

    assertThat(manifest.expectFileString(TYPES_FILE)).contains("basic_string");
    assertClientStubHeaderOrder(manifest.expectFileString(CLIENT_FILE));
    assertThat(manifest.getFileString("basic_service_rest_json_1.ex")).isPresent();
  }

  @Test
  void defaultsToOnlyServiceWhenServiceSettingOmitted() {
    MockManifest manifest = new MockManifest();
    ObjectNode settings = ObjectNode.builder().withMember("edition", "2026").build();
    PluginContext context =
        PluginContext.builder()
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
    assertThat(clientSource).contains("alias BasicServiceTypes, as: Types");
    assertThat(clientSource)
        .contains("@spec get_type_closure(map(), BasicServiceTypes.GetTypeClosureInput.t())");
    assertThat(clientSource).contains("def get_type_closure(");
    assertThat(clientSource).contains("RuntimeHttp.dispatch");
    assertThat(clientSource).contains("@type client_config :: map()");
    int moduleIndex = clientSource.indexOf("defmodule BasicServiceClient do");
    int moduledocIndex = clientSource.indexOf("@moduledoc \"\"\"");
    int aliasIndex = clientSource.indexOf("alias BasicServiceTypes, as: Types");
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
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    ObjectNode settings = ObjectNode.builder().withMember("edition", "2026").build();
    PluginContext context =
        PluginContext.builder()
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
    ObjectNode settings =
        ObjectNode.builder().withMember("service", "smithy.beam.demo.basic#BasicService").build();
    PluginContext context =
        PluginContext.builder()
            .model(loadModel())
            .fileManifest(manifest)
            .settings(settings)
            .build();
    assertThatThrownBy(() -> new ElixirClientPlugin().execute(context))
        .isInstanceOf(CodegenException.class)
        .hasMessageContaining("edition");
  }

  @Test
  void relativeDateAndRelativeVersionDoNotChangeTypesOrClientStubOutput() {
    URL resource = ElixirClientPluginTest.class.getResource("/model/dedicated_operation_io.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    MockManifest baseline = new MockManifest();
    ObjectNode baselineSettings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.dedicated_io#DedicatedIoService")
            .withMember("edition", "2026")
            .build();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(baseline)
                .settings(baselineSettings)
                .build());
    MockManifest extended = new MockManifest();
    ObjectNode extendedSettings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.dedicated_io#DedicatedIoService")
            .withMember("edition", "2026")
            .withMember("relativeDate", "2026-01-01")
            .withMember("relativeVersion", "1.0.0")
            .build();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(extended)
                .settings(extendedSettings)
                .build());
    assertThat(extended.expectFileString("dedicated_io_service_types.ex"))
        .isEqualTo(baseline.expectFileString("dedicated_io_service_types.ex"));
    assertThat(extended.expectFileString("dedicated_io_service_client.ex"))
        .isEqualTo(baseline.expectFileString("dedicated_io_service_client.ex"));
  }

  @Test
  void unsupportedModelProtocolFailsWithCodegenException() {
    URL resource = ElixirClientPluginTest.class.getResource("/model/multi_service.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.multi#ServiceA")
            .withMember("edition", "2026")
            .withMember("relativeDate", "2026-01-01")
            .withMember("relativeVersion", "1.0.0")
            .build();
    assertThatThrownBy(
            () ->
                new ElixirClientPlugin()
                    .execute(
                        PluginContext.builder()
                            .model(model)
                            .fileManifest(manifest)
                            .settings(settings)
                            .build()))
        .isInstanceOf(CodegenException.class)
        .hasMessageContaining("No BeamProtocolCodegen registered for protocol trait")
        .hasMessageContaining("smithy.beam.demo.multi#TestProtocol");
  }

  @Test
  void restJson1ProtocolEmitsStubModuleAndBindingComments() {
    URL resource =
        ElixirClientPluginTest.class.getResource("/model/protocol_rest_json_fixture.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
            .withMember("edition", "2026")
            .build();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());

    assertThat(manifest.expectFileString("demo_rest_json_rest_json_1.ex"))
        .contains("defmodule DemoRestJsonRestJson1 do")
        .contains("REST JSON 1 codecs for smithy.beam.demo.protocoljson#DemoRestJson");
    assertThat(manifest.expectFileString("runtime_http.ex"))
        .contains("defmodule RuntimeHttp do")
        .contains("http_client = Map.get(config, :http_client, __MODULE__.ReqClient)")
        .contains("req = %RuntimeTypes.HttpRequest{}")
        .contains("Utils.split_base_url")
        .contains("case http_client.request(req_opts) do");
    assertThat(manifest.expectFileString("demo_rest_json_client.ex"))
        .contains("HTTP request bindings for smithy.beam.demo.protocoljson#DescribeItem:")
        .contains("  id @ LABEL")
        .contains("  requestTag @ HEADER")
        .contains("  verbose @ QUERY");
  }

  @Test
  void restJson1ProtocolEmitsRealEncoderAndDecoder() {
    URL resource =
        ElixirClientPluginTest.class.getResource("/model/protocol_rest_json_fixture.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
            .withMember("edition", "2026")
            .build();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());

    String codec = manifest.expectFileString("demo_rest_json_rest_json_1.ex");
    assertThat(codec).contains("def encode_describe_item_request(");
    assertThat(codec).contains("def decode_describe_item_request(");
    assertThat(codec).contains("def decode_describe_item_response(");
    assertThat(codec).contains("%RuntimeTypes.HttpRequest{");
    assertThat(codec).contains("Jason.decode!");
    assertThat(codec).contains("uri_encode(");
    assertThat(codec).contains("uri_decode(");
    assertThat(codec).contains("decode_query_param(");
    assertThat(manifest.getFileString("runtime_helpers.ex")).isEmpty();
    assertThat(manifest.expectFileString("utils.ex")).contains("defmodule Utils do");
    assertThat(codec).contains("def decode_describe_item_request(");
    assertThat(codec).contains("label_map");
    assertThat(codec).doesNotContain("RuntimeHelpers.parse_labels(");
    assertThat(codec).doesNotContain("BeamPath.parse_labels");

    new ElixirServerPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());

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
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    MockManifest manifest = new MockManifest();
    ObjectNode settings =
        ObjectNode.builder()
            .withMember("service", "smithy.beam.demo.resource_lifecycle#ResourceLifecycleService")
            .withMember("edition", "2026")
            .build();
    new ElixirClientPlugin()
        .execute(
            PluginContext.builder().model(model).fileManifest(manifest).settings(settings).build());

    assertThat(manifest.getFileString("resource_lifecycle_service_rest_json_1.ex")).isPresent();
    assertThat(manifest.getFileString("runtime_http.ex")).isPresent();

    String org = manifest.expectFileString("organization_resource.ex");
    assertThat(org).contains("defmodule OrganizationResource do");
    assertThat(org).contains("Client.get_organization(config,");
    assertThat(org).contains("org_id: org_id");
    assertThat(org).contains("Client.create_organization(config, input)");
    assertThat(org).doesNotContain("%{input | }");
    assertThat(org).contains("Top-level organization resource.");

    String employee = manifest.expectFileString("employee_resource.ex");
    assertThat(employee).contains("get_employee(");
    assertThat(employee).contains("org_id: org_id");
    assertThat(employee).contains("employee_id: employee_id");
    assertThat(employee).contains("list_employees_by_status(");
  }
}
