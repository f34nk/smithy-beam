package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangTypesPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

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
}
