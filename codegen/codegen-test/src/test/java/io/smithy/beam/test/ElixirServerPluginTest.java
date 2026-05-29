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

    private static final String TYPES_FILE = "basic_types.ex";
    private static final String SERVER_FILE = "basic_service_server.ex";

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
        assertServerStubHeaderOrder(manifest.expectFileString(SERVER_FILE));
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
        assertServerStubHeaderOrder(manifest.expectFileString(SERVER_FILE));
    }

    private static void assertServerStubHeaderOrder(String serverSource) {
        assertThat(serverSource).contains("defmodule BasicServiceServer do");
        assertThat(serverSource).contains("@moduledoc \"\"\"");
        assertThat(serverSource).contains("alias BasicTypes");
        assertThat(serverSource)
                .contains("@spec handle_get_type_closure(term(), BasicTypes.GetTypeClosureInput.t(), term())");
        assertThat(serverSource)
                .contains("def handle_get_type_closure(_ctx, _input, _meta), do: {:error, :not_implemented}");
        int moduleIndex = serverSource.indexOf("defmodule BasicServiceServer do");
        int moduledocIndex = serverSource.indexOf("@moduledoc \"\"\"");
        int aliasIndex = serverSource.indexOf("alias BasicTypes");
        int specIndex = serverSource.indexOf("@spec handle_get_type_closure");
        assertThat(moduleIndex).isLessThan(moduledocIndex);
        assertThat(moduledocIndex).isLessThan(aliasIndex);
        assertThat(aliasIndex).isLessThan(specIndex);
        assertThat(serverSource.stripLeading()).startsWith("defmodule");
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
        assertThat(extended.expectFileString("dedicated_io_types.ex"))
                .isEqualTo(baseline.expectFileString("dedicated_io_types.ex"));
        assertThat(extended.expectFileString("dedicated_io_service_server.ex"))
                .isEqualTo(baseline.expectFileString("dedicated_io_service_server.ex"));
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
