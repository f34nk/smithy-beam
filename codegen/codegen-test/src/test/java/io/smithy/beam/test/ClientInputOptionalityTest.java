package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangTypesPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class ClientInputOptionalityTest {

    @Test
    void requiredInputMemberRendersOptionalInTypesHeader() {
        Model model = Model.assembler()
                .addImport(getClass().getResource("/model/protocol_rest_json_fixture.smithy"))
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
                .withMember("edition", "2026")
                .build();
        new ErlangTypesPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        String types = manifest.getFileString("protocoljson_types.hrl").orElse("");
        assertThat(types).contains("describe_item_input");
        assertThat(types).contains("id :: item_id() | undefined");
    }
}
