package io.smithy.beam.erlang.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * End-to-end layout assertions for the Erlang client codegen against the
 * {@code awsJson1_1} protocol fixture.
 *
 * <p>Drives the full {@code DirectedCodegen} pipeline (including all
 * discovered {@code ErlangIntegration}s) and asserts the structural
 * properties that the per-component unit tests cannot — in particular the
 * relative positioning of {@code -spec} lines, public function clauses,
 * and the internal {@code make_<op>_request/2} helper.
 */
class ErlangClientLayoutTest {

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
                .discoverModels(ErlangClientLayoutTest.class.getClassLoader())
                .addUnparsedModel("awsjson11.smithy", MODEL)
                .assemble()
                .unwrap();

        ObjectNode settings = Node.objectNodeBuilder()
                .withMember("service", "example.awsjson11#AwsJson11Service")
                .withMember("module", "awsjson11")
                .withMember("edition", "2025")
                .build();

        MockManifest manifest = new MockManifest();
        PluginContext ctx = PluginContext.builder()
                .fileManifest(manifest)
                .model(model)
                .settings(settings)
                .build();

        new ErlangClientCodegenPlugin().execute(ctx);
        src = manifest.expectFileString("src/generated/awsjson11_client.erl");
    }

    @Test
    void specIsAdjacentToPublicFunction() {
        assertThat(src).containsPattern(
                "-spec describe_item\\(Client :: map\\(\\), Input :: describe_item_input\\(\\)\\)"
                        + "[\\s\\S]*?\\.\\s+describe_item\\(Client, Input\\) ->");
    }

    @Test
    void publicFunctionDispatchesToHelper() {
        assertThat(src).contains("describe_item(Client, Input) ->");
        assertThat(src).contains("describe_item(Client, Input, #{}).");
        assertThat(src).contains("make_describe_item_request(Client, Input)");
        assertThat(src).contains("smithy_retry:with_retry(");
    }

    @Test
    void helperHasItsOwnSpec() {
        assertThat(src).containsPattern(
                "-spec make_describe_item_request\\(Client :: map\\(\\),"
                        + " Input :: describe_item_input\\(\\)\\)"
                        + "[\\s\\S]*?\\.\\s+make_describe_item_request\\(Client, Input\\)");
    }

    @Test
    void parseErrorDefinedOnceWithCorrectName() {
        int occurrences = countOccurrences(src, "parse_error(StatusCode, Body) ->");
        assertThat(occurrences).isEqualTo(1);
        assertThat(src).contains("-spec parse_error(StatusCode ::");
        assertThat(src).doesNotContain("parse_describe_item_error(");
    }

    @Test
    void noNotImplementedStubInGeneratedOutput() {
        assertThat(src).doesNotContain("{error, not_implemented}");
    }

    @Test
    void helperAppearsAfterPublicFunction() {
        int publicIdx = src.indexOf("describe_item(Client, Input) ->");
        int helperIdx = src.indexOf("make_describe_item_request(Client, Input)");
        assertThat(publicIdx).isPositive();
        assertThat(helperIdx).isGreaterThan(publicIdx);
    }

    @Test
    void exportsBothArities() {
        assertThat(src).contains("describe_item/2");
        assertThat(src).contains("describe_item/3");
        assertThat(src).contains("parse_error/2");
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
