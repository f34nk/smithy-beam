package io.smithy.beam.test;

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

class ElixirServerPluginTest {

    private static final String TYPES_FILE = "basic_service_types.ex";
    private static final String SERVER_FILE = "basic_service_server.ex";
    private static final String BEHAVIOUR_FILE = "basic_service_behaviour.ex";

    private static Model loadModel() {
        URL resource = ElixirServerPluginTest.class.getResource("/model/basic.smithy");
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
    void emitsTypesModuleAndServerStubOnManifest() {
        Model model = loadModel();
        MockManifest manifest = new MockManifest();

        new ElixirServerPlugin().execute(buildContext(model, manifest));

        assertThat(manifest.expectFileString(TYPES_FILE)).contains("basic_string");
        assertBehaviourModule(manifest.expectFileString(BEHAVIOUR_FILE));
        assertServerDispatcher(manifest.expectFileString(SERVER_FILE));
        assertThat(manifest.getFileString("basic_service_rest_json_1.ex")).isEmpty();
        assertThat(manifest.getFileString("basic_service_router.ex")).isEmpty();
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
        new ElixirServerPlugin().execute(context);
        assertThat(manifest.expectFileString(TYPES_FILE)).contains("basic_string");
        assertBehaviourModule(manifest.expectFileString(BEHAVIOUR_FILE));
        assertServerDispatcher(manifest.expectFileString(SERVER_FILE));
    }

    private static void assertBehaviourModule(String source) {
        assertThat(source).contains("defmodule BasicServiceBehaviour do");
        assertThat(source).contains("alias BasicServiceTypes");
        assertThat(source).contains("@callback handle_get_type_closure(");
        assertThat(source).contains("BasicServiceTypes.GetTypeClosureInput.t()");
        assertThat(source).contains("{:handle_get_type_closure, 3}");
        assertThat(source).contains("def callbacks do");
    }

    private static void assertServerDispatcher(String source) {
        assertThat(source).contains("defmodule BasicServiceServer do");
        assertThat(source).contains("@behaviour BasicServiceBehaviour");
        assertThat(source).contains("alias BasicServiceBehaviour");
        assertThat(source).contains("@default_impl BasicServiceImpl");
        assertThat(source).contains("@handlers_key {BasicServiceServer, :handlers}");
        assertThat(source).contains("defp resolve_impl(impl) do");
        assertThat(source).contains("BasicServiceBehaviour.callbacks()");
        assertThat(source).contains("function_exported?(impl, fun, 3)");
        assertThat(source).contains("Function.capture(impl, fun, 3)");
        assertThat(source).contains("def init_handlers do");
        assertThat(source).contains(":persistent_term.put(@handlers_key, handlers)");
        assertThat(source).contains("defp dispatch_handler(fun, ctx, input, meta) do");
        assertThat(source).contains("dispatch_handler(:handle_get_type_closure, ctx, input, meta)");
        assertThat(source).doesNotContain("def handle_get_type_closure(_ctx, _input, _meta), do: {:error, :not_implemented}");
        int moduleIndex = source.indexOf("defmodule BasicServiceServer do");
        int moduledocIndex = source.indexOf("@moduledoc \"\"\"");
        int behaviourIndex = source.indexOf("@behaviour BasicServiceBehaviour");
        int aliasIndex = source.indexOf("alias BasicServiceTypes");
        assertThat(moduleIndex).isLessThan(moduledocIndex);
        assertThat(moduledocIndex).isLessThan(behaviourIndex);
        assertThat(behaviourIndex).isLessThan(aliasIndex);
    }

    @Test
    void multipleServicesWithoutExplicitServiceSettingFails() {
        URL resource = ElixirServerPluginTest.class.getResource("/model/multi_service.smithy");
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
        assertThatThrownBy(() -> new ElixirServerPlugin().execute(context))
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
        assertThatThrownBy(() -> new ElixirServerPlugin().execute(context))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("edition");
    }

    @Test
    void relativeDateAndRelativeVersionDoNotChangeTypesOrServerStubOutput() {
        URL resource = ElixirServerPluginTest.class.getResource("/model/dedicated_operation_io.smithy");
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
        new ElixirServerPlugin().execute(PluginContext.builder()
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
        new ElixirServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(extended)
                .settings(extendedSettings)
                .build());
        assertThat(extended.expectFileString("dedicated_io_service_types.ex"))
                .isEqualTo(baseline.expectFileString("dedicated_io_service_types.ex"));
        assertThat(extended.expectFileString("dedicated_io_service_server.ex"))
                .isEqualTo(baseline.expectFileString("dedicated_io_service_server.ex"));
        assertThat(extended.expectFileString("dedicated_io_service_behaviour.ex"))
                .isEqualTo(baseline.expectFileString("dedicated_io_service_behaviour.ex"));
    }

    @Test
    void unsupportedModelProtocolFailsWithCodegenException() {
        URL resource = ElixirServerPluginTest.class.getResource("/model/multi_service.smithy");
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
        assertThatThrownBy(() -> new ElixirServerPlugin().execute(PluginContext.builder()
                        .model(model)
                        .fileManifest(manifest)
                        .settings(settings)
                        .build()))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("No BeamProtocolCodegen registered for protocol trait")
                .hasMessageContaining("smithy.beam.demo.multi#TestProtocol");
    }

    @Test
    void resourceLifecycleEmitsServerHelperModules() {
        URL resource = ElixirServerPluginTest.class.getResource("/model/resource_lifecycle.smithy");
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
        new ElixirServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        assertThat(manifest.getFileString("resource_lifecycle_service_rest_json_1.ex"))
                .isPresent();
        assertThat(manifest.getFileString("resource_lifecycle_service_router.ex"))
                .isPresent();

        String org = manifest.expectFileString("organization_resource.ex");
        assertThat(org).contains("defmodule OrganizationResource do");
        assertThat(org).contains("handle_read(");
        assertThat(org).contains("ResourceLifecycleServiceServer.handle_get_organization(");
        assertThat(org).contains("ResourceLifecycleServiceServer.handle_create_organization(ctx, input, meta)");
        assertThat(org).doesNotContain("%{input | }");
    }
}
