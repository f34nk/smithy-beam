package io.smithy.beam.elixir.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

class ElixirServerCodegenPluginTest {

    private static final String SIMPLE_MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace com.example",
            "",
            "service SimpleService {",
            "    version: \"2024-01-01\"",
            "    operations: [GetItem]",
            "}",
            "",
            "operation GetItem {",
            "    input: GetItemInput",
            "    output: GetItemOutput",
            "}",
            "",
            "structure GetItemInput {",
            "    id: String",
            "}",
            "",
            "structure GetItemOutput {",
            "    name: String",
            "}");

    @Test
    void pluginNameIsElixirServerCodegen() {
        assertThat(new ElixirServerCodegenPlugin().getName())
                .isEqualTo("elixir-server-codegen");
    }

    @Test
    void executeDoesNotThrowOnMinimalModel() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", SIMPLE_MODEL)
                .assemble()
                .unwrap();

        ObjectNode settings = Node.objectNodeBuilder()
                .withMember("service", "com.example#SimpleService")
                .withMember("namespace", "SimpleService")
                .withMember("edition", "2025")
                .build();

        PluginContext ctx = PluginContext.builder()
                .fileManifest(new MockManifest())
                .model(model)
                .settings(settings)
                .build();

        assertThatCode(() -> new ElixirServerCodegenPlugin().execute(ctx))
                .doesNotThrowAnyException();
    }

    @Test
    void executeGeneratesFilesOnMinimalModel() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", SIMPLE_MODEL)
                .assemble()
                .unwrap();

        ObjectNode settings = Node.objectNodeBuilder()
                .withMember("service", "com.example#SimpleService")
                .withMember("namespace", "SimpleService")
                .withMember("edition", "2025")
                .build();

        MockManifest manifest = new MockManifest();
        PluginContext ctx = PluginContext.builder()
                .fileManifest(manifest)
                .model(model)
                .settings(settings)
                .build();

        new ElixirServerCodegenPlugin().execute(ctx);

        assertThat(manifest.getFiles()).isNotEmpty();
    }
}
