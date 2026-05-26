package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangClientPlugin;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.codegen.core.CodegenException;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErlangClientPluginTest {

    private static final String TYPES_FILE = "basic_types.hrl";
    private static final String CLIENT_FILE = "basic_client.erl";

    private static Model loadModel() {
        URL resource = ErlangClientPluginTest.class.getResource("/model/basic.smithy");
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static PluginContext buildContext(Model model, FileManifest manifest) {
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.basic#BasicService")
                .withMember("edition", "2026")
                .build();
        return PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build();
    }

    @Test
    void emitsTypesHeaderAndClientStubOnManifest() {
        Model model = loadModel();
        MockManifest manifest = new MockManifest();

        new ErlangClientPlugin().execute(buildContext(model, manifest));

        assertThat(manifest.expectFileString(TYPES_FILE)).contains("-type basic_string()");
        assertClientStubHeaderOrder(manifest.expectFileString(CLIENT_FILE));
    }

    @Test
    void defaultsToOnlyServiceWhenServiceSettingOmitted() {
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder().withMember("edition", "2026").build();
        PluginContext context = PluginContext.builder()
                .model(loadModel())
                .fileManifest(manifest)
                .settings(settings)
                .build();
        new ErlangClientPlugin().execute(context);
        assertThat(manifest.expectFileString(TYPES_FILE)).contains("-type basic_string()");
        assertClientStubHeaderOrder(manifest.expectFileString(CLIENT_FILE));
    }

    private static void assertClientStubHeaderOrder(String clientSource) {
        assertThat(clientSource).contains("-module(basic_client).");
        assertThat(clientSource).contains("-include(\"basic_types.hrl\").");
        assertThat(clientSource).contains("-export([get_type_closure/2]).");
        int moduleIndex = clientSource.indexOf("-module(basic_client).");
        int includeIndex = clientSource.indexOf("-include(\"basic_types.hrl\").");
        int exportIndex = clientSource.indexOf("-export([get_type_closure/2]).");
        assertThat(moduleIndex).isLessThan(includeIndex);
        assertThat(includeIndex).isLessThan(exportIndex);
        assertThat(clientSource.stripLeading()).doesNotStartWith("-include");
    }

    @Test
    void multipleServicesWithoutExplicitServiceSettingFails() {
        URL resource = ErlangClientPluginTest.class.getResource("/model/multi_service.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        ObjectNode settings = ObjectNode.builder().withMember("edition", "2026").build();
        PluginContext context = PluginContext.builder()
                .model(model)
                .fileManifest(new MockManifest())
                .settings(settings)
                .build();
        assertThatThrownBy(() -> new ErlangClientPlugin().execute(context))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("service");
    }

    @Test
    void missingEditionFails() {
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.basic#BasicService")
                .build();
        PluginContext context = PluginContext.builder()
                .model(loadModel())
                .fileManifest(manifest)
                .settings(settings)
                .build();
        assertThatThrownBy(() -> new ErlangClientPlugin().execute(context))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("edition");
    }

    @Test
    void relativeDateAndRelativeVersionWithoutProtocolDoNotChangeTypesOrClientStubOutput() {
        URL resource = ErlangClientPluginTest.class.getResource("/model/multi_service.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest baseline = new MockManifest();
        ObjectNode baselineSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.multi#ServiceA")
                .withMember("edition", "2026")
                .build();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(baseline)
                .settings(baselineSettings)
                .build());
        MockManifest extended = new MockManifest();
        ObjectNode extendedSettings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.multi#ServiceA")
                .withMember("edition", "2026")
                .withMember("relativeDate", "2026-01-01")
                .withMember("relativeVersion", "1.0.0")
                .build();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(extended)
                .settings(extendedSettings)
                .build());
        assertThat(extended.expectFileString("multi_types.hrl"))
                .isEqualTo(baseline.expectFileString("multi_types.hrl"));
        assertThat(extended.expectFileString("multi_client.erl"))
                .isEqualTo(baseline.expectFileString("multi_client.erl"));
    }

    @Test
    void restJson1ProtocolWiresClientOperationToCodecAndHttp() {
        URL resource = ErlangClientPluginTest.class.getResource("/model/protocol_rest_json_fixture.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
                .withMember("edition", "2026")
                .withMember("protocol", "aws.protocols#restJson1")
                .build();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        assertThat(manifest.expectFileString("protocoljson_rest_json_1.erl"))
                .contains("-module(protocoljson_rest_json_1).")
                .contains("REST JSON 1 codecs for smithy.beam.demo.protocoljson#DemoRestJson");
        assertThat(manifest.expectFileString("protocoljson_http.erl"))
                .contains("-module(protocoljson_http).")
                .contains("dispatch(Config, #http_request{");
        String client = manifest.expectFileString("protocoljson_client.erl");
        assertThat(client)
                .contains("describe_item(Config, Input) ->")
                .contains("Req = protocoljson_rest_json_1:encode_describe_item_request(Input),")
                .contains("case protocoljson_http:dispatch(Config, Req) of")
                .contains("protocoljson_rest_json_1:decode_describe_item_response(Resp);");
    }

    @Test
    void restJson1ProtocolEmitsRealEncoderAndDecoder() {
        URL resource = ErlangClientPluginTest.class.getResource("/model/protocol_rest_json_fixture.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.protocoljson#DemoRestJson")
                .withMember("edition", "2026")
                .withMember("protocol", "aws.protocols#restJson1")
                .build();
        new ErlangClientPlugin().execute(PluginContext.builder()
                .model(model)
                .fileManifest(manifest)
                .settings(settings)
                .build());

        String codec = manifest.expectFileString("protocoljson_rest_json_1.erl");
        assertThat(codec).contains("encode_describe_item_request(");
        assertThat(codec).contains("decode_describe_item_response(");
        assertThat(codec).contains("http_request{");
        assertThat(codec).contains("jsone:decode(Body)");
        assertThat(codec).contains("{error, _} -> #{}");
        assertThat(codec).doesNotContain("{error, _} -> #{}}");
        assertThat(codec).contains("#describe_item_input{");
        assertThat(codec).doesNotContain("#describe_item_input(){");
        assertThat(codec).contains("(V) when V =/= undefined");
        assertThat(codec).contains("uri_encode(");
    }

    @Test
    void explicitInvalidProtocolFailsWithCodegenException() {
        URL resource = ErlangClientPluginTest.class.getResource("/model/multi_service.smithy");
        assertThat(resource).isNotNull();
        Model model = Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
        MockManifest manifest = new MockManifest();
        ObjectNode settings = ObjectNode.builder()
                .withMember("service", "smithy.beam.demo.multi#ServiceA")
                .withMember("edition", "2026")
                .withMember("protocol", "smithy.api#String")
                .withMember("relativeDate", "2026-01-01")
                .withMember("relativeVersion", "1.0.0")
                .build();
        assertThatThrownBy(() -> new ErlangClientPlugin().execute(PluginContext.builder()
                        .model(model)
                        .fileManifest(manifest)
                        .settings(settings)
                        .build()))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("protocol")
                .hasMessageContaining("smithy.api#String");
    }
}
