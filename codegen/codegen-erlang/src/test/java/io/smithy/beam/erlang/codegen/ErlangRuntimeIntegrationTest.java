package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.client.ErlangClientCodegenPlugin;
import io.smithy.beam.erlang.server.ErlangServerCodegenPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Verifies that Erlang runtime source files land directly inside
 * {@code outputDir} alongside the generated code, not in a separate
 * {@code runtime/} tree.
 */
class ErlangRuntimeIntegrationTest {

    private static final String AWSJSON11_MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace example.awsjson11",
            "",
            "use aws.protocols#awsJson1_1",
            "",
            "@awsJson1_1",
            "service AwsJson11Service {",
            "    version: \"2024-01-01\"",
            "    operations: [DescribeItem]",
            "}",
            "",
            "operation DescribeItem {",
            "    input: DescribeItemInput",
            "    output: DescribeItemOutput",
            "}",
            "",
            "structure DescribeItemInput {",
            "    itemId: String",
            "}",
            "",
            "structure DescribeItemOutput {",
            "    name: String",
            "}");

    private static MockManifest clientManifest;
    private static MockManifest serverManifest;

    @BeforeAll
    static void runCodegen() {
        Model model = Model.assembler()
                .discoverModels(ErlangRuntimeIntegrationTest.class.getClassLoader())
                .addUnparsedModel("awsjson11.smithy", AWSJSON11_MODEL)
                .assemble()
                .unwrap();

        ObjectNode clientSettings = Node.objectNodeBuilder()
                .withMember("service", "example.awsjson11#AwsJson11Service")
                .withMember("module", "awsjson11")
                .withMember("edition", "2025")
                .build();

        ObjectNode serverSettings = Node.objectNodeBuilder()
                .withMember("service", "example.awsjson11#AwsJson11Service")
                .withMember("module", "awsjson11")
                .withMember("edition", "2025")
                .build();

        clientManifest = new MockManifest();
        new ErlangClientCodegenPlugin().execute(PluginContext.builder()
                .fileManifest(clientManifest)
                .model(model)
                .settings(clientSettings)
                .build());

        serverManifest = new MockManifest();
        new ErlangServerCodegenPlugin().execute(PluginContext.builder()
                .fileManifest(serverManifest)
                .model(model)
                .settings(serverSettings)
                .build());
    }

    @Test
    void clientRuntimeFilesLandInOutputDir() {
        assertThat(clientManifest.getFileString("src/generated/smithy_http_client.erl")).isPresent();
        assertThat(clientManifest.getFileString("src/generated/smithy_json.erl")).isPresent();
    }

    @Test
    void clientRuntimeFilesAreNotInRuntimeTree() {
        assertThat(clientManifest.getFiles())
                .noneMatch(p -> p.toString().contains("runtime/"));
    }

    @Test
    void serverGeneratedFilesLandInOutputDir() {
        assertThat(serverManifest.getFiles())
                .isNotEmpty()
                .allMatch(p -> p.toString().contains("src/generated/"));
    }

    @Test
    void serverManifestHasNoRuntimeTree() {
        assertThat(serverManifest.getFiles())
                .noneMatch(p -> p.toString().contains("runtime/"));
    }

    @Test
    void serverHandlerRuntimeFileLandsInOutputDir() {
        // Every generated server module declares `-behaviour(smithy_handler).`,
        // so the corresponding runtime module must be copied alongside it.
        assertThat(serverManifest.getFileString("src/generated/smithy_handler.erl")).isPresent();
    }

    @Test
    void clientRuntimeFilesRespectCustomOutputDir() {
        Model model = Model.assembler()
                .discoverModels(ErlangRuntimeIntegrationTest.class.getClassLoader())
                .addUnparsedModel("awsjson11.smithy", AWSJSON11_MODEL)
                .assemble()
                .unwrap();

        ObjectNode settings = Node.objectNodeBuilder()
                .withMember("service", "example.awsjson11#AwsJson11Service")
                .withMember("module", "awsjson11")
                .withMember("edition", "2025")
                .withMember("outputDir", "custom/output")
                .build();

        MockManifest manifest = new MockManifest();
        new ErlangClientCodegenPlugin().execute(PluginContext.builder()
                .fileManifest(manifest)
                .model(model)
                .settings(settings)
                .build());

        assertThat(manifest.getFileString("custom/output/smithy_http_client.erl")).isPresent();
        assertThat(manifest.getFiles())
                .noneMatch(p -> p.toString().contains("runtime/"));
    }
}
