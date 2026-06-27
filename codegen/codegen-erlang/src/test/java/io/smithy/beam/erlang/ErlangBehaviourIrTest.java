package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.ir.erlang.ErlCallbackSpec;
import io.smithy.beam.ir.erlang.ErlModule;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ErlangBehaviourIrTest {

  @Test
  void behaviourModuleContainsCallbacks() {
    Model model = basicModel();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.basic#BasicService"), ServiceShape.class);
    BeamErlangLayout layout =
        new BeamErlangLayout(
            new io.smithy.beam.core.BeamSettings(), service.getId().getNamespace(), service);
    SymbolProvider sp =
        SymbolProvider.cache(
            new ErlangSymbolProvider(
                new io.smithy.beam.core.BeamSettings(),
                model,
                service,
                layout.behaviourModuleFile(),
                io.smithy.beam.core.BeamCodegenKind.SERVER));
    ErlangContext ctx =
        new ErlangContext(
            model,
            new io.smithy.beam.core.BeamSettings(),
            sp,
            new MockManifest(),
            new WriterDelegator<>(new MockManifest(), sp, ErlangWriter.factory()),
            List.of(),
            service,
            io.smithy.beam.core.BeamHttpBindings.from(model),
            null,
            null,
            layout.serverModuleName(),
            layout.serverModuleFile(),
            null,
            null,
            null);
    List<OperationShape> operations = ErlangTopDown.containedOperationsSorted(model, service);
    List<ErlCallbackSpec> callbacks =
        operations.stream().map(op -> ErlangBehaviourIr.operationCallback(ctx, op, sp)).toList();
    ErlModule module = ErlangBehaviourIr.behaviourModule(layout, service, callbacks);
    String source = module.asString();
    assertThat(source).contains("-module(basic_service_behaviour).");
    assertThat(source).contains("-include(\"basic_service_types.hrl\").");
    assertThat(source).contains("-callback handle_get_type_closure(");
    assertThat(source).contains("Input :: get_type_closure_input()");
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
