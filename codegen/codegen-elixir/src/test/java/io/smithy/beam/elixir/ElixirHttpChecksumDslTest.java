package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.Variable;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ElixirHttpChecksumIrTest {
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
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    ElixirSymbolProvider sp =
        new ElixirSymbolProvider(
            settings,
            model,
            service,
            layout.typesModuleFile(),
            ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
            BeamCodegenKind.CLIENT);

    Expression requiredExpr =
        ElixirHttpChecksumDsl.requestChecksumHeadersExpr(model, required, sp, "headers")
            .orElseThrow();
    Expression flexibleExpr =
        ElixirHttpChecksumDsl.requestChecksumHeadersExpr(model, flexible, sp, "headers")
            .orElseThrow();
    String combined =
        ElixirRenderer.renderStatement(requiredExpr)
            + "\n\n"
            + ElixirRenderer.renderStatement(flexibleExpr);
    assertThat(combined)
        .isEqualTo(readExpectedString("dsl/http_checksum_request_headers.expected.ex"));
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
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    ElixirSymbolProvider sp =
        new ElixirSymbolProvider(
            settings,
            model,
            service,
            layout.typesModuleFile(),
            ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
            BeamCodegenKind.CLIENT);

    Expression guarded =
        ElixirHttpChecksumDsl.responseChecksumGuardExpr(
            model, flexible, TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("output"))));
    assertThat(ElixirRenderer.renderExpression(guarded))
        .isEqualTo(readExpectedString("dsl/http_checksum_response_guard.expected.ex"));
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

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirHttpChecksumIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
