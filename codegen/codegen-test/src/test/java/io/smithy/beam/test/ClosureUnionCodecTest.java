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

class ClosureUnionCodecTest {

    private static final String SERVICE = "smithy.beam.test.nestedunion#NestedUnionService";

    private static Model loadModel() {
        URL resource = ClosureUnionCodecTest.class.getResource("/model/nested_document_union_fixture.smithy");
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
    void closureUnionHelpersEmitForNestedDocumentMembers() {
        MockManifest manifest = runErlangClient(loadModel());
        String codec = manifest.expectFileString(findAwsJson11Codec(manifest));
        assertThat(codec).contains("encode_widget_value(");
        assertThat(codec).contains("decode_widget_value(");
        assertThat(codec).contains("encode_expected_widget_value(");
        assertThat(codec).contains("decode_expected_widget_value(");
        assertThat(codec).contains("encode_widget_value(Record#expected_widget_value.value)");
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
