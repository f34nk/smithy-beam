package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangTypesPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.codegen.core.CodegenException;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErlangTypesPluginTest {

    private static final String TYPES_FILE = "basic_types.hrl";

    private static Model loadModel() {
        URL resource = ErlangTypesPluginTest.class.getResource("/model/basic.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static PluginContext buildContext(Model model, FileManifest manifest) {
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
    void generatesExpectedTypesInBasicTypesHeader() {
        Model model = loadModel();
        MockManifest manifest = new MockManifest();

        new ErlangTypesPlugin().execute(buildContext(model, manifest));

        String content = manifest.expectFileString(TYPES_FILE);

        assertThat(content)
                .contains("%% Record and type definitions for the basic model.")
                .contains("-type basic_string() :: binary().")
                .contains("-type basic_integer() :: integer().")
                .contains("-type basic_long() :: integer().")
                .contains("-type basic_float() :: float().")
                .contains("-type basic_boolean() :: boolean().")
                .contains("-type basic_blob() :: binary().")
                .contains("-type basic_byte() :: integer().")
                .contains("-type basic_short() :: integer().")
                .contains("-type basic_double() :: float().")
                .contains("-type basic_big_integer() :: integer().")
                .contains("basic_big_decimal()")
                .contains("decimal:decimal()")
                .contains("-type basic_timestamp() :: erlang:timestamp().")
                .contains("-type basic_document() :: term().")
                .contains("-type basic_list() :: [basic_string()].")
                .contains("-type basic_map() :: #{basic_string() => basic_string()}.")
                .contains("-type basic_status() :: active | inactive | pending | {unknown, binary()}.")
                .contains("-type basic_priority() :: low | medium | high | {unknown, integer()}.")
                .contains("-type basic_union() ::")
                .contains("{text, basic_string()}")
                .contains("{number, basic_integer()}")
                .contains("{flag, basic_boolean()}")
                .contains("{unknown, binary()}")
                .contains("-record(basic_item, {")
                .contains("name :: basic_string(),")
                .contains("count :: basic_integer() | undefined")
                .contains("-type basic_item() :: #basic_item{}.");
    }

    private static Model loadReservedWordsModel() {
        URL resource = ErlangTypesPluginTest.class.getResource("/model/reserved_words.smithy");
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
    void reservedWordsEscapeAndDeconflictInErlangOutput() {
        MockManifest manifest = new MockManifest();
        new ErlangTypesPlugin().execute(buildReservedWordsContext(manifest));
        String content = manifest.expectFileString("reserved_types.hrl");
        assertThat(content)
                .contains("after_")
                .contains("begin_")
                .contains("case_")
                .contains("end_")
                .contains("receive_")
                .contains("{case_, rw_string()}")
                .contains("{end_, rw_string()}")
                .contains("receive_ ::")
                .contains("after_ ::")
                .contains("my_type_2");
    }

    @Test
    void reachableErrorShapeFailsInErlangPlugin() {
            URL resource = ErlangTypesPluginTest.class.getResource("/model/error_shapes.smithy");
            assertThat(resource).isNotNull();
            Model model = Model.assembler()
                            .addImport(resource)
                            .discoverModels()
                            .assemble()
                            .unwrap();
            ObjectNode settings = ObjectNode.builder()
                            .withMember("service", "smithy.beam.demo.errors#ErrorDemoService")
                            .withMember("edition", "2026")
                            .build();
            PluginContext context = PluginContext.builder()
                            .model(model)
                            .fileManifest(new MockManifest())
                            .settings(settings)
                            .build();
            assertThatThrownBy(() -> new ErlangTypesPlugin().execute(context))
                            .isInstanceOf(CodegenException.class)
                            .hasMessageContaining("smithy.beam.demo.errors#NotImplementedYet")
                            .hasMessageContaining("only emits type definitions");
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
            new ErlangTypesPlugin().execute(context);
            assertThat(manifest.expectFileString("basic_types.hrl")).contains("-type basic_string()");
    }

    @Test
    void multipleServicesWithoutExplicitServiceSettingFails() {
            URL resource = ErlangTypesPluginTest.class.getResource("/model/multi_service.smithy");
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
            assertThatThrownBy(() -> new ErlangTypesPlugin().execute(context))
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
            assertThatThrownBy(() -> new ErlangTypesPlugin().execute(context))
                            .isInstanceOf(CodegenException.class)
                            .hasMessageContaining("edition");
    }

    private static Model loadMultiServiceModel() {
        URL resource = ErlangTypesPluginTest.class.getResource("/model/multi_service.smithy");
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

    @Test
    void relativeDateAndRelativeVersionWithoutProtocolDoNotChangeMultiServiceTypesOutput() {
        Model model = loadMultiServiceModel();
        MockManifest baseline = new MockManifest();
        ObjectNode baselineSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.multi#ServiceA")
                .withMember("edition", "2026")
                .build();
        new ErlangTypesPlugin().execute(pluginContext(model, baseline, baselineSettings));

        MockManifest extended = new MockManifest();
        ObjectNode extendedSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.multi#ServiceA")
                .withMember("edition", "2026")
                .withMember("relativeDate", "2026-01-01")
                .withMember("relativeVersion", "1.0.0")
                .build();
        new ErlangTypesPlugin().execute(pluginContext(model, extended, extendedSettings));

        assertThat(extended.expectFileString("multi_types.hrl"))
                .isEqualTo(baseline.expectFileString("multi_types.hrl"));
    }

    @Test
    void explicitInvalidProtocolFailsWithCodegenException() {
        Model model = loadMultiServiceModel();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.multi#ServiceA")
                .withMember("edition", "2026")
                .withMember("protocol", "smithy.api#String")
                .withMember("relativeDate", "2026-01-01")
                .withMember("relativeVersion", "1.0.0")
                .build();

        assertThatThrownBy(() -> new ErlangTypesPlugin().execute(pluginContext(model, manifest, settings)))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("protocol")
                .hasMessageContaining("smithy.api#String");
    }

    private static Model loadRelativeDeprecationModel() {
        URL resource = ErlangTypesPluginTest.class.getResource("/model/relative_deprecation.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static Model loadMemberOrderModel() {
        URL resource = ErlangTypesPluginTest.class.getResource("/model/member_order.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static Model loadNullableMembersModel() {
        URL resource = ErlangTypesPluginTest.class.getResource("/model/nullable_members.smithy");
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
        new ErlangTypesPlugin().execute(pluginContext(model, manifest, settings));

        String content = manifest.expectFileString("nullable_members_types.hrl");

        assertThat(content)
                .contains("-type nm_list() :: [nm_string()].")
                .contains("label :: nm_string(),")
                .contains("count :: nm_integer() | undefined")
                .contains("tags :: nm_list() | undefined");
        assertThat(content.substring(content.indexOf("-record(mixed_nullable")))
                .doesNotContain("label :: nm_string() | undefined");
    }

    @Test
    void structureRecordFieldsFollowSmithyMemberDeclarationOrder() {
        Model model = loadMemberOrderModel();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.member_order#MemberOrderService")
                .withMember("edition", "2026")
                .build();
        new ErlangTypesPlugin().execute(pluginContext(model, manifest, settings));

        String content = manifest.expectFileString("member_order_types.hrl");
        int zebra = content.indexOf("zebra ::");
        int alpha = content.indexOf("alpha ::");
        int mike = content.indexOf("mike ::");
        assertThat(zebra).isGreaterThan(-1);
        assertThat(alpha).isGreaterThan(-1);
        assertThat(mike).isGreaterThan(-1);
        assertThat(zebra).isLessThan(alpha);
        assertThat(alpha).isLessThan(mike);
    }

    @Test
    void relativeDateRemovesDeprecatedStringShapeFromGeneratedTypes() {
        Model model = loadRelativeDeprecationModel();

        MockManifest baseline = new MockManifest();
        ObjectNode baselineSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.relative_deprecation#RelativeDeprecationService")
                .withMember("edition", "2026")
                .build();
        new ErlangTypesPlugin().execute(pluginContext(model, baseline, baselineSettings));
        assertThat(baseline.expectFileString("relative_deprecation_types.hrl"))
                .contains("-type legacy_string() :: binary().");

        MockManifest filtered = new MockManifest();
        ObjectNode filteredSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.relative_deprecation#RelativeDeprecationService")
                .withMember("edition", "2026")
                .withMember("relativeDate", "2026-01-01")
                .build();
        new ErlangTypesPlugin().execute(pluginContext(model, filtered, filteredSettings));
        assertThat(filtered.expectFileString("relative_deprecation_types.hrl"))
                .doesNotContain("-type legacy_string() :: binary().");
    }
}
