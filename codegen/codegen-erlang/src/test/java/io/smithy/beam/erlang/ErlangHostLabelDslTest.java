package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.erlang.ErlangRenderer;
import io.beam.dsl.erlang.Function;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ErlangHostLabelIrTest {
  @Test
  void hostLabelHelpersAsStringMatchesGolden() throws IOException {
    Model model = hostLabelModel();
    ServiceShape service =
        model.expectShape(
            ShapeId.from("smithy.beam.test.hostlabel#HostLabelService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    ErlangSymbolProvider sp =
        new ErlangSymbolProvider(
            settings, model, service, "host_label_types.hrl", BeamCodegenKind.CLIENT);
    List<Function> functions = ErlangHostLabelDsl.buildHostFunctions(model, service, sp);
    assertThat(functions).hasSize(1);
    String combined = ErlangRenderer.renderFunction(functions.get(0));
    assertThat(combined)
        .isEqualTo(DslGoldenAssertions.readExpectedString("dsl/host_label_helpers.expected.erl"));
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
}
