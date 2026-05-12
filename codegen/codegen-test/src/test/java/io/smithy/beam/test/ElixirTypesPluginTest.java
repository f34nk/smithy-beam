package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirTypesPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.codegen.core.CodegenException;

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

    @Test
    void reachableErrorShapeFailsInElixirPlugin() {
        URL resource = ElixirTypesPluginTest.class.getResource("/model/error_shapes.smithy");
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
        assertThatThrownBy(() -> new ElixirTypesPlugin().execute(context))
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
        new ElixirTypesPlugin().execute(context);
        assertThat(manifest.expectFileString("lib/generated/basic_types.ex")).contains("basic_string");
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
        assertThat(extended.expectFileString("lib/generated/multi_types.ex"))
                .isEqualTo(baseline.expectFileString("lib/generated/multi_types.ex"));
    }
}
