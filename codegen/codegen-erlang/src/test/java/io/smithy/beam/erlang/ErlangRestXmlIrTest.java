package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangRestXmlIrTest {
    @Test
    void restXmlHelpersAsStringMatchGolden() throws IOException {
        String decode = ErlangRestXmlIr.xmlDecodeHelpers().stream()
                .map(ErlFunction::asString)
                .collect(java.util.stream.Collectors.joining("\n\n"));
        String encode = ErlangRestXmlIr.xmlEncodeHelpers().stream()
                .map(ErlFunction::asString)
                .collect(java.util.stream.Collectors.joining("\n\n"));
        String combined = decode + "\n\n" + encode;
        for (ErlFunction fn : ErlangRestXmlIr.xmlDecodeHelpers()) {
            assertStructural(fn);
        }
        for (ErlFunction fn : ErlangRestXmlIr.xmlEncodeHelpers()) {
            assertStructural(fn);
        }
        assertThat(combined).isEqualTo(readExpectedString("ir/rest_xml_helpers.expected.erl"));
    }

    @Test
    void encodeRequestIsStructural() {
        assertStructural(sampleEncodeRequest());
    }

    private static ErlFunction sampleEncodeRequest() {
        String idl = """
                $version: "2"
                namespace smithy.beam.demo.http

                use aws.protocols#restXml

                string Name

                @restXml
                service HttpService {
                    version: "2026"
                    operations: [GetName]
                }

                @readonly
                @http(method: "GET", uri: "/names/{name}", code: 200)
                operation GetName {
                    input: GetNameInput
                    output: GetNameOutput
                }

                structure GetNameInput {
                    @required
                    @httpLabel
                    name: Name
                }

                structure GetNameOutput {
                    name: Name
                }
                """;
        var model = software.amazon.smithy.model.Model.assembler()
                .addUnparsedModel("http.smithy", idl)
                .discoverModels()
                .assemble()
                .unwrap();
        var service = model.expectShape(
                software.amazon.smithy.model.shapes.ShapeId.from("smithy.beam.demo.http#HttpService"),
                software.amazon.smithy.model.shapes.ServiceShape.class);
        var op = model.expectShape(
                software.amazon.smithy.model.shapes.ShapeId.from("smithy.beam.demo.http#GetName"),
                software.amazon.smithy.model.shapes.OperationShape.class);
        var sp = new ErlangSymbolProvider(
                new io.smithy.beam.core.BeamSettings(),
                model,
                service,
                "http_types.hrl",
                io.smithy.beam.core.BeamCodegenKind.CLIENT);
        return ErlangRestXmlIr.encodeRequest(
                model,
                service,
                op,
                software.amazon.smithy.model.knowledge.HttpBindingIndex.of(model),
                sp,
                false);
    }

    private static void assertStructural(ErlFunction fn) {
        assertThat(fn.name()).isNotBlank();
        assertThat(fn.clauses()).isNotEmpty();
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlangRestXmlIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }
}
