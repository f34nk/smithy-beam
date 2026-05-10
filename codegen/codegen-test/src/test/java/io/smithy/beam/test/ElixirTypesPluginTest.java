package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirTypesPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class ElixirTypesPluginTest {

    private static Model loadModel() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/basic.smithy");
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

    private static Model loadReservedWordsModel() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/reserved_words.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static PluginContext buildReservedWordsContext(MockManifest manifest) {
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.reserved#ReservedService")
                .withMember("edition", "2026")
                .build();
        return PluginContext.builder()
                .model(loadReservedWordsModel())
                .fileManifest(manifest)
                .settings(settings)
                .build();
    }

    @Test
    void pluginRunsWithoutException() {
        Model model = loadModel();
        PluginContext context = buildContext(model);
        new ElixirTypesPlugin().execute(context);
        // TODO: assert file contents match baseline in later commits.
    }

    @Test
    void reservedWordsEscapeAndDeconflictInElixirOutput() {
        MockManifest manifest = new MockManifest();
        new ElixirTypesPlugin().execute(buildReservedWordsContext(manifest));
        String content = manifest.expectFileString("lib/generated/reserved_types.ex");
        assertThat(content)
                .contains("after_")
                .contains("begin_")
                .contains("case_")
                .contains("end_")
                .contains("receive_")
                .contains("{:case_,")
                .contains("{:end_,")
                .contains("receive_:")
                .contains("after_:")
                .contains("my_type_2");
    }
}
