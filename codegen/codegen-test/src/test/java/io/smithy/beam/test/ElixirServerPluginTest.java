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

    private static final String TYPES_FILE = "lib/generated/basic_types.ex";
    private static final String SERVER_FILE = "lib/generated/basic_server.ex";

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
        assertThat(manifest.expectFileString(SERVER_FILE)).contains("defmodule BasicServer do");
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
        assertThat(manifest.expectFileString(SERVER_FILE)).contains("defmodule BasicServer do");
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
    void protocolRelativeDateRelativeVersionDoNotChangeTypesOrServerStubOutput() {
        URL resource = ElixirServerPluginTest.class.getResource("/model/multi_service.smithy");
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
        new ElixirServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(baseline)
                .settings(baselineSettings)
                .build());
        MockManifest extended = new MockManifest();
        ObjectNode extendedSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.multi#ServiceA")
                .withMember("edition", "2026")
                .withMember("protocol", "smithy.api#String")
                .withMember("relativeDate", "2026-01-01")
                .withMember("relativeVersion", "1.0.0")
                .build();
        new ElixirServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(extended)
                .settings(extendedSettings)
                .build());
        assertThat(extended.expectFileString("lib/generated/multi_types.ex"))
                .isEqualTo(baseline.expectFileString("lib/generated/multi_types.ex"));
        assertThat(extended.expectFileString("lib/generated/multi_server.ex"))
                .isEqualTo(baseline.expectFileString("lib/generated/multi_server.ex"));
    }
}
