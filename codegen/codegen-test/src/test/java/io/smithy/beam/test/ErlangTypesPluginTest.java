package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangTypesPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangTypesPluginTest {

    private static Model loadModel() {
        URL resource = ErlangTypesPluginTest.class.getResource("/model/basic.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static PluginContext buildContext(Model model) {
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.basic#BasicService")
                .withMember("edition", "2026")
                .build();
        return PluginContext.builder()
                .model(model)
                .fileManifest(new MockManifest())
                .settings(settings)
                .build();
    }

    @Test
    void pluginRunsWithoutException() {
        Model model = loadModel();
        PluginContext context = buildContext(model);
        new ErlangTypesPlugin().execute(context);
        // TODO: assert file contents match baseline in later commits.
    }
}
