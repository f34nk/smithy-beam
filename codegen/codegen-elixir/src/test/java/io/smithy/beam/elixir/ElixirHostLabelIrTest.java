package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.ir.elixir.ElixirRenderer;
import io.beam.ir.elixir.Function;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ElixirHostLabelIrTest {
  @Test
  void hostLabelHelpersAsStringMatchesGolden() throws IOException {
    Model model = hostLabelModel();
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.hostlabel#HostLabelService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    ElixirSymbolProvider sp =
        new ElixirSymbolProvider(
            settings,
            model,
            service,
            layout.clientModuleFile(),
            ElixirSymbolProvider.toModuleName(layout.clientModuleName()),
            BeamCodegenKind.CLIENT);
    List<Function> functions = ElixirHostLabelIr.buildHostFunctions(model, service, sp);
    assertThat(functions).hasSize(1);
    ElixirIrTestSupport.assertStructural(functions.get(0));
    assertThat(ElixirRenderer.renderFunction(functions.get(0)))
        .isEqualTo(readExpectedString("ir/host_label_helpers.expected.ex"));
  }

  private static Model hostLabelModel() {
    String idl =
        """
                $version: "2"
                namespace smithy.beam.test.hostlabel

                use aws.protocols#restJson1

                @restJson1
                service HostLabelService {
                    version: "2026"
                    operations: [GetTenantData]
                }

                @endpoint(hostPrefix: "{tenant}.")
                @http(method: "GET", uri: "/data/{tenant}")
                operation GetTenantData {
                    input: GetTenantDataInput
                    output: GetTenantDataOutput
                }

                structure GetTenantDataInput {
                    @required
                    @hostLabel
                    @httpLabel
                    tenant: String
                }

                structure GetTenantDataOutput {
                    value: String
                }
                """;
    return Model.assembler()
        .addUnparsedModel("test.smithy", idl)
        .discoverModels()
        .assemble()
        .unwrap();
  }

  private static String readExpectedString(String resourcePath) throws IOException {
    try (InputStream in =
        ElixirHostLabelIrTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
      assertThat(in).as("resource %s", resourcePath).isNotNull();
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      if (text.endsWith("\n")) {
        text = text.substring(0, text.length() - 1);
      }
      return text;
    }
  }
}
