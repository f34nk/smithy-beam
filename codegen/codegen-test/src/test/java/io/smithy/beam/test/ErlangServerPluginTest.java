package io.smithy.beam.test;

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

class ErlangServerPluginTest {

    private static final String TYPES_FILE = "basic_types.hrl";
    private static final String SERVER_FILE = "basic_service_server.erl";

    private static Model loadModel() {
        URL resource = ErlangServerPluginTest.class.getResource("/model/basic.smithy");
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
    void emitsTypesHeaderAndServerStubOnManifest() {
        Model model = loadModel();
        MockManifest manifest = new MockManifest();

        new ErlangServerPlugin().execute(buildContext(model, manifest));

        assertThat(manifest.expectFileString(TYPES_FILE)).contains("-type basic_string()");
        assertServerStubHeaderOrder(manifest.expectFileString(SERVER_FILE));
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
        new ErlangServerPlugin().execute(context);
        assertThat(manifest.expectFileString(TYPES_FILE)).contains("-type basic_string()");
        assertServerStubHeaderOrder(manifest.expectFileString(SERVER_FILE));
    }

    private static void assertServerStubHeaderOrder(String serverSource) {
        assertThat(serverSource).contains("-module(basic_service_server).");
        assertThat(serverSource).contains("-include(\"basic_types.hrl\").");
        assertThat(serverSource).contains("-export([handle_get_type_closure/3]).");
        int moduleIndex = serverSource.indexOf("-module(basic_service_server).");
        int includeIndex = serverSource.indexOf("-include(\"basic_types.hrl\").");
        int exportIndex = serverSource.indexOf("-export([handle_get_type_closure/3]).");
        assertThat(moduleIndex).isLessThan(includeIndex);
        assertThat(includeIndex).isLessThan(exportIndex);
        assertThat(serverSource.stripLeading()).doesNotStartWith("-include");
    }

    @Test
    void multipleServicesWithoutExplicitServiceSettingFails() {
        URL resource = ErlangServerPluginTest.class.getResource("/model/multi_service.smithy");
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
        assertThatThrownBy(() -> new ErlangServerPlugin().execute(context))
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
        assertThatThrownBy(() -> new ErlangServerPlugin().execute(context))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("edition");
    }

    @Test
    void relativeDateAndRelativeVersionWithoutProtocolDoNotChangeTypesOrServerStubOutput() {
        URL resource = ErlangServerPluginTest.class.getResource("/model/multi_service.smithy");
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
        new ErlangServerPlugin().execute(PluginContext.builder()
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
        new ErlangServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(extended)
                .settings(extendedSettings)
                .build());
        assertThat(extended.expectFileString("multi_types.hrl"))
                .isEqualTo(baseline.expectFileString("multi_types.hrl"));
        assertThat(extended.expectFileString("service_a_server.erl"))
                .isEqualTo(baseline.expectFileString("service_a_server.erl"));
    }

    @Test
    void explicitInvalidProtocolFailsWithCodegenException() {
        URL resource = ErlangServerPluginTest.class.getResource("/model/multi_service.smithy");
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
        assertThatThrownBy(() -> new ErlangServerPlugin().execute(PluginContext.builder()
                        .model(model)
                        .fileManifest(manifest)
                        .settings(settings)
                        .build()))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("protocol")
                .hasMessageContaining("smithy.api#String");
    }

    @Test
    void resourceLifecycleEmitsServerHelperModules() {
        URL resource = ErlangServerPluginTest.class.getResource("/model/resource_lifecycle.smithy");
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
        new ErlangServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        String org = manifest.expectFileString("organization_resource.erl");
        assertThat(org).contains("-module(organization_resource).");
        assertThat(org).contains("handle_read(");
        assertThat(org).contains("resource_lifecycle_service_server:handle_get_organization(");
        assertThat(org).contains("resource_lifecycle_service_server:handle_create_organization(Ctx, Input, Meta).");
        assertThat(org).doesNotContain("Input#create_organization_input{}");
    }
}
