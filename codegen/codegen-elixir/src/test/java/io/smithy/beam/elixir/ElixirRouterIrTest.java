package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExModule;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-ir migration: golden fixtures live in beam-ir; re-enable locally if needed")
class ElixirRouterIrTest {

  @Test
  void routerModuleMatchesRestJsonExpectations() {
    Model model = basicModel();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.basic#BasicService"), ServiceShape.class);
    BeamSettings settings = new BeamSettings();
    BeamElixirLayout layout =
        new BeamElixirLayout(settings, service.getId().getNamespace(), service);
    SymbolProvider sp =
        SymbolProvider.cache(
            new ElixirSymbolProvider(
                settings,
                model,
                service,
                layout.serverModuleFile(),
                ElixirSymbolProvider.toModuleName(layout.serverModuleName()),
                BeamCodegenKind.SERVER));
    List<software.amazon.smithy.model.shapes.OperationShape> operations =
        ElixirTopDown.containedOperationsSorted(model, service);
    ExModule module =
        ElixirRouterIr.routerModule(
            model, service, layout, ShapeId.from("aws.protocols#restJson1"), operations, sp);
    String router = module.asString();
    assertThat(router).contains("defmodule BasicServiceRouter do");
    assertThat(router).contains("def dispatch(");
    assertThat(router).contains("route(request.method, request.path, handler, request)");
    assertThat(router).contains("defp route(");
    assertThat(router).contains("\"/basic-items\"");
    assertThat(router).contains("\"/types/\" <> name_seg = path");
    assertThat(router).contains("parse_labels(path, \"/types/{name}\")");
    assertThat(router).contains("{:error, {:not_found, method, path}}");
  }

  private static Model basicModel() {
    return Model.assembler()
        .addUnparsedModel(
            "basic.smithy",
            """
                        $version: "2"
                        namespace smithy.beam.demo.basic

                        use aws.protocols#restJson1
                        use smithy.api#String

                        string BasicString

                        @restJson1
                        service BasicService {
                            version: "2026"
                            operations: [GetTypeClosure, ListBasicItems]
                        }

                        @readonly
                        @http(method: "GET", uri: "/types/{name}", code: 200)
                        operation GetTypeClosure {
                            input: GetTypeClosureInput
                            output: GetTypeClosureOutput
                        }

                        @readonly
                        @http(method: "GET", uri: "/basic-items", code: 200)
                        operation ListBasicItems {
                            input: ListBasicItemsInput
                            output: ListBasicItemsOutput
                        }

                        structure GetTypeClosureInput {
                            @required
                            @httpLabel
                            name: BasicString
                        }
                        structure GetTypeClosureOutput {}
                        structure ListBasicItemsInput {}
                        structure ListBasicItemsOutput {}
                        """)
        .discoverModels()
        .assemble()
        .unwrap();
  }
}
