package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangClientPlugin;
import io.smithy.beam.erlang.ErlangServerPlugin;
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

class ErlangClientPluginTest {

    private static final String TYPES_FILE = "basic_types.hrl";
    private static final String CLIENT_FILE = "basic_service_client.erl";

    private static Model loadModel() {
        URL resource = ErlangClientPluginTest.class.getResource("/model/basic.smithy");
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
    void emitsTypesHeaderAndClientStubOnManifest() {
        Model model = loadModel();
        MockManifest manifest = new MockManifest();

        new ErlangClientPlugin().execute(buildContext(model, manifest));

        assertThat(manifest.expectFileString(TYPES_FILE)).contains("-type basic_string()");
        assertClientStubHeaderOrder(manifest.expectFileString(CLIENT_FILE));
        assertThat(manifest.getFileString("basic_service_rest_json_1.erl")).isEmpty();
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
        new ErlangClientPlugin().execute(context);
        assertThat(manifest.expectFileString(TYPES_FILE)).contains("-type basic_string()");
        assertClientStubHeaderOrder(manifest.expectFileString(CLIENT_FILE));
    }

    private static void assertClientStubHeaderOrder(String clientSource) {
        assertThat(clientSource).contains("-module(basic_service_client).");
        assertThat(clientSource).contains("-include(\"basic_types.hrl\").");
        assertThat(clientSource).contains("-export([get_type_closure/2]).");
        int moduleIndex = clientSource.indexOf("-module(basic_service_client).");
        int includeIndex = clientSource.indexOf("-include(\"basic_types.hrl\").");
        int exportIndex = clientSource.indexOf("-export([get_type_closure/2]).");
        assertThat(moduleIndex).isLessThan(includeIndex);
        assertThat(includeIndex).isLessThan(exportIndex);
        assertThat(clientSource.stripLeading()).doesNotStartWith("-include");
    }

    @Test
    void multipleServicesWithoutExplicitServiceSettingFails() {
        URL resource = ErlangClientPluginTest.class.getResource("/model/multi_service.smithy");
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
        assertThatThrownBy(() -> new ErlangClientPlugin().execute(context))
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
        assertThatThrownBy(() -> new ErlangClientPlugin().execute(context))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("edition");
    }

    @Test
    void relativeDateAndRelativeVersionDoNotChangeTypesOrClientStubOutput() {
        URL resource = ErlangClientPluginTest.class.getResource("/model/dedicated_operation_io.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest baseline = new MockManifest();
        ObjectNode baselineSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.dedicated_io#DedicatedIoService")
                .withMember("edition", "2026")
                .build();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(baseline)
                .settings(baselineSettings)
                .build());
        MockManifest extended = new MockManifest();
        ObjectNode extendedSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.dedicated_io#DedicatedIoService")
                .withMember("edition", "2026")
                .withMember("relativeDate", "2026-01-01")
                .withMember("relativeVersion", "1.0.0")
                .build();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(extended)
                .settings(extendedSettings)
                .build());
        assertThat(extended.expectFileString("dedicated_io_types.hrl"))
                .isEqualTo(baseline.expectFileString("dedicated_io_types.hrl"));
        assertThat(extended.expectFileString("dedicated_io_service_client.erl"))
                .isEqualTo(baseline.expectFileString("dedicated_io_service_client.erl"));
    }

    @Test
    void restJson1ProtocolWiresClientOperationToCodecAndHttp() {
        URL resource = ErlangClientPluginTest.class.getResource("/model/protocol_rest_json_fixture.smithy");
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
                .build();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        assertThat(manifest.expectFileString("demo_rest_json_rest_json_1.erl"))
                .contains("-module(demo_rest_json_rest_json_1).")
                .contains("REST JSON 1 codecs for smithy.beam.demo.protocoljson#DemoRestJson");
        assertThat(manifest.expectFileString("runtime_http.erl"))
                .contains("-module(runtime_http).")
                .contains("HttpClient = maps:get(http_client, Config, httpc),")
                .contains("dispatch_signed(HttpClient, Config, #http_request{");
        String client = manifest.expectFileString("demo_rest_json_client.erl");
        assertThat(client)
                .contains("describe_item(Config, Input) ->")
                .contains("Req = demo_rest_json_rest_json_1:encode_describe_item_request(Input),")
                .contains("case runtime_http:dispatch(Config, Req) of")
                .contains("demo_rest_json_rest_json_1:decode_describe_item_response(Resp);");
    }

