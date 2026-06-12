package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.elixir.ElixirServerPlugin;
import io.smithy.beam.elixir.ElixirTypesPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import io.smithy.beam.erlang.ErlangServerPlugin;
import io.smithy.beam.erlang.ErlangTypesPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceRenameTest {

    private static Model loadModel() {
        URL rename = ServiceRenameTest.class
                .getResource("/model/service_rename.smithy");
        URL shared = ServiceRenameTest.class
                .getResource("/model/shared_widget.smithy");
        return Model.assembler()
                .addImport(rename)
                .addImport(shared)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static ObjectNode settings() {
        return ObjectNode.builder()
                .withMember("service",
                        "smithy.beam.test.rename#RenameService")
                .withMember("edition", "2026")
                .build();
    }

    @Test
    void renamedShapeUsesRenameTargetInTypesHeader() {
        Model model = loadModel();
        MockManifest manifest = new MockManifest();
        new ErlangTypesPlugin().execute(PluginContext.builder()
                .model(model).fileManifest(manifest)
                .settings(settings()).build());

        String types = manifest.getFileString("rename_service_types.hrl").orElse("");
        assertThat(types).contains("-record(renamed_widget,");
        assertThat(types).doesNotContain("-record(widget,");
    }

    @Test
    void renamedShapeIdentifierMatchesBetweenTypesAndClient() {
        Model model = loadModel();
        MockManifest typesManifest = new MockManifest();
        new ErlangTypesPlugin().execute(PluginContext.builder()
                .model(model).fileManifest(typesManifest)
                .settings(settings()).build());

        MockManifest clientManifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model).fileManifest(clientManifest)
                .settings(settings()).build());

        String types = typesManifest.getFileString(
                "rename_service_types.hrl").orElse("");
        String codec = clientManifest.getFileString(
                "rename_service_rest_json_1.erl").orElse("");

        assertThat(types).contains("renamed_widget");
        assertThat(codec).isNotEmpty();
        assertThat(codec).contains("get_widget_output");
        assertThat(codec).doesNotContain("#widget{");
    }

    @Test
    void selfRenamedServiceProducesRenamedServerModuleFile() {
        Model model = Model.assembler()
                .addImport(getClass().getResource("/model/service_self_rename.smithy"))
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        new ErlangServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", "smithy.beam.test#OriginalName")
                        .withMember("edition", "2026")
                        .build())
                .build());

        assertThat(manifest.getFileString("renamed_service_server.erl")).isPresent();
        assertThat(manifest.getFileString("original_name_server.erl")).isEmpty();
        assertThat(manifest.getFileString("renamed_service_router.erl")).isPresent();
        assertThat(manifest.getFileString("renamed_service_rest_json_1.erl")).isPresent();
    }

    @Test
    void elixirRenamedShapeUsesRenameTargetInTypesModule() {
        Model model = loadModel();
        MockManifest manifest = new MockManifest();
        new ElixirTypesPlugin().execute(PluginContext.builder()
                .model(model).fileManifest(manifest)
                .settings(settings()).build());

        String types = manifest.getFileString("rename_service_types.ex").orElse("");
        assertThat(types).contains("defmodule RenamedWidget");
        assertThat(types).doesNotContain("defmodule Widget do");
    }

    @Test
    void elixirRenamedShapeIdentifierMatchesBetweenTypesAndClient() {
        Model model = loadModel();
        MockManifest typesManifest = new MockManifest();
        new ElixirTypesPlugin().execute(PluginContext.builder()
                .model(model).fileManifest(typesManifest)
                .settings(settings()).build());

        MockManifest clientManifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(model).fileManifest(clientManifest)
                .settings(settings()).build());

        String types = typesManifest.getFileString("rename_service_types.ex").orElse("");
        String codec = clientManifest.getFileString("rename_service_rest_json_1.ex").orElse("");

        assertThat(types).containsAnyOf("renamed_widget", "RenamedWidget");
        assertThat(codec).isNotEmpty();
        assertThat(codec).contains("GetWidgetOutput");
        assertThat(codec).contains("decode_get_widget_response");
        assertThat(codec).doesNotContain("%Widget{");
    }

    @Test
    void elixirSelfRenamedServiceProducesRenamedServerModuleFile() {
        Model model = Model.assembler()
                .addImport(getClass().getResource("/model/service_self_rename.smithy"))
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        new ElixirServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", "smithy.beam.test#OriginalName")
                        .withMember("edition", "2026")
                        .build())
                .build());

        assertThat(manifest.getFileString("renamed_service_server.ex")).isPresent();
        assertThat(manifest.getFileString("original_name_server.ex")).isEmpty();
        assertThat(manifest.getFileString("renamed_service_router.ex")).isPresent();
        assertThat(manifest.getFileString("renamed_service_rest_json_1.ex")).isPresent();
    }
}
