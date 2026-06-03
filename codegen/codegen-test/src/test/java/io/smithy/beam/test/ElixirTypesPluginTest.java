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
    void generatesExpectedTypesInBasicServiceTypesModule() {
        Model model = loadModel();
        MockManifest manifest = new MockManifest();
        new ElixirTypesPlugin().execute(buildContext(model, manifest));

        String content = manifest.expectFileString("basic_service_types.ex");

        assertThat(content)
                .contains("defmodule BasicServiceTypes do")
                .contains("Type definitions for the BasicServiceTypes model.")
                .contains("@type basic_string :: String.t()")
                .contains("@type basic_integer :: integer()")
                .contains("@type basic_long :: integer()")
                .contains("@type basic_float :: float()")
                .contains("@type basic_boolean :: boolean()")
                .contains("@type basic_blob :: binary()")
                .contains("@type basic_byte :: integer()")
                .contains("@type basic_short :: integer()")
                .contains("@type basic_double :: float()")
                .contains("@type basic_big_integer :: integer()")
                .contains("@type basic_big_decimal :: Decimal.t()")
                .contains("@type basic_timestamp :: DateTime.t()")
                .contains("@type basic_document :: any()")
                .contains("@type basic_list :: [")
                .contains(".basic_string()]")
                .contains("@type basic_map :: %{")
                .contains(".basic_string() => ")
                .contains(".basic_string()}")
                .contains("defmodule BasicStatus do")
                .contains(":active | :inactive | :pending | {:unknown, String.t()}")
                .contains("defmodule BasicPriority do")
                .contains(":low | :medium | :high | {:unknown, integer()}")
                .contains("@type basic_union ::")
                .contains("{:text, BasicServiceTypes.basic_string()}")
                .contains("{:number, BasicServiceTypes.basic_integer()}")
                .contains("{:flag, BasicServiceTypes.basic_boolean()}")
                .contains("{:unknown, String.t()}")
                .contains("defmodule BasicItem do")
                .contains("name: BasicServiceTypes.basic_string(),")
                .contains("count: BasicServiceTypes.basic_integer() | nil");
    }

    private static PluginContext buildContext(Model model, MockManifest manifest) {
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
    void restJson1ServiceEmitsTypesOnlyWithoutWireModules() {
        Model model = loadReservedWordsModel();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.reserved#ReservedService")
                .withMember("edition", "2026")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, manifest, settings));

        assertThat(manifest.expectFileString("reserved_service_types.ex")).contains("@type");
        assertThat(manifest.getFileString("reserved_service_rest_json_1.ex")).isEmpty();
        assertThat(manifest.getFileString("reserved_service_router.ex")).isEmpty();
        assertThat(manifest.getFileString("runtime_http.ex")).isEmpty();
    }

    @Test
    void reservedWordsEscapeAndDeconflictInElixirOutput() {
        MockManifest manifest = new MockManifest();
        new ElixirTypesPlugin().execute(buildReservedWordsContext(manifest));
        String content = manifest.expectFileString("reserved_service_types.ex");
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

    private static Model loadErrorShapesModel() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/error_shapes.smithy");
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
        Model model = loadErrorShapesModel();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.error_shapes#ErrorShapeService")
                .withMember("edition", "2026")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, manifest, settings));

        String content = manifest.expectFileString("error_shape_service_types.ex");

        assertThat(content)
                .contains(
                        "# Error shape: smithy.beam.demo.error_shapes#ServiceUnavailable (server)")
                .contains("defmodule ServiceUnavailable do")
                .contains(
                        "Error from smithy.beam.demo.error_shapes#ServiceUnavailable (fault: server, retryable: true).")
                .contains("defexception [")
                .contains("message: nil,")
                .contains("__beam_error_kind: :server")
                .contains("def message(e), do: inspect(e)");
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

        String content = manifest.expectFileString("sparse_collections_service_types.ex");

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

        String content = manifest.expectFileString("recursive_tree_service_types.ex");

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

        String content = manifest.expectFileString("streaming_blob_service_types.ex");

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

        String content = manifest.expectFileString("nullable_members_service_types.ex");

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

        String content = manifest.expectFileString("member_order_service_types.ex");
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

        String content = manifest.expectFileString("dedicated_io_service_types.ex");

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
        assertThat(manifest.expectFileString("basic_service_types.ex")).contains("basic_string");
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
    void relativeDateAndRelativeVersionWithModelProtocolDoNotChangeElixirTypeOnlyOutput() {
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
                .withMember("relativeDate", "2026-01-01")
                .withMember("relativeVersion", "1.0.0")
                .build();
        new ElixirTypesPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(extended)
                .settings(extendedSettings)
                .build());
        assertThat(extended.expectFileString("service_a_types.ex"))
                .isEqualTo(baseline.expectFileString("service_a_types.ex"));
    }

    private static Model loadRelativeDeprecationModel() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/relative_deprecation.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void relativeDateRemovesDeprecatedStringShapeFromGeneratedTypes() {
        Model model = loadRelativeDeprecationModel();

        MockManifest baseline = new MockManifest();
        ObjectNode baselineSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.relative_deprecation#RelativeDeprecationService")
                .withMember("edition", "2026")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, baseline, baselineSettings));
        assertThat(baseline.expectFileString("relative_deprecation_service_types.ex"))
                .contains("@type legacy_string :: String.t()");

        MockManifest filtered = new MockManifest();
        ObjectNode filteredSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.relative_deprecation#RelativeDeprecationService")
                .withMember("edition", "2026")
                .withMember("relativeDate", "2026-01-01")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, filtered, filteredSettings));
        assertThat(filtered.expectFileString("relative_deprecation_service_types.ex"))
                .doesNotContain("@type legacy_string :: String.t()");
    }

    @Test
    void documentedTypesEmitShapeAndMemberDocs() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/documented_types.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.documented_types#DocumentedTypesService")
                .withMember("edition", "2026")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, manifest, settings));

        String content = manifest.expectFileString("documented_types_service_types.ex");

        assertThat(content).contains("@moduledoc \"\"\"");
        assertThat(content).contains("A documented structure with member docs.");
        assertThat(content).contains("## Members");
        assertThat(content).contains("`name` - Human-readable item name.");
        assertThat(content).contains("@typedoc");
        assertThat(content).contains("Tagged union carrying documented variants.");
    }

    @Test
    void relativeVersionRemovesDeprecatedStringShapeFromGeneratedTypes() {
        Model model = loadRelativeDeprecationModel();

        MockManifest baseline = new MockManifest();
        ObjectNode baselineSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.relative_deprecation#RelativeDeprecationService")
                .withMember("edition", "2026")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, baseline, baselineSettings));
        assertThat(baseline.expectFileString("relative_deprecation_service_types.ex"))
                .contains("@type legacy_version_string :: String.t()");

        MockManifest filtered = new MockManifest();
        ObjectNode filteredSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.relative_deprecation#RelativeDeprecationService")
                .withMember("edition", "2026")
                .withMember("relativeVersion", "1.0.0")
                .build();
        new ElixirTypesPlugin().execute(pluginContext(model, filtered, filteredSettings));
        assertThat(filtered.expectFileString("relative_deprecation_service_types.ex"))
                .doesNotContain("@type legacy_version_string :: String.t()");
    }
}
