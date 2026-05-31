package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class MediaTypeBindingTest {

    private static final String MODEL = """
            $version: "2"
            namespace smithy.beam.test.mediatype

            use aws.protocols#restJson1

            @restJson1
            service MediaTypeService {
                version: "2026"
                operations: [PutDocument, GetDocument, CreateNote]
            }

            @http(method: "PUT", uri: "/documents/{id}")
            operation PutDocument {
                input: PutDocumentInput
                output: PutDocumentOutput
            }

            structure PutDocumentInput {
                @required
                @httpLabel
                id: String

                @httpPayload
                content: DocumentBlob
            }

            structure PutDocumentOutput {
                id: String
            }

            @http(method: "GET", uri: "/documents/{id}")
            @readonly
            operation GetDocument {
                input: GetDocumentInput
                output: GetDocumentOutput
            }

            structure GetDocumentInput {
                @required
                @httpLabel
                id: String
            }

            structure GetDocumentOutput {
                @httpPayload
                content: DocumentBlob
            }

            @http(method: "POST", uri: "/notes")
            operation CreateNote {
                input: CreateNoteInput
                output: CreateNoteOutput
            }

            structure CreateNoteInput {
                text: String
            }

            structure CreateNoteOutput {
                id: String
            }

            @mediaType("application/pdf")
            blob DocumentBlob
            """;

    private static Model loadModel() {
        return Model.assembler()
                .addUnparsedModel("test.smithy", MODEL)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static MockManifest runErlangPlugin(Model model) {
        MockManifest manifest = new MockManifest();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", "smithy.beam.test.mediatype#MediaTypeService")
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    private static MockManifest runElixirPlugin(Model model) {
        MockManifest manifest = new MockManifest();
        new ElixirClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(ObjectNode.builder()
                        .withMember("service", "smithy.beam.test.mediatype#MediaTypeService")
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    @Test
    void httpPayloadMediaTypeInErlangRequestEncoder() {
        String codec = runErlangPlugin(loadModel())
                .getFileString("media_type_service_rest_json_1.erl")
                .orElse("");
        assertThat(codec).contains("encode_put_document_request(");
        assertThat(codec).contains("<<\"Content-Type\">>, <<\"application/pdf\">>");
    }

    @Test
    void httpPayloadMediaTypeInErlangResponseDecoder() {
        String codec = runErlangPlugin(loadModel())
                .getFileString("media_type_service_rest_json_1.erl")
                .orElse("");
        assertThat(codec).contains("decode_get_document_response(");
        assertThat(codec).contains("content_type_matches(Headers, <<\"application/pdf\">>)");
    }

    @Test
    void documentOperationsKeepJsonContentTypeInErlangEncoder() {
        String codec = runErlangPlugin(loadModel())
                .getFileString("media_type_service_rest_json_1.erl")
                .orElse("");
        assertThat(codec).contains("encode_create_note_request(");
        assertThat(codec).contains("<<\"Content-Type\">>, <<\"application/json\">>");
    }

    @Test
    void httpPayloadMediaTypeInElixirRequestEncoder() {
        String codec = runElixirPlugin(loadModel())
                .getFileString("media_type_service_rest_json_1.ex")
                .orElse("");
        assertThat(codec).contains("def encode_put_document_request(");
        assertThat(codec).contains("{\"Content-Type\", \"application/pdf\"}");
    }

    @Test
    void httpPayloadMediaTypeInElixirResponseDecoder() {
        String codec = runElixirPlugin(loadModel())
                .getFileString("media_type_service_rest_json_1.ex")
                .orElse("");
        assertThat(codec).contains("def decode_get_document_response(");
        assertThat(codec).contains("content_type_matches(headers, \"application/pdf\")");
    }

    @Test
    void documentOperationsKeepJsonContentTypeInElixirEncoder() {
        String codec = runElixirPlugin(loadModel())
                .getFileString("media_type_service_rest_json_1.ex")
                .orElse("");
        assertThat(codec).contains("def encode_create_note_request(");
        assertThat(codec).contains("{\"Content-Type\", \"application/json\"}");
    }
}
