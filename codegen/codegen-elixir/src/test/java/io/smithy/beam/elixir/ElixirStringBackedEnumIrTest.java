package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExNestedModule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirStringBackedEnumIrTest {
  private static EnumShape largeStatusShape;
  private static ElixirSymbolProvider provider;
  private static BeamSettings settings;

  @BeforeAll
  static void setup() {
    String idl =
        """
                $version: "2"
                namespace com.large

                service LargeService {
                    operations: [LargeOp]
                }

                operation LargeOp {
                    input: LargeInput
                    output: LargeOutput
                }

                structure LargeInput {
                    status: LargeStatus
                }

                structure LargeOutput {}

                enum LargeStatus {
                    ALPHA
                    BETA
                    GAMMA
                }
                """;
    Model model = Model.assembler().addUnparsedModel("large.smithy", idl).assemble().unwrap();
    ServiceShape service =
        model.expectShape(ShapeId.from("com.large#LargeService"), ServiceShape.class);
    settings = new BeamSettings();
    settings.edition("2026");
    settings.elixirEnumStringThreshold(0);
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    provider =
        new ElixirSymbolProvider(
            settings,
            model,
            service,
            layout.typesModuleFile(),
            ElixirSymbolProvider.toModuleName(layout.typesModuleName()),
            BeamCodegenKind.TYPES);
    largeStatusShape = model.expectShape(ShapeId.from("com.large#LargeStatus"), EnumShape.class);
  }

  @Test
  void stringBackedEnumAsStringMatchesGolden() throws IOException {
    Symbol symbol = provider.toSymbol(largeStatusShape);
    assertThat(ElixirSymbolProvider.isStringBackedEnum(symbol)).isTrue();
    ExNestedModule nested =
        ElixirDirectedCodegen.buildEnumNestedModule(largeStatusShape, symbol, settings);
    assertThat(nested.asString())
        .isEqualTo(readExpectedString("ir/string_backed_enum_large.expected.ex"));
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirStringBackedEnumIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IOException("Missing test resource: " + resourcePath);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
