package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ClosureEnumCodecTest {

    private static final String SERVICE = "smithy.beam.test.nestedenum#NestedEnumService";

    private static Model loadModel() {
        URL resource = ClosureEnumCodecTest.class.getResource("/model/nested_document_enum_fixture.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static MockManifest runErlangClient(Model model) {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", SERVICE)
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    @Test
    void closureEnumHelpersEmitForDocumentMembers() {
        MockManifest manifest = runErlangClient(loadModel());
        String codec = manifest.expectFileString(findAwsJson11Codec(manifest));
        assertThat(codec).contains("encode_widget_kind(");
        assertThat(codec).contains("decode_widget_kind(");
        assertThat(codec).contains("encode_create_widget_request(");
        assertThat(codec).contains("encode_widget_kind(Kind)");
    }

    private static String findAwsJson11Codec(MockManifest manifest) {
        return manifest.getFiles().stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith("aws_json_1_1.erl"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing aws_json_1_1 codec, files: "
                        + manifest.getFiles().stream()
                                .map(Path::getFileName)
                                .map(Path::toString)
                                .collect(Collectors.toList())));
    }
}
