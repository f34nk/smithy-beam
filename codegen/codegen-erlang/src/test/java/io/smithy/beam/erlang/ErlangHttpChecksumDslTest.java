package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.erlang.AtomExpr;
import io.beam.dsl.erlang.ErlangRenderer;
import io.beam.dsl.erlang.Expression;
import io.beam.dsl.erlang.TupleExpr;
import io.beam.dsl.erlang.Variable;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ErlangHttpChecksumIrTest {
  @Test
  void requestChecksumHeadersExprMatchesGolden() throws IOException {
    Model model = checksumFixtureModel();
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.checksum#HttpChecksumRestJsonService"),
            ServiceShape.class);
    OperationShape required =
        model.expectShape(
            ShapeId.from("smithy.beam.test.checksum#PutRequiredChecksum"), OperationShape.class);
    OperationShape flexible =
        model.expectShape(
            ShapeId.from("smithy.beam.test.checksum#PutFlexibleChecksum"), OperationShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    ErlangSymbolProvider sp =
        new ErlangSymbolProvider(
            settings, model, service, "http_checksum_types.hrl", BeamCodegenKind.CLIENT);

    Expression requiredExpr =
        ErlangHttpChecksumDsl.requestChecksumHeadersExpr(model, required, sp, "Headers")
            .orElseThrow();
    Expression flexibleExpr =
        ErlangHttpChecksumDsl.requestChecksumHeadersExpr(model, flexible, sp, "Headers")
            .orElseThrow();
    String combined =
        ErlangRenderer.renderExpression(requiredExpr)
            + "\n\n"
            + ErlangRenderer.renderExpression(flexibleExpr);
    assertThat(combined)
        .isEqualTo(
            DslGoldenAssertions.readExpectedString(
                "dsl/http_checksum_request_headers.expected.erl"));
  }

  @Test
  void responseChecksumGuardExprMatchesGolden() throws IOException {
    Model model = checksumFixtureModel();
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.checksum#HttpChecksumRestJsonService"),
            ServiceShape.class);
    OperationShape flexible =
        model.expectShape(
            ShapeId.from("smithy.beam.test.checksum#PutFlexibleChecksum"), OperationShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    ErlangSymbolProvider sp =
        new ErlangSymbolProvider(
            settings, model, service, "http_checksum_types.hrl", BeamCodegenKind.CLIENT);

    Expression guarded =
        ErlangHttpChecksumDsl.responseChecksumGuardExpr(
            model, flexible, TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Output"))));
    assertThat(ErlangRenderer.renderExpression(guarded))
        .isEqualTo(
            DslGoldenAssertions.readExpectedString("dsl/http_checksum_response_guard.expected.erl"));
  }

  static Model checksumFixtureModel() {
    String idl =
        """
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
}