    @Test
    void restJson1ProtocolEmitsRealEncoderAndDecoder() {
        URL resource = ErlangClientPluginTest.class.getResource("/model/protocol_rest_json_fixture.smithy");
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
                .build();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        String codec = manifest.expectFileString("demo_rest_json_rest_json_1.erl");
        assertThat(codec).contains("encode_describe_item_request(");
        assertThat(codec).contains("decode_describe_item_response(");
        assertThat(codec).contains("http_request{");
        assertThat(codec).contains("jsone:try_decode(Body)");
        assertThat(codec).contains("{ok, Val, _} -> Val;");
        assertThat(codec).contains("{error, _} -> #{}");
        assertThat(codec).doesNotContain("{error, _} -> #{}}");
        assertThat(codec).contains("#describe_item_input{");
        assertThat(codec).doesNotContain("#describe_item_input(){");
        assertThat(codec).contains("(V) when V =/= undefined");
        assertThat(codec).contains("uri_encode(");
        String runtimeHelpers = manifest.expectFileString("runtime_helpers.erl");
        assertThat(runtimeHelpers.split("-module\\(runtime_helpers\\)", -1)).hasSize(2);
        assertThat(runtimeHelpers)
                .contains("-module(runtime_helpers).")
                .contains("parse_labels(Path, Template)");
        assertThat(codec).contains("decode_describe_item_request(");
        assertThat(codec).contains("LabelMap");
        assertThat(codec).doesNotContain("runtime_helpers:parse_labels(Path");
        assertThat(codec).doesNotContain("beam_path:parse_labels");

        new ErlangServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        String router = manifest.expectFileString("demo_rest_json_router.erl");
        assertThat(router).contains("<<\"/items/\", NameSeg/binary>>");
        assertThat(router).contains("parse_labels(Path, <<\"/items/{id}\">>)");
        assertThat(router).contains("demo_rest_json_rest_json_1:decode_describe_item_request");
        assertThat(router).doesNotContain("demo_rest_json_rest_json_1:decode_describe_item_response");
        assertThat(router).contains("<<\"/items\">>");
        assertThat(router).doesNotContain("Path = Path");
        assertThat(router).contains("end;\nroute(");
    }

    @Test
    void unsupportedModelProtocolFailsWithCodegenException() {
        URL resource = ErlangClientPluginTest.class.getResource("/model/multi_service.smithy");
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
                .withMember("relativeDate", "2026-01-01")
                .withMember("relativeVersion", "1.0.0")
                .build();
        assertThatThrownBy(() -> new ErlangClientPlugin().execute(PluginContext.builder()
                        .model(model)
                        .fileManifest(manifest)
                        .settings(settings)
                        .build()))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("No BeamProtocolCodegen registered for protocol trait")
                .hasMessageContaining("smithy.beam.demo.multi#TestProtocol");
    }

    @Test
    void resourceLifecycleEmitsClientHelperModules() {
        URL resource = ErlangClientPluginTest.class.getResource("/model/resource_lifecycle.smithy");
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
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        assertThat(manifest.getFileString("resource_lifecycle_service_rest_json_1.erl"))
                .isPresent();
        assertThat(manifest.getFileString("runtime_http.erl")).isPresent();

        String org = manifest.expectFileString("organization_resource.erl");
        assertThat(org).contains("-module(organization_resource).");
        assertThat(org).contains("-type client_config() :: #{binary() => term()}.");
        assertThat(org).contains("read/2");
        assertThat(org).contains("resource_lifecycle_service_client:get_organization(");
        assertThat(org).contains("#get_organization_input{org_id = org_id}");
        assertThat(org).contains("create(Config, Input) ->");
        assertThat(org).contains("resource_lifecycle_service_client:create_organization(Config, Input).");
        assertThat(org).doesNotContain("Input#create_organization_input{}");
        assertThat(org).contains("Top-level organization resource.");

        String employee = manifest.expectFileString("employee_resource.erl");
        assertThat(employee).contains("get_employee(");
        assertThat(employee).contains("org_id = org_id");
        assertThat(employee).contains("employee_id = employee_id");
        assertThat(employee).contains("list_employees_by_status(");
    }
}
