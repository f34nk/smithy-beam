package io.smithy.beam.test;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.erlang.ErlangClientPlugin;
import java.net.URL;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

class SparseCodecRoundTripTest {

  @Test
  void sparseListMemberUsesDecodeSparseList() {
    URL resource = getClass().getResource("/model/sparse_collections.smithy");
    assertThat(resource).isNotNull();
    Model model = Model.assembler().addImport(resource).discoverModels().assemble().unwrap();
    MockManifest manifest = new MockManifest();
    new ErlangClientPlugin()
        .execute(
            PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(
                    ObjectNode.builder()
                        .withMember(
                            "service",
                            "smithy.beam.demo.sparse_collections#SparseCollectionsRestJson")
                        .withMember("edition", "2026")
                        .build())
                .build());

    String codec =
        manifest.getFileString("sparse_collections_rest_json_rest_json_1.erl").orElse("");
    assertThat(codec).contains("decode_sparse_list(");
    assertThat(codec).contains("decode_list(");
    assertThat(codec).contains("null -> undefined");
    assertThat(codec).contains("encode_sparse_list(");
    assertThat(codec).contains("items = decode_sparse_list(");
    assertThat(codec).contains("counts = decode_sparse_map(");
    assertThat(codec).contains("tags = decode_list(");
  }
}
