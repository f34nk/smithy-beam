package io.smithy.beam.gleam;

import io.beam.lang.gleam.CustomType;
import io.beam.lang.gleam.DocComment;
import io.beam.lang.gleam.Field;
import io.beam.lang.gleam.Function;
import io.beam.lang.gleam.Import;
import io.beam.lang.gleam.Module;
import io.beam.lang.gleam.Param;
import io.beam.lang.gleam.TypeAlias;
import io.beam.lang.gleam.Variant;
import io.smithy.beam.core.BeamGleamLayout;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

final class GleamServerDsl {

  private GleamServerDsl() {}

  static Module behaviourModule(
      BeamGleamLayout layout,
      ServiceShape service,
      Model model,
      SymbolProvider sp,
      String typesModuleName) {
    List<TypeAlias> aliases = new ArrayList<>();
    for (OperationShape op : GleamTopDown.containedOperationsSorted(model, service)) {
      aliases.add(handlerTypeAlias(op, model, sp, typesModuleName));
    }
    return Module.of(
        layout.behaviourModuleName(),
        DocComment.of(
            "Handler contract for "
                + service.getId()
                + ".\n\nPlain function type aliases (not an OTP behaviour attribute)."),
        List.of(Import.of(typesModuleName)),
        aliases,
        List.of(),
        List.of());
  }

  static Module implModule(
      BeamGleamLayout layout,
      ServiceShape service,
      Model model,
      SymbolProvider sp,
      String typesModuleName) {
    List<Function> functions = new ArrayList<>();
    for (OperationShape op : GleamTopDown.containedOperationsSorted(model, service)) {
      functions.add(handlerTodo(op, model, sp, typesModuleName));
    }
    return Module.of(
        layout.implModuleName(),
        DocComment.of(
            "Handler implementation stubs for "
                + service.getId()
                + ".\n\nReplace todo bodies with real handlers."),
        List.of(Import.of(typesModuleName)),
        List.of(),
        List.of(),
        functions);
  }

  static Module serverModule(BeamGleamLayout layout, ServiceShape service) {
    return Module.of(
        layout.serverModuleName(),
        DocComment.of(
            "HTTP server adapter stub for "
                + service.getId()
                + ".\n\nWire an HTTP stack in a later pass."),
        List.of(),
        List.of(),
        List.of(
            CustomType.pub(
                "ServerConfig",
                List.of(
                    Variant.of(
                        "ServerConfig", List.of(Field.labelled("port", "Int")))))),
        List.of(
            Function.pub(
                "start",
                List.of(Param.of("_config", "ServerConfig")),
                "Result(Nil, String)",
                "Error(\"not_implemented\")")));
  }

  /** Stub router used when no restJson1 (or other) protocol is selected. */
  static Module stubRouterModule(BeamGleamLayout layout, ServiceShape service) {
    return Module.of(
        layout.routerModuleName(),
        DocComment.of(
            "Request router stub for "
                + service.getId()
                + ".\n\nReal method/path routing lands with protocol support."),
        List.of(),
        List.of(),
        List.of(),
        List.of(
            Function.pub(
                "route",
                List.of(
                    Param.of("_method", "String"),
                    Param.of("_path", "String"),
                    Param.of("_body", "BitArray")),
                "Result(BitArray, String)",
                "Error(\"not_implemented\")")));
  }

  private static TypeAlias handlerTypeAlias(
      OperationShape op, Model model, SymbolProvider sp, String typesModuleName) {
    Symbol opSym = sp.toSymbol(op);
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String inType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(input));
    String outType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(output));
    String definition = "fn(" + inType + ") -> Result(" + outType + ", String)";
    return TypeAlias.pub(handlerTypeName(opSym.getName()), definition);
  }

  private static Function handlerTodo(
      OperationShape op, Model model, SymbolProvider sp, String typesModuleName) {
    Symbol opSym = sp.toSymbol(op);
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String inType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(input));
    String outType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(output));
    return Function.pubTodo(
        handlerName(opSym.getName()),
        List.of(Param.of("_input", inType)),
        "Result(" + outType + ", String)");
  }

  static String handlerName(String operationSnakeName) {
    return "handle_" + operationSnakeName;
  }

  static String handlerTypeName(String operationSnakeName) {
    return "Handle" + GleamSymbolProvider.toPascalCase(operationSnakeName);
  }
}
