package io.smithy.beam.test;

import io.smithy.beam.elixir.ElixirClientPlugin;
import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class HttpPrefixHeadersTest {

    private static final String MODEL = """
            $version: "2"
            namespace smithy.beam.test.prefixheaders

            use aws.protocols#restJson1

            @restJson1
            service PrefixHeadersService {
                version: "2026"
                operations: [PutObject, GetObject]
            }

            @http(method: "PUT", uri: "/objects/{key}")
            operation PutObject {
                input: PutObjectInput
                output: PutObjectOutput
            }

            structure PutObjectInput {
                @required
                @httpLabel
                key: String

                @httpPrefixHeaders("x-amz-meta-")
                metadata: MetadataMap
            }

            map MetadataMap {
                key: String
                value: String
            }

            structure PutObjectOutput {
                etag: String
            }

            @http(method: "GET", uri: "/objects/{key}")
            @readonly
            operation GetObject {
                input: GetObjectInput
                output: GetObjectOutput
            }

            structure GetObjectInput {
                @required
                @httpLabel
                key: String
            }

            structure GetObjectOutput {
                @httpPrefixHeaders("x-amz-meta-")
                metadata: MetadataMap

                @httpHeader("ETag")
                etag: String
            }
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
                        .withMember("service",
                                "smithy.beam.test.prefixheaders#PrefixHeadersService")
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
                        .withMember("service",
                                "smithy.beam.test.prefixheaders#PrefixHeadersService")
                        .withMember("edition", "2026")
                        .build())
                .build());
        return manifest;
    }

    @Test
    void requestPrefixHeadersMergedIntoHeadersOnErlangEncode() {
        String codec = runErlangPlugin(loadModel())
                .getFileString("prefix_headers_service_rest_json_1.erl")
                .orElse("");
        assertThat(codec).contains("encode_put_object_request(");
        assertThat(codec).contains("Headers = Headers ++ prefix_headers_to_list(<<\"x-amz-meta-\">>, Metadata)");
        assertThat(codec).contains("prefix_headers_to_list(_Prefix, undefined) ->");
        assertThat(codec).contains("prefix_headers_from_list(Headers, Prefix) ->");
    }

    @Test
    void requestPrefixHeadersMergedIntoHeadersOnElixirEncode() {
        String codec = runElixirPlugin(loadModel())
                .getFileString("prefix_headers_service_rest_json_1.ex")
                .orElse("");
        assertThat(codec).contains("def encode_put_object_request(");
        assertThat(codec).contains("prefix_headers_to_list(\"x-amz-meta-\", input.metadata)");
        assertThat(codec).contains("defp prefix_headers_to_list(_prefix, nil), do: []");
        assertThat(codec).contains("defp prefix_headers_from_list(headers, prefix) do");
    }

    @Test
    void requestPrefixHeadersReconstructedFromHeadersOnErlangDecode() {
        String codec = runErlangPlugin(loadModel())
                .getFileString("prefix_headers_service_rest_json_1.erl")
                .orElse("");
        assertThat(codec).contains("decode_put_object_request(");
        assertThat(codec).contains("metadata = prefix_headers_from_list(Headers, <<\"x-amz-meta-\">>)");
    }

    @Test
    void responsePrefixHeadersReconstructedFromHeadersOnErlangDecode() {
        String codec = runErlangPlugin(loadModel())
                .getFileString("prefix_headers_service_rest_json_1.erl")
                .orElse("");
        assertThat(codec).contains("decode_get_object_response(");
        assertThat(codec).contains("metadata = prefix_headers_from_list(Headers, <<\"x-amz-meta-\">>)");
    }

    @Test
    void requestPrefixHeadersReconstructedFromHeadersOnElixirDecode() {
        String codec = runElixirPlugin(loadModel())
                .getFileString("prefix_headers_service_rest_json_1.ex")
                .orElse("");
        assertThat(codec).contains("def decode_put_object_request(");
        assertThat(codec).contains("metadata: prefix_headers_from_list(headers, \"x-amz-meta-\")");
    }

    @Test
    void responsePrefixHeadersReconstructedFromHeadersOnElixirDecode() {
        String codec = runElixirPlugin(loadModel())
                .getFileString("prefix_headers_service_rest_json_1.ex")
                .orElse("");
        assertThat(codec).contains("def decode_get_object_response(");
        assertThat(codec).contains("metadata: prefix_headers_from_list(headers, \"x-amz-meta-\")");
    }
}
