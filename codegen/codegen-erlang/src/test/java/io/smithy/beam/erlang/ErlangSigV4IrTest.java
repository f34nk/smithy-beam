package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlModule;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangSigV4IrTest {
    private static final ShapeId SIGV4_SERVICE =
            ShapeId.from("smithy.beam.test.sigv4#Sigv4TestService");

    @Test
    void signMatchesGolden() throws IOException {
        assertThat(ErlangSigV4Ir.sign().asString())
                .isEqualTo(readExpectedString("ir/sigv4_sign.expected.erl"));
    }

    @Test
    void signRequestMatchesGolden() throws IOException {
        assertThat(ErlangSigV4Ir.signRequest().asString())
                .isEqualTo(readExpectedString("ir/sigv4_sign_request.expected.erl"));
    }

    @Test
    void helperFunctionsMatchGolden() throws IOException {
        String combined = ErlangSigV4Ir.helperFunctions().stream()
                .map(ErlFunction::asString)
                .collect(Collectors.joining("\n\n"));
        assertThat(combined).isEqualTo(readExpectedString("ir/sigv4_helpers.expected.erl"));
    }

    @Test
    void sigV4ModuleMatchesGolden() throws IOException {
        ServiceShape service = sigv4Model().expectShape(SIGV4_SERVICE, ServiceShape.class);
        ErlModule module = ErlangSigV4Ir.sigV4Module("sigv4test_service_sigv4", "runtime_types.hrl", service);
        assertThat(module.asString())
                .isEqualTo(readExpectedString("ir/sigv4_module.expected.erl"));
    }

    private static Model sigv4Model() {
        return Model.assembler()
                .addImport(ErlangSigV4IrTest.class.getResource("/model/sigv4_fixture.smithy"))
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlangSigV4IrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }
}
