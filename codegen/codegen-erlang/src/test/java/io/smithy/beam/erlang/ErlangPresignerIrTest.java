package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlModule;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangPresignerIrTest {
    private static final ShapeId SIGV4_SERVICE =
            ShapeId.from("smithy.beam.test.sigv4#Sigv4TestService");

    @Test
    void presignUrlMatchesGolden() throws IOException {
        assertThat(ErlangPresignerIr.presignUrl("sigv4test_service_sigv4").asString())
                .isEqualTo(readExpectedString("ir/presigner_presign_url.expected.erl"));
    }

    @Test
    void presignerModuleMatchesGolden() throws IOException {
        ServiceShape service = sigv4Model().expectShape(SIGV4_SERVICE, ServiceShape.class);
        ErlModule module = ErlangPresignerIr.presignerModule(
                "sigv4test_service_presigner",
                "runtime_types.hrl",
                "sigv4test_service_sigv4",
                service);
        assertThat(module.asString()).isEqualTo(readExpectedString("ir/presigner_module.expected.erl"));
    }

    private static Model sigv4Model() {
        return Model.assembler()
                .addImport(ErlangPresignerIrTest.class.getResource("/model/sigv4_fixture.smithy"))
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlangPresignerIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }
}
