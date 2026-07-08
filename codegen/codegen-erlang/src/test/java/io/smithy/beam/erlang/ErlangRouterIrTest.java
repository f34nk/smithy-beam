package io.smithy.beam.erlang;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamErlangLayout;
import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Module;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ErlangRouterIrTest {

  @Test
  void routerModuleMatchesRestJsonExpectations() {
    Model model = basicModel();
    ServiceShape service =
        model.expectShape(ShapeId.from("smithy.beam.demo.basic#BasicService"), ServiceShape.class);
    BeamErlangLayout layout =
        new BeamErlangLayout(
            new io.smithy.beam.core.BeamSettings(), service.getId().getNamespace(), service);
    software.amazon.smithy.codegen.core.SymbolProvider sp =
        software.amazon.smithy.codegen.core.SymbolProvider.cache(
            new ErlangSymbolProvider(
                new io.smithy.beam.core.BeamSettings(),
                model,
                service,
                layout.serverModuleFile(),
                io.smithy.beam.core.BeamCodegenKind.SERVER));
    List<software.amazon.smithy.model.shapes.OperationShape> operations =
        ErlangTopDown.containedOperationsSorted(model, service);
    Module module =
        ErlangRouterIr.routerModule(
            model, service, layout, ShapeId.from("aws.protocols#restJson1"), operations, sp);
    String router = ErlangRenderer.render(module);
    assertThat(router).contains("-module(basic_service_router).");
    assertThat(router).contains("-export([\n    dispatch/2\n]).");
    assertThat(router).contains("#http_request{method = Method, path = Path}");
    assertThat(router).contains("route(Method, Path, Handler, Req)");
    assertThat(router).contains("<<\"/basic-items\">>");
    assertThat(router).contains("<<\"/types/\", NameSeg/binary>>");
    assertThat(router).contains("parse_labels(Path, <<\"/types/{name}\">>)");
    assertThat(router).doesNotContain("utils:parse_labels");
    assertThat(router).contains("-spec parse_labels(binary(), binary())");
    assertThat(router).contains("{error, {not_found, Method, Path}}");
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
