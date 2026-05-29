package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangClientErrorDispatchTest {

    @Test
    void errorDispatcherEmitsStatusClauseAndUnknownFallback() {
        String codec = generateGetItemCodec();
        assertThat(codec).contains("decode_get_item_response_error(");
        assertThat(codec).contains("{error, #not_found_error{");
        assertThat(codec).contains("unknown_error");
        assertThat(codec).contains("__type");
    }

    @Test
    void httpErrorStatusClauseComesBeforeTypeDiscriminator() {
        String codec = generateGetItemCodec();
        int statusClausePos = codec.indexOf("decode_get_item_response_error(404,");
        int typeClausePos = codec.indexOf("__type");
        assertThat(statusClausePos).isGreaterThan(0);
        assertThat(statusClausePos).isLessThan(typeClausePos);
    }

    private static String generateGetItemCodec() {
        URL resource = ErlangClientErrorDispatchTest.class.getResource("/model/error_shapes.smithy");
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
                        .withMember("service",
                                "smithy.beam.demo.error_shapes#ErrorFixtureService")
                        .withMember("edition", "2026")
                        .withMember("protocol", "aws.protocols#restJson1")
                        .build())
                .build());
        return manifest.expectFileString("error_shapes_service_rest_json_1.erl");
    }
}
