package io.smithy.beam.gleam;

import io.beam.lang.gleam.CustomType;
import io.beam.lang.gleam.DocComment;
import io.beam.lang.gleam.Field;
import io.beam.lang.gleam.Function;
import io.beam.lang.gleam.Import;
import io.beam.lang.gleam.Module;
import io.beam.lang.gleam.Param;
import io.beam.lang.gleam.RawExpr;
import io.beam.lang.gleam.TypeAlias;
import io.beam.lang.gleam.Variant;
import io.smithy.beam.core.BeamGleamLayout;
import io.smithy.beam.core.BeamProtocolIds;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
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
      functions.add(handlerStub(op, model, sp, typesModuleName));
    }
    return Module.of(
        layout.implModuleName(),
        DocComment.of(
            "Handler implementation stubs for "
                + service.getId()
                + ".\n\nReplace Error(\"not_implemented\") bodies with real handlers."),
        List.of(Import.of(typesModuleName)),
        List.of(),
        List.of(),
        functions);
  }

  static Module serverModule(
      BeamGleamLayout layout,
      ServiceShape service,
      Model model,
      SymbolProvider sp,
      ShapeId protocolTraitId) {
    if (protocolTraitId != null && BeamProtocolIds.REST_JSON_1.equals(protocolTraitId)) {
      return restJsonServerModule(layout, service, model, sp);
    }
    return stubServerModule(layout, service);
  }

  private static Module stubServerModule(BeamGleamLayout layout, ServiceShape service) {
    return Module.of(
        layout.serverModuleName(),
        DocComment.of(
            "HTTP server adapter stub for "
                + service.getId()
                + ".\n\nWire an HTTP stack when a protocol is selected."),
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

  /**
   * Thin I/O edge: feed a gleam/http Request into the pure router. Generated code does not import
   * mist/wisp; callers bind this handle function to their HTTP stack.
   */
  private static Module restJsonServerModule(
      BeamGleamLayout layout, ServiceShape service, Model model, SymbolProvider sp) {
    String routerModule = layout.routerModuleName();
    String implModule = layout.implModuleName();
    List<Import> imports =
        List.of(
            Import.of(routerModule),
            Import.of(implModule),
            Import.type("gleam/http/request", "Request"),
            Import.type("gleam/http/response", "Response"));

    StringBuilder handlersBody = new StringBuilder();
    handlersBody.append(routerModule).append(".Handlers(\n");
    List<OperationShape> operations = GleamTopDown.containedOperationsSorted(model, service);
    for (int i = 0; i < operations.size(); i++) {
      String handler = handlerName(sp.toSymbol(operations.get(i)).getName());
      handlersBody
          .append("  ")
          .append(handler)
          .append(": ")
          .append(implModule)
          .append('.')
          .append(handler);
      if (i < operations.size() - 1) {
        handlersBody.append(',');
      }
      handlersBody.append('\n');
    }
    handlersBody.append(')');

    List<Function> functions = new ArrayList<>();
    functions.add(
        Function.pub(
            "default_handlers",
            List.of(),
            routerModule + ".Handlers",
            RawExpr.of(handlersBody.toString()),
            DocComment.of(
                "Build Handlers from the generated impl module stubs.\n\n"
                    + "Override individual fields when wiring custom handlers.")));
    functions.add(
        Function.pub(
            "handle",
            List.of(
                Param.of("request", "Request(BitArray)"),
                Param.of("handlers", routerModule + ".Handlers")),
            "Response(BitArray)",
            RawExpr.of(routerModule + ".route(request, handlers)"),
            DocComment.of(
                "Feed a request into the pure router and return the Response.\n\n"
                    + "Bind this from your HTTP server loop.")));
    functions.add(
        Function.pub(
            "handle_default",
            List.of(Param.of("request", "Request(BitArray)")),
            "Response(BitArray)",
            RawExpr.of("handle(request, default_handlers())"),
            DocComment.of("Convenience: handle using default_handlers/0.")));
    functions.add(
        Function.pub(
            "start",
            List.of(Param.of("_config", "ServerConfig")),
            "Result(Nil, String)",
            "Error(\"use handle/2 with your HTTP stack; no embedded listener\")",
            DocComment.of(
                "No embedded listener. Call handle/2 from your HTTP stack adapter.")));
    return Module.of(
        layout.serverModuleName(),
        DocComment.of(
            "Thin HTTP adapter for "
                + service.getId()
                + ".\n\nCalls the pure router; does not own a listen loop or import HTTP frameworks."),
        imports,
        List.of(),
        List.of(
            CustomType.pub(
                "ServerConfig",
                List.of(
                    Variant.of(
                        "ServerConfig", List.of(Field.labelled("port", "Int")))))),
        functions);
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

  private static Function handlerStub(
      OperationShape op, Model model, SymbolProvider sp, String typesModuleName) {
    Symbol opSym = sp.toSymbol(op);
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String inType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(input));
    String outType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(output));
    return Function.pub(
        handlerName(opSym.getName()),
        List.of(Param.of("_input", inType)),
        "Result(" + outType + ", String)",
        "Error(\"not_implemented\")",
        DocComment.of("Stub handler for " + op.getId() + ". Replace with a real implementation."));
  }

  static String handlerName(String operationSnakeName) {
    return "handle_" + operationSnakeName;
  }

  static String handlerTypeName(String operationSnakeName) {
    return "Handle" + GleamSymbolProvider.toPascalCase(operationSnakeName);
  }
}
