package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirTypesPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        String content = manifest.expectFileString("reserved_types.ex");
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

    private static Model loadErrorShapeModel() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/error_shape.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static PluginContext pluginContext(Model model, MockManifest manifest, ObjectNode settings) {
        return PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    @Test
    void errorShapeEmitsModuleWithModeledMetadata() {
        Model model = loadErrorShapeModel();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.error_shape#ErrorShapeService")
                .withMember("edition", "2026")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, manifest, settings));

        String content = manifest.expectFileString("error_shape_types.ex");

        assertThat(content)
                .contains("defmodule ServiceUnavailable do")
                .contains("Error structure ServiceUnavailable.")
                .contains("retryable=true httpCode=503")
                .contains("message:")
                .contains("defstruct [:message]");
    }

    private static Model loadSparseCollectionsModel() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/sparse_collections.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void sparseListAndMapShapesWidenElementAndValueTypes() {
        Model model = loadSparseCollectionsModel();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.sparse_collections#SparseCollectionsService")
                .withMember("edition", "2026")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, manifest, settings));

        String content = manifest.expectFileString("sparse_collections_types.ex");

        assertThat(content)
                .contains("@type sc_sparse_list :: [")
                .contains(".sc_string() | nil]")
                .contains("@type sc_sparse_map :: %{")
                .contains(".sc_string() => ")
                .contains(".sc_integer() | nil}");
    }

    private static Model loadRecursiveTreeModel() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/recursive_tree.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void recursiveAggregatesReferenceNamedTypeAliases() {
        Model model = loadRecursiveTreeModel();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.recursive_tree#RecursiveTreeService")
                .withMember("edition", "2026")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, manifest, settings));

        String content = manifest.expectFileString("recursive_tree_types.ex");

        assertThat(content)
                .contains("@type rt_string :: String.t()")
                .contains("@type rt_node_list :: [")
                .contains(".RtNode.t()]")
                .contains("@type rt_node_map :: %{")
                .contains(".RtNode.t()}")
                .contains("defmodule RtNode do")
                .contains("children:")
                .contains(".rt_node_list()")
                .contains("by_key:")
                .contains(".rt_node_map()");

        assertThat(countOccurrences(content, "@type rt_string ::")).isEqualTo(1);
        assertThat(countOccurrences(content, "@type rt_node_list ::")).isEqualTo(1);
        assertThat(countOccurrences(content, "@type rt_node_map ::")).isEqualTo(1);
        assertThat(countOccurrences(content, "defmodule RtNode do")).isEqualTo(1);

        assertThat(content).doesNotContain("[%RtNode");
        assertThat(content).doesNotContain("%{RtNode");
    }

    private static Model loadStreamingBlobModel() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/streaming_blob.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void streamingBlobAliasCarriesStreamingPayloadComment() {
        Model model = loadStreamingBlobModel();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.streaming_blob#StreamingBlobService")
                .withMember("edition", "2026")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, manifest, settings));

        String content = manifest.expectFileString("streaming_blob_types.ex");

        assertThat(content)
                .contains("# Streaming payload; framing deferred to protocol layer.")
                .contains("@type sb_streaming_payload :: binary()")
                .contains("@type sb_blob :: binary()");
        assertThat(content).doesNotContain("@type sb_blob :: binary()\n# Streaming");
    }

    private static Model loadNullableMembersModel() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/nullable_members.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void mixedRequiredAndOptionalMembersFollowNullableIndex() {
        Model model = loadNullableMembersModel();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.nullable_members#NullableMembersService")
                .withMember("edition", "2026")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, manifest, settings));

        String content = manifest.expectFileString("nullable_members_types.ex");

        assertThat(content)
                .contains("@type nm_list :: [")
                .contains("count: ")
                .contains("| nil")
                .contains("tags: ")
                .contains("| nil");

        int labelIdx = content.indexOf("label:");
        assertThat(labelIdx).isGreaterThan(-1);
        String labelLine = content.substring(labelIdx, content.indexOf('\n', labelIdx));
        assertThat(labelLine).doesNotContain("| nil");
    }

    private static Model loadMemberOrderModel() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/member_order.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void structureFieldsFollowSmithyMemberDeclarationOrder() {
        Model model = loadMemberOrderModel();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.member_order#MemberOrderService")
                .withMember("edition", "2026")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, manifest, settings));

        String content = manifest.expectFileString("member_order_types.ex");
        int zebra = content.indexOf("zebra:");
        int alpha = content.indexOf("alpha:");
        int mike = content.indexOf("mike:");
        assertThat(zebra).isGreaterThan(-1);
        assertThat(alpha).isGreaterThan(-1);
        assertThat(mike).isGreaterThan(-1);
        assertThat(zebra).isLessThan(alpha);
        assertThat(alpha).isLessThan(mike);
    }

    private static Model loadDedicatedOperationIoModel() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/dedicated_operation_io.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void dedicatedOperationIoEmitsEmptyStructsForUnitLikeStructures() {
        Model model = loadDedicatedOperationIoModel();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.dedicated_io#DedicatedIoService")
                .withMember("edition", "2026")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, manifest, settings));

        String content = manifest.expectFileString("dedicated_io_types.ex");

        assertThat(content)
                .contains("defmodule HealthCheckInput do")
                .contains("defstruct []")
                .contains("defmodule HealthCheckOutput do");
        assertThat(countOccurrences(content, "defmodule HealthCheckInput do")).isEqualTo(1);
        assertThat(countOccurrences(content, "defmodule HealthCheckOutput do")).isEqualTo(1);
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
        new ElixirTypesPlugin().execute(context);
        assertThat(manifest.expectFileString("basic_types.ex")).contains("basic_string");
    }

    @Test
    void multipleServicesWithoutExplicitServiceSettingFails() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/multi_service.smithy");
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
        assertThatThrownBy(() -> new ElixirTypesPlugin().execute(context))
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
        assertThatThrownBy(() -> new ElixirTypesPlugin().execute(context))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("edition");
    }

    @Test
    void protocolRelativeDateRelativeVersionDoNotChangeElixirTypeOnlyOutput() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/multi_service.smithy");
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
        new ElixirTypesPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(baseline)
                .settings(baselineSettings)
                .build());
        MockManifest extended = new MockManifest();
        ObjectNode extendedSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.multi#ServiceA")
                .withMember("edition", "2026")
                .withMember("protocol", "smithy.beam.demo.multi#TestProtocol")
                .withMember("relativeDate", "2026-01-01")
                .withMember("relativeVersion", "1.0.0")
                .build();
        new ElixirTypesPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(extended)
                .settings(extendedSettings)
                .build());
        assertThat(extended.expectFileString("multi_types.ex"))
                .isEqualTo(baseline.expectFileString("multi_types.ex"));
    }
}
