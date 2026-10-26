package io.smithy.beam.gleam;

import io.beam.lang.gleam.CustomType;
import io.beam.lang.gleam.DocComment;
import io.beam.lang.gleam.Field;
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
      functions.add(stubRequest(op, model, sp, typesModuleName));
      functions.add(stubResponse(op, model, sp, typesModuleName));
    }

    DocComment moduleDoc =
        BeamDocumentation.forShape(service)
            .map(DocComment::of)
            .orElseGet(
                () ->
                    DocComment.of(
                        "Sans-io client for "
                            + layout.clientModuleName()
                            + ".\n\nEach operation exposes a request builder and a response"
                            + " parser on gleam/http types. Send is left to the caller until a"
                            + " protocol codec is wired."));

    return Module.of(
        layout.clientModuleName(),
        moduleDoc,
        List.of(
            Import.of(typesModuleName),
            Import.type("gleam/http/request", "Request"),
            Import.type("gleam/http/response", "Response")),
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
                List.of(Field.labelled("base_url", "String")))),
        DocComment.of("Minimal client configuration for sans-io request builders."));
  }

  private static CustomType clientErrorType() {
    return CustomType.pub(
        "ClientError",
        List.of(
            Variant.of("NotImplemented"),
            Variant.of(
                "UnexpectedStatus",
                List.of(
                    Field.labelled("status", "Int"),
                    Field.labelled("body", "BitArray"))),
            Variant.of(
                "DecodeError",
                List.of(Field.labelled("message", "String"))),
            Variant.of(
                "ModeledError",
                List.of(
                    Field.labelled("name", "String"),
                    Field.labelled("message", "String")))),
        DocComment.of(
            "Domain client errors from response parsing (unexpected status, decode failure,"
                + " modeled service errors). Transport failures stay with the caller HTTP"
                + " client."));
  }

  private static Function stubRequest(
      OperationShape op, Model model, SymbolProvider sp, String typesModuleName) {
    Symbol opSym = sp.toSymbol(op);
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(input));
    String name = opSym.getName() + "_request";
    DocComment doc =
        BeamDocumentation.forShape(op)
            .map(text -> DocComment.of(text + "\n\nBuilds a gleam/http Request (stub)."))
            .orElse(DocComment.of("Builds a gleam/http Request for " + opSym.getName() + " (stub)."));
    List<Param> params =
        List.of(Param.of("_config", "ClientConfig"), Param.of("_input", inputType));
    return Function.pub(name, params, "Request(BitArray)", "todo", doc);
  }

  private static Function stubResponse(
      OperationShape op, Model model, SymbolProvider sp, String typesModuleName) {
    Symbol opSym = sp.toSymbol(op);
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(output));
    String name = opSym.getName() + "_response";
    String returnType = "Result(" + outputType + ", ClientError)";
    DocComment doc =
        BeamDocumentation.forShape(op)
            .map(text -> DocComment.of(text + "\n\nParses a gleam/http Response (stub)."))
            .orElse(
                DocComment.of(
                    "Parses a gleam/http Response for " + opSym.getName() + " (stub)."));
    List<Param> params = List.of(Param.of("_response", "Response(BitArray)"));
    return Function.pub(name, params, returnType, "Error(NotImplemented)", doc);
  }
}
