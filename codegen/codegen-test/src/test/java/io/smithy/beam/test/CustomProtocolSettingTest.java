package io.smithy.beam.test;

import io.smithy.beam.erlang.ErlangClientPlugin;
import io.smithy.beam.test.support.TestCustomProtocolIntegration;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.build.PluginContext;
import software.amazon.smithy.model.Model;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class CustomProtocolSettingTest {

    private static Model loadModel(String resourcePath) {
        URL resource = CustomProtocolSettingTest.class.getResource(resourcePath);
        assertThat(resource).isNotNull();
        return Model.assembler()
                .addImport(resource)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    @Test
    void explicitProtocolSettingEmitsCodecAndWireOperationStub() {
        Model model = loadModel("/model/dedicated_operation_io.smithy");
        MockManifest manifest = new MockManifest();

        new ErlangClientPlugin()
                .execute(
                        PluginContext.builder()
                                .model(model)
                                .fileManifest(manifest)
                                .pluginClassLoader(CustomProtocolSettingTest.class.getClassLoader())
                                .settings(
                                        software.amazon.smithy.model.node.ObjectNode.builder()
                                                .withMember(
                                                        "service",
                                                        "smithy.beam.demo.dedicated_io#DedicatedIoService")
                                                .withMember("edition", "2026")
                                                .withMember(
                                                        "protocol",
                                                        TestCustomProtocolIntegration.TEST_CUSTOM_PROTOCOL
                                                                .toString())
                                                .build())
                                .build());

        assertThat(manifest.getFileString("dedicated_io_service_test_custom_protocol.erl"))
                .isPresent();
        String client = manifest.expectFileString("dedicated_io_service_client.erl");
        assertThat(client).contains("dedicated_io_service_test_custom_protocol:encode_health_check_request");
        assertThat(client).doesNotContain("not_implemented");
    }
}
