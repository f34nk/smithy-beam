package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
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

class NestedDocumentStructureCodecTest {

    private static final String SERVICE = "smithy.beam.test.nesteddoc#NestedDocService";

    private static Model loadModel() {
        URL resource = NestedDocumentStructureCodecTest.class
                .getResource("/model/nested_document_structure_fixture.smithy");
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

    private static MockManifest runElixirClient(Model model) {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
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
    void erlangNestedStructureHelpersEmitForListDocumentMembers() {
        MockManifest manifest = runErlangClient(loadModel());
        String codec = manifest.expectFileString(findAwsJson11Codec(manifest));
        assertThat(codec).contains("decode_execution_input(");
        assertThat(codec).contains("encode_execution_input(");
        assertThat(codec).contains("decode_execution_input_list(");
        assertThat(codec).contains("encode_execution_input_list(");
        assertThat(codec).contains("encode_execution_input_list(Inputs)");
    }

    @Test
    void elixirNestedStructureHelpersEmitForListDocumentMembers() {
        MockManifest manifest = runElixirClient(loadModel());
        String codec = manifest.expectFileString(findElixirAwsJson11Codec(manifest));
        assertThat(codec).contains("defp decode_execution_input(");
        assertThat(codec).contains("defp encode_execution_input(");
        assertThat(codec).contains("defp decode_execution_input_list(");
        assertThat(codec).contains("defp encode_execution_input_list(");
        assertThat(codec).contains("decode_execution_input_list(");
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

    private static String findElixirAwsJson11Codec(MockManifest manifest) {
        return manifest.getFiles().stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith("aws_json_1_1.ex"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing aws_json_1_1 codec, files: "
                        + manifest.getFiles().stream()
                                .map(Path::getFileName)
                                .map(Path::toString)
                                .collect(Collectors.toList())));
    }
}
