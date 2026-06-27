package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlFunction;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErlangHttpChecksumIrTest {
    @Test
    void checksumHelperFunctionsMatchGolden() throws IOException {
        List<ErlFunction> functions = ErlangHttpChecksumIr.checksumHelperFunctions();
        assertThat(helpersAsString(functions)).isEqualTo(readExpectedString("ir/http_checksum_helpers.expected.erl"));
        for (ErlFunction fn : functions) {
            assertThat(fn.name()).isNotBlank();
            assertThat(fn.clauses()).isNotEmpty();
        }
    }

    @Test
    void requestChecksumHeadersExprMatchesGolden() throws IOException {
        Model model = checksumFixtureModel();
        ServiceShape service = model.expectShape(
                ShapeId.from("smithy.beam.test.checksum#HttpChecksumRestJsonService"), ServiceShape.class);
        OperationShape required = model.expectShape(
                ShapeId.from("smithy.beam.test.checksum#PutRequiredChecksum"), OperationShape.class);
        OperationShape flexible = model.expectShape(
                ShapeId.from("smithy.beam.test.checksum#PutFlexibleChecksum"), OperationShape.class);
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        ErlangSymbolProvider sp = new ErlangSymbolProvider(
                settings, model, service, "http_checksum_types.hrl", BeamCodegenKind.CLIENT);

        ErlExpr requiredExpr = ErlangHttpChecksumIr.requestChecksumHeadersExpr(
                model, required, sp, "Headers").orElseThrow();
        ErlExpr flexibleExpr = ErlangHttpChecksumIr.requestChecksumHeadersExpr(
                model, flexible, sp, "Headers").orElseThrow();
        String combined = exprAsString(requiredExpr) + "\n\n" + exprAsString(flexibleExpr);
        assertThat(combined).isEqualTo(readExpectedString("ir/http_checksum_request_headers.expected.erl"));
    }

    @Test
    void responseChecksumGuardExprMatchesGolden() throws IOException {
        Model model = checksumFixtureModel();
        ServiceShape service = model.expectShape(
                ShapeId.from("smithy.beam.test.checksum#HttpChecksumRestJsonService"), ServiceShape.class);
        OperationShape flexible = model.expectShape(
                ShapeId.from("smithy.beam.test.checksum#PutFlexibleChecksum"), OperationShape.class);
        BeamSettings settings = new BeamSettings();
        settings.edition("2026");
        ErlangSymbolProvider sp = new ErlangSymbolProvider(
                settings, model, service, "http_checksum_types.hrl", BeamCodegenKind.CLIENT);

        ErlExpr guarded = ErlangHttpChecksumIr.responseChecksumGuardExpr(
                model,
                flexible,
                io.smithy.beam.ir.erlang.ErlTuple.tuple(
                        io.smithy.beam.ir.erlang.ErlAtom.atom("ok"),
                        io.smithy.beam.ir.erlang.ErlVar.var("Output")));
        assertThat(exprAsString(guarded)).isEqualTo(readExpectedString("ir/http_checksum_response_guard.expected.erl"));
    }

    static Model checksumFixtureModel() {
        String idl = """
                $version: "2"
                namespace smithy.beam.test.checksum

                use aws.protocols#restJson1
                use aws.protocols#httpChecksum
                use smithy.api#http
                use smithy.api#httpPayload
                use smithy.api#httpHeader

                @restJson1
                service HttpChecksumRestJsonService {
                    version: "2026"
                    operations: [PutRequiredChecksum, PutFlexibleChecksum]
                }

                @httpChecksum(requestChecksumRequired: true)
                @http(method: "PUT", uri: "/required")
                operation PutRequiredChecksum {
                    input: PutRequiredChecksumInput
                    output: Unit
                }

                structure PutRequiredChecksumInput {
                    @httpPayload
                    content: Blob
                }

                @httpChecksum(
                    requestAlgorithmMember: "checksumAlgorithm",
                    requestValidationModeMember: "validationMode",
                    responseAlgorithms: ["CRC32C", "SHA256"]
                )
                @http(method: "PUT", uri: "/flexible")
                operation PutFlexibleChecksum {
                    input: PutFlexibleChecksumInput
                    output: PutFlexibleChecksumOutput
                }

                structure PutFlexibleChecksumInput {
                    @httpHeader("x-amz-sdk-checksum-algorithm")
                    checksumAlgorithm: ChecksumAlgorithm

                    @httpHeader("x-amz-request-validation-mode")
                    validationMode: ValidationMode

                    @httpPayload
                    content: Blob
                }

                enum ChecksumAlgorithm {
                    CRC32C
                    SHA256
                }

                enum ValidationMode {
                    ENABLED
                }

                structure PutFlexibleChecksumOutput {
                    @httpPayload
                    content: Blob
                }

                structure Unit {}
                """;
        return Model.assembler()
                .addUnparsedModel("http_checksum.smithy", idl)
                .discoverModels()
                .assemble()
                .unwrap();
    }

    private static String exprAsString(ErlExpr expr) {
        return String.join("\n", expr.lines());
    }

    private static String helpersAsString(List<ErlFunction> functions) {
        return functions.stream().map(ErlFunction::asString).collect(Collectors.joining("\n\n"));
    }

    private static String readExpectedString(String resourcePath) throws IOException {
        try (InputStream in = ErlangHttpChecksumIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertThat(in).as("resource %s", resourcePath).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text;
        }
    }
}
