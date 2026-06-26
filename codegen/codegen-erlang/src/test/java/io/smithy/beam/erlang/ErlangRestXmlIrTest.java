package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    @Test
    void capturedCodecBodiesDoNotDuplicateClauseTerminators() {
        Model model = sampleModel();
        ServiceShape service = model.expectShape(
                ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
        OperationShape op = model.expectShape(
                ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
        ErlangSymbolProvider sp = sampleSymbolProvider(model, service);
        HttpBindingIndex httpIndex = HttpBindingIndex.of(model);

        String encodeRequest = ErlangRestXmlIr.encodeRequest(model, service, op, httpIndex, sp, false).asString();
        String decodeRequest = ErlangRestXmlOperationIr.buildDecodeRequest(model, op, httpIndex, sp).asString();
        String decodeResponse = ErlangRestXmlOperationIr.buildDecodeResponse(model, op, httpIndex, sp).stream()
                .map(ErlFunction::asString)
                .collect(java.util.stream.Collectors.joining("\n\n"));
        String encodeResponse = ErlangRestXmlOperationIr.buildEncodeResponse(model, op, httpIndex, sp).asString();

        for (String generated : List.of(encodeRequest, decodeRequest, decodeResponse, encodeResponse)) {
            assertThat(generated).doesNotContain("}..");
            assertThat(generated).doesNotContain("}};;");
            assertThat(generated).doesNotContain("};.");
        }
    }

    private static Model sampleModel() {
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
        return Model.assembler()
                .addUnparsedModel("http.smithy", idl)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static ErlFunction sampleEncodeRequest() {
        Model model = sampleModel();
        ServiceShape service = model.expectShape(
                ShapeId.from("smithy.beam.demo.http#HttpService"), ServiceShape.class);
        OperationShape op = model.expectShape(
                ShapeId.from("smithy.beam.demo.http#GetName"), OperationShape.class);
        return ErlangRestXmlIr.encodeRequest(
                model,
                service,
                op,
                HttpBindingIndex.of(model),
                sampleSymbolProvider(model, service),
                false);
    }

    private static ErlangSymbolProvider sampleSymbolProvider(Model model, ServiceShape service) {
        return new ErlangSymbolProvider(
                new io.smithy.beam.core.BeamSettings(),
                model,
                service,
                "http_types.hrl",
                io.smithy.beam.core.BeamCodegenKind.CLIENT);
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
