package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.elixir.ElixirTypesPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import io.smithy.beam.erlang.ErlangTypesPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class NameSettingTest {

    private static Model loadBasicModel() {
        URL basic = NameSettingTest.class.getResource("/model/basic.smithy");
        return Model.assembler()
                .addImport(basic)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static ObjectNode settingsWithName(String name) {
        return ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.basic#BasicService")
                .withMember("edition", "2026")
                .withMember("name", name)
                .build();
    }

    @Test
    void erlangClientAndTypesShareConfiguredStem() {
        Model model = loadBasicModel();
        ObjectNode settings = settingsWithName("basic_demo");

        MockManifest typesManifest = new MockManifest();
        new ErlangTypesPlugin().execute(PluginContext.builder()
                .model(model).fileManifest(typesManifest)
                .settings(settings).build());

        MockManifest clientManifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model).fileManifest(clientManifest)
                .settings(settings).build());

        assertThat(typesManifest.getFileString("basic_demo_types.hrl")).isPresent();
        assertThat(typesManifest.getFileString("basic_service_types.hrl")).isEmpty();

        assertThat(clientManifest.getFileString("basic_demo_client.erl")).isPresent();
        assertThat(clientManifest.getFileString("basic_demo_rest_json_1.erl")).isPresent();
        assertThat(clientManifest.getFileString("basic_service_client.erl")).isEmpty();
    }

    @Test
    void elixirClientAndTypesShareConfiguredStem() {
        Model model = loadBasicModel();
        ObjectNode settings = settingsWithName("basic_demo");

        MockManifest typesManifest = new MockManifest();
        new ElixirTypesPlugin().execute(PluginContext.builder()
                .model(model).fileManifest(typesManifest)
                .settings(settings).build());

        MockManifest clientManifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(model).fileManifest(clientManifest)
                .settings(settings).build());

        assertThat(typesManifest.getFileString("basic_demo_types.ex")).isPresent();
        assertThat(clientManifest.getFileString("basic_demo_client.ex")).isPresent();
        assertThat(clientManifest.getFileString("basic_demo_rest_json_1.ex")).isPresent();
    }
}
