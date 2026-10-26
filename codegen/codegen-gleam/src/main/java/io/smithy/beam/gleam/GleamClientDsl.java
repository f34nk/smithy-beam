package io.smithy.beam.gleam;

import io.beam.lang.gleam.CustomType;
import io.beam.lang.gleam.DocComment;
import io.beam.lang.gleam.Function;
import io.beam.lang.gleam.Import;
import io.beam.lang.gleam.Module;
import io.beam.lang.gleam.Param;
import io.beam.lang.gleam.Variant;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamGleamLayout;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

final class GleamClientDsl {

  private GleamClientDsl() {}

  static Module clientModule(
      BeamGleamLayout layout,
      ServiceShape service,
      Model model,
      SymbolProvider sp,
      String typesModuleName) {
    List<Function> functions = new ArrayList<>();
    for (OperationShape op : GleamTopDown.containedOperationsSorted(model, service)) {
      functions.add(stubOperation(op, model, sp, typesModuleName));
    }

    DocComment moduleDoc =
        BeamDocumentation.forShape(service)
            .map(DocComment::of)
            .orElseGet(
                () ->
                    DocComment.of(
                        "Client stubs for "
                            + layout.clientModuleName()
                            + ".\n\nOperation stubs accept config and input and return"
                            + " Error(NotImplemented) until a protocol codec is wired."));

    return Module.of(
        layout.clientModuleName(),
        moduleDoc,
        List.of(Import.of(typesModuleName)),
        List.of(),
        List.of(clientConfigType(), clientErrorType()),
        functions);
  }

  private static CustomType clientConfigType() {
    return CustomType.pub(
        "ClientConfig",
        List.of(
            Variant.of(
                "ClientConfig",
                List.of(io.beam.lang.gleam.Field.labelled("base_url", "String")))),
        DocComment.of("Minimal client configuration for stub operations."));
  }

  private static CustomType clientErrorType() {
    return CustomType.pub(
        "ClientError",
        List.of(Variant.of("NotImplemented")),
        DocComment.of(
            "Client error channel. Expanded later with transport, decode, and modeled errors."));
  }

  private static Function stubOperation(
      OperationShape op, Model model, SymbolProvider sp, String typesModuleName) {
    Symbol opSym = sp.toSymbol(op);
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String inputType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(input));
    String outputType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(output));
    String returnType = "Result(" + outputType + ", ClientError)";
    DocComment doc = BeamDocumentation.forShape(op).map(DocComment::of).orElse(null);
    List<Param> params =
        List.of(Param.of("_config", "ClientConfig"), Param.of("_input", inputType));
    if (doc == null) {
      return Function.pub(opSym.getName(), params, returnType, "Error(NotImplemented)");
    }
    return Function.pub(opSym.getName(), params, returnType, "Error(NotImplemented)", doc);
  }
}
