package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.elixir.ElixirServerPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class ElixirRestJson1CodecTest {

    private static final String JSON_NAME_MODEL = """
            $version: "2"
            namespace smithy.beam.test.jsonname

            use aws.protocols#restJson1

            @restJson1
            service JsonNameService {
                version: "2026"
                operations: [GetItem]
            }

            @http(method: "GET", uri: "/items/{id}")
            @readonly
            operation GetItem {
                input: GetItemInput
                output: GetItemOutput
            }

            structure GetItemInput {
                @required @httpLabel
                id: String
            }

            structure GetItemOutput {
                @jsonName("displayName")
                name: String
            }
            """;

    private static MockManifest runElixirClient(Model model, String service) {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", service)
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    @Test
    void jsonNameUsesWireKeyOnEncode() {
        Model model = Model.assembler()
                .addUnparsedModel("test.smithy", JSON_NAME_MODEL)
                .discoverModels()
                .assemble()
                .unwrap();
        String codec = runElixirClient(model, "smithy.beam.test.jsonname#JsonNameService")
                .getFileString("json_name_service_rest_json_1.ex")
                .orElse("");
        assertThat(codec).contains("\"displayName\"");
        assertThat(codec).doesNotContain("\"name\" =>");
    }

    @Test
    void sparseNullEncodesAsJsonNull() {
        URL resource = getClass().getResource("/model/sparse_collections.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        String codec = runElixirClient(
                model, "smithy.beam.demo.sparse_collections#SparseCollectionsRestJson")
                .getFileString("sparse_collections_rest_json_rest_json_1.ex")
                .orElse("");
        assertThat(codec).contains("decode_sparse_list(");
        assertThat(codec).contains("fn nil -> nil");
        assertThat(codec).contains("decode_sparse_map(");
        assertThat(codec).contains("Jason.decode!");
    }

    @Test
    void clientErrorDispatchMatchesModeledHttpError() {
        URL resource = getClass().getResource("/model/error_shapes.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        String codec = runElixirClient(model, "smithy.beam.demo.error_shapes#ErrorFixtureService")
                .getFileString("error_fixture_service_rest_json_1.ex")
                .orElse("");
        assertThat(codec).contains("defp decode_get_item_response_error(404,");
        assertThat(codec).contains("struct!(ErrorShapesTypes.NotFoundError");
        assertThat(codec).contains("__type");
        assertThat(codec).contains("unknown_error");
    }

    @Test
    void serverResponseEncoderEmitsSuccessAndErrorPaths() {
        URL resource = getClass().getResource("/model/protocol_rest_json_fixture.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        new ElixirServerPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
                        .withMember("edition", "2026")
                        .build())
                .build());
        String serverCodec = manifest.getFiles().stream()
                .map(p -> p.getFileName().toString())
                .filter(name -> name.endsWith("rest_json_1.ex"))
                .findFirst()
                .flatMap(manifest::getFileString)
                .orElse("");
        assertThat(serverCodec).contains("def encode_describe_item_response");
        assertThat(serverCodec).contains("def encode_create_item_response");
        assertThat(serverCodec).contains("status: 200");
        assertThat(serverCodec).contains("status: 201");
    }
}
