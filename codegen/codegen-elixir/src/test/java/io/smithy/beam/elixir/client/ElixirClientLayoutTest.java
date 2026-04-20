package io.smithy.beam.elixir.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * End-to-end layout assertions for the Elixir client codegen against the
 * {@code awsJson1_1} protocol fixture.
 *
 * <p>Drives the full {@code DirectedCodegen} pipeline (including all
 * discovered {@code ElixirIntegration}s) and asserts the structural
 * properties that the per-component unit tests cannot — in particular the
 * relative positioning of {@code @spec} lines, the public {@code def}, and
 * the private {@code defp <op>_op/1} helper.
 */
class ElixirClientLayoutTest {

    private static final String MODEL = String.join("\n",
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
            "structure DescribeItemInput { itemId: String }",
            "structure DescribeItemOutput { name: String, description: String }");

    private static String src;

    @BeforeAll
    static void runCodegen() {
        Model model = Model.assembler()
                .discoverModels(ElixirClientLayoutTest.class.getClassLoader())
                .addUnparsedModel("awsjson11.smithy", MODEL)
                .assemble()
                .unwrap();

        ObjectNode settings = Node.objectNodeBuilder()
                .withMember("service", "example.awsjson11#AwsJson11Service")
                .withMember("namespace", "AwsJson11Service")
                .withMember("edition", "2025")
                .build();

        MockManifest manifest = new MockManifest();
        PluginContext ctx = PluginContext.builder()
                .fileManifest(manifest)
                .model(model)
                .settings(settings)
                .build();

        new ElixirClientCodegenPlugin().execute(ctx);
        src = manifest.expectFileString("src/generated/aws_json11_service_client.ex");
    }

    @Test
    void specIsAdjacentToPublicDef() {
        assertThat(src).containsPattern(
                "@spec describe_item\\(map\\(\\), DescribeItemInput\\.t\\(\\)\\) ::"
                        + "[\\s\\S]*?\\}\\s+def describe_item\\(config, input\\) do");
    }

    @Test
    void publicDefDispatchesToOpHelper() {
        assertThat(src).contains("def describe_item(config, input) do");
        assertThat(src).contains("SmithyClient.execute(config, describe_item_op(input))");
    }

    @Test
    void privateHelperHasDocAndSpec() {
        assertThat(src).containsPattern(
                "@doc false\\s+@spec describe_item_op\\(DescribeItemInput\\.t\\(\\)\\)"
                        + " :: SmithyClient\\.Operation\\.t\\(\\)\\s+"
                        + "defp describe_item_op\\(%DescribeItemInput\\{\\} = input\\) do");
    }

    @Test
    void noNotImplementedStubInGeneratedOutput() {
        assertThat(src).doesNotContain(":not_implemented");
    }

    @Test
    void privateHelperAppearsAfterPublicDef() {
        int publicIdx = src.indexOf("def describe_item(config, input) do");
        int helperIdx = src.indexOf("defp describe_item_op(");
        assertThat(publicIdx).isPositive();
        assertThat(helperIdx).isGreaterThan(publicIdx);
    }
}
