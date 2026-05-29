package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class EnumRoundTripTest {

    @Test
    void enumCodecHelperEmitsUnknownClause() {
        URL resource = getClass().getResource("/model/protocol_rest_json_fixture.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
                        .withMember("edition", "2026")
                        .withMember("protocol", "aws.protocols#restJson1")
                        .build())
                .build());

        String codec = manifest.getFileString("protocoljson_service_rest_json_1.erl").orElse("");
        assertThat(codec).contains("when is_binary(V) -> {unknown, V}");
        assertThat(codec).contains("encode_");
        assertThat(codec).contains("{unknown, V}) when is_binary(V) -> V");
    }
}
