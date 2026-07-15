package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.Callback;
import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Module;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

@Disabled("beam-dsl migration: golden fixtures live in beam-dsl; re-enable locally if needed")
class ElixirBehaviourIrTest {

  @Test
  void behaviourModuleContainsCallbacks() {
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
                layout.behaviourModuleFile(),
                ElixirSymbolProvider.toModuleName(layout.behaviourModuleName()),
                BeamCodegenKind.SERVER));
    ElixirContext ctx =
        new ElixirContext(
            model,
            settings,
            sp,
            new MockManifest(),
            new WriterDelegator<>(new MockManifest(), sp, ElixirWriter.factory("server")),
            List.of(),
            service,
            BeamHttpBindings.from(model),
            null,
            null,
            ElixirSymbolProvider.toModuleName(layout.serverModuleName()),
            layout.serverModuleFile());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    List<Callback> callbacks =
        operations.stream().map(op -> ElixirBehaviourDsl.operationCallback(ctx, op, sp)).toList();
    Module module = ElixirBehaviourDsl.behaviourModule(layout, service, callbacks, operations, sp);
    String source = ElixirRenderer.render(module);
    assertThat(source).contains("defmodule BasicServiceBehaviour do");
    assertThat(source).contains("alias BasicServiceTypes, as: Types");
    assertThat(source).contains("@callback handle_get_type_closure(");
    assertThat(source).contains("BasicServiceTypes.GetTypeClosureInput.t()");
    assertThat(source).contains("def callbacks do");
    assertThat(source).contains("{:handle_get_type_closure, 3}");
  }

  private static Model basicModel() {
    return Model.assembler()
        .addUnparsedModel(
            "basic.smithy",
            """
                        $version: "2"
                        namespace smithy.beam.demo.basic

                        use aws.protocols#restJson1

                        @restJson1
                        service BasicService {
                            version: "2026"
                            operations: [GetTypeClosure]
                        }

                        @readonly
                        @http(method: "GET", uri: "/type-closure", code: 200)
                        operation GetTypeClosure {
                            input: GetTypeClosureInput
                            output: GetTypeClosureOutput
                        }

                        structure GetTypeClosureInput {}
                        structure GetTypeClosureOutput {}
                        """)
        .discoverModels()
        .assemble()
        .unwrap();
  }
}
