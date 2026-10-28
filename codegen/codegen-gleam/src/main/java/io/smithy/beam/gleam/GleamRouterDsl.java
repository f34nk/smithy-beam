package io.smithy.beam.gleam;

import io.beam.lang.gleam.CustomType;
import io.beam.lang.gleam.DocComment;
import io.beam.lang.gleam.Field;
import io.beam.lang.gleam.Function;
import io.beam.lang.gleam.Import;
import io.beam.lang.gleam.Module;
import io.beam.lang.gleam.Param;
import io.beam.lang.gleam.RawExpr;
import io.beam.lang.gleam.Variant;
import io.smithy.beam.core.BeamGleamLayout;
import io.smithy.beam.core.BeamMemberNames;
import io.smithy.beam.core.BeamMemberNullability;
import io.smithy.beam.core.BeamProtocolIds;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.knowledge.NullableIndex;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.NumberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpTrait;

/** Pure restJson1 router: Request in, Response out. */
final class GleamRouterDsl {

  private GleamRouterDsl() {}

  static Module routerModule(
      BeamGleamLayout layout,
      ServiceShape service,
      Model model,
      SymbolProvider sp,
      ShapeId protocolTraitId) {
    if (protocolTraitId == null || !BeamProtocolIds.REST_JSON_1.equals(protocolTraitId)) {
      return GleamServerDsl.stubRouterModule(layout, service);
    }
    return restJsonRouter(layout, service, model, sp, protocolTraitId);
  }

  private static Module restJsonRouter(
      BeamGleamLayout layout,
      ServiceShape service,
      Model model,
      SymbolProvider sp,
      ShapeId protocolTraitId) {
    String typesModule = layout.typesModuleName();
    String behaviourModule = layout.behaviourModuleName();
    String codecModule = layout.codecModuleName(protocolTraitId);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    List<OperationShape> operations = GleamTopDown.containedOperationsSorted(model, service);

    List<Field> handlerFields = new ArrayList<>();
    for (OperationShape op : operations) {
      Symbol opSym = sp.toSymbol(op);
      String handler = GleamServerDsl.handlerName(opSym.getName());
      String handlerType =
          behaviourModule + "." + GleamServerDsl.handlerTypeName(opSym.getName());
      handlerFields.add(Field.labelled(handler, handlerType));
    }

    List<Function> functions = new ArrayList<>();
    functions.add(routeFunction(operations, sp));
    for (OperationShape op : operations) {
      if (!op.hasTrait(HttpTrait.class)) {
        continue;
      }
      functions.add(tryRouteFunction(op, sp, httpIndex));
      functions.add(
          decodeInputFunction(op, model, sp, typesModule, codecModule, httpIndex));
      functions.add(encodeOutputFunction(op, model, sp, typesModule, codecModule));
    }
    functions.add(notFoundFunction());
    functions.add(finishHandlerFunction());
    functions.add(successResponseFunction());
    functions.add(queryParamsFunction());
    functions.add(queryStringFunction());
    functions.add(queryBoolFunction());
    functions.add(queryIntFunction());
    functions.add(headerStringFunction());
    if (serviceHasLabelBindings(model, operations)) {
      functions.addAll(labelParsingFunctions());
    }

    List<Import> imports = new ArrayList<>();
    imports.add(Import.of(typesModule));
    imports.add(Import.of(behaviourModule));
    imports.add(Import.of(codecModule));
    imports.add(Import.type("gleam/http/request", "Request"));
    imports.add(Import.type("gleam/http/response", "Response"));
    imports.add(Import.of("gleam/http"));
    imports.add(Import.of("gleam/bit_array"));
    imports.add(Import.of("gleam/dict"));
    imports.add(Import.of("gleam/json"));
    imports.add(Import.of("gleam/list"));
    imports.add(Import.of("gleam/option"));
    imports.add(Import.of("gleam/string"));
    imports.add(Import.of("gleam/uri"));
    imports.add(Import.of("gleam/int"));

    return Module.of(
        layout.routerModuleName(),
        DocComment.of(
            "Pure restJson1 router for "
                + service.getId()
                + ".\n\nMatches method/path, decodes input, calls handlers, and encodes"
                + " Response values. Generated; do not edit."),
        imports,
        List.of(),
        List.of(
            CustomType.pub(
                "Handlers",
                List.of(Variant.of("Handlers", handlerFields)),
                DocComment.of("Operation handlers supplied by the application."))),
        functions);
  }

  private static Function routeFunction(List<OperationShape> operations, SymbolProvider sp) {
    List<OperationShape> httpOps =
        operations.stream().filter(op -> op.hasTrait(HttpTrait.class)).toList();
    StringBuilder body = new StringBuilder();
    if (httpOps.isEmpty()) {
      body.append("not_found(request)");
    } else {
      for (int i = 0; i < httpOps.size(); i++) {
        OperationShape op = httpOps.get(i);
        String tryName = "try_" + sp.toSymbol(op).getName();
        String indent = "  ".repeat(i);
        if (i == 0) {
          body.append("case ").append(tryName).append("(request, handlers) {\n");
        } else {
          body.append(indent).append("case ").append(tryName).append("(request, handlers) {\n");
        }
        body.append(indent).append("  Ok(response) -> response\n");
        body.append(indent).append("  Error(_) ->\n");
        if (i == httpOps.size() - 1) {
          body.append(indent).append("    not_found(request)\n");
          for (int close = i; close >= 0; close--) {
            body.append("  ".repeat(close)).append("}\n");
          }
          // trim trailing newline handled by renderer
          if (body.charAt(body.length() - 1) == '\n') {
            body.setLength(body.length() - 1);
          }
        }
      }
    }
    return Function.pub(
        "route",
        List.of(
            Param.of("request", "Request(BitArray)"),
            Param.of("handlers", "Handlers")),
        "Response(BitArray)",
        RawExpr.of(body.toString()),
        DocComment.of("Route a request to a matching operation handler."));
  }

  private static Function tryRouteFunction(
      OperationShape op, SymbolProvider sp, HttpBindingIndex httpIndex) {
    Symbol opSym = sp.toSymbol(op);
    String opName = opSym.getName();
    String tryName = "try_" + opName;
    String handlerName = GleamServerDsl.handlerName(opName);
    HttpTrait http = op.expectTrait(HttpTrait.class);
    String methodCtor = httpMethodCtor(http.getMethod());
    int status = http.getCode();
    List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
    String uri = http.getUri().toString();

    StringBuilder body = new StringBuilder();
    body.append("case request.method {\n");
    body.append("  ").append(methodCtor).append(" ->\n");
    if (labels.isEmpty()) {
      body.append("    case request.path {\n");
      body.append("      \"").append(escape(uri)).append("\" -> {\n");
      body.append("        let input = decode_")
          .append(opName)
          .append("_input(request, dict.new())\n");
      body.append("        let Handlers(").append(handlerName).append(":, ..) = handlers\n");
      body.append("        Ok(finish_handler(")
          .append(handlerName)
          .append("(input), ")
          .append(status)
          .append(", encode_")
          .append(opName)
          .append("_output))\n");
      body.append("      }\n");
      body.append("      _ -> Error(Nil)\n");
      body.append("    }\n");
    } else {
      body.append("    case parse_labels(request.path, \"")
          .append(escape(uri))
          .append("\") {\n");
      body.append("      Ok(labels) -> {\n");
      body.append("        let input = decode_")
          .append(opName)
          .append("_input(request, labels)\n");
      body.append("        let Handlers(").append(handlerName).append(":, ..) = handlers\n");
      body.append("        Ok(finish_handler(")
          .append(handlerName)
          .append("(input), ")
          .append(status)
          .append(", encode_")
          .append(opName)
          .append("_output))\n");
      body.append("      }\n");
      body.append("      Error(_) -> Error(Nil)\n");
      body.append("    }\n");
    }
    body.append("  _ -> Error(Nil)\n");
    body.append('}');

    return Function.priv(
        tryName,
        List.of(
            Param.of("request", "Request(BitArray)"),
            Param.of("handlers", "Handlers")),
        "Result(Response(BitArray), Nil)",
        RawExpr.of(body.toString()));
  }

  private static Function decodeInputFunction(
      OperationShape op,
      Model model,
      SymbolProvider sp,
      String typesModule,
      String codecModule,
      HttpBindingIndex httpIndex) {
    Symbol opSym = sp.toSymbol(op);
    String opName = opSym.getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputCtor = typesModule + "." + GleamCodecNames.structureConstructor(sp, input);
    String inputType = GleamTopDown.qualifiedType(typesModule, sp.toSymbol(input));
    NullableIndex nullableIndex = NullableIndex.of(model);

    List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
    List<HttpBinding> queries = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY);
    List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> documents = httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT);
    List<HttpBinding> payload = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);
    boolean hasJsonBody = !documents.isEmpty() || !payload.isEmpty();

    StringBuilder body = new StringBuilder();
    if (hasJsonBody) {
      body.append("let base = case json.parse_bits(request.body, ")
          .append(codecModule)
          .append('.')
          .append(GleamCodecNames.decoderName(input))
          .append("()) {\n");
      body.append("  Ok(decoded) -> decoded\n");
      body.append("  Error(_) -> ")
          .append(emptyInputLiteral(inputCtor, input, sp, nullableIndex))
          .append('\n');
      body.append("}\n");
      if (!labels.isEmpty() || !queries.isEmpty() || !headers.isEmpty()) {
        body.append("let ")
            .append(inputCtor)
            .append("(")
            .append(shorthandFields(input, sp))
            .append(") = base\n");
        for (HttpBinding label : labels) {
          appendLabelAssign(body, label, input, sp, nullableIndex);
        }
        for (HttpBinding query : queries) {
          appendQueryAssign(body, query, input, model, sp, nullableIndex, codecModule);
        }
        for (HttpBinding header : headers) {
          appendHeaderAssign(body, header, input, sp, nullableIndex);
        }
        body.append(inputCtor).append("(").append(shorthandFields(input, sp)).append(')');
      } else {
        body.append("base");
      }
    } else {
      body.append(
          buildBoundInput(
              inputCtor,
              input,
              model,
              sp,
              nullableIndex,
              labels,
              queries,
              headers,
              codecModule));
    }

    boolean usesLabels = !labels.isEmpty();
    String labelsParam = usesLabels ? "labels" : "_labels";
    return Function.priv(
        "decode_" + opName + "_input",
        List.of(
            Param.of("request", "Request(BitArray)"),
            Param.of(labelsParam, "dict.Dict(String, String)")),
        inputType,
        RawExpr.of(body.toString()));
  }

  private static String shorthandFields(StructureShape input, SymbolProvider sp) {
    return input.members().stream()
        .map(m -> BeamMemberNames.fieldName(sp, m) + ":")
        .collect(Collectors.joining(", "));
  }

  private static String emptyInputLiteral(
      String inputCtor,
      StructureShape input,
      SymbolProvider sp,
      NullableIndex nullableIndex) {
    if (input.members().isEmpty()) {
      return inputCtor;
    }
    List<String> args = new ArrayList<>();
    for (MemberShape member : input.members()) {
      String field = BeamMemberNames.fieldName(sp, member);
      if (BeamMemberNullability.isMemberNullable(nullableIndex, input, member)) {
        args.add(field + ": option.None");
      } else {
        // Bound fields are overlaid after JSON decode; use inert placeholders here.
        args.add(field + ": \"\"");
      }
    }
    return inputCtor + "(" + String.join(", ", args) + ")";
  }

  private static String buildBoundInput(
      String inputCtor,
      StructureShape input,
      Model model,
      SymbolProvider sp,
      NullableIndex nullableIndex,
      List<HttpBinding> labels,
      List<HttpBinding> queries,
      List<HttpBinding> headers,
      String codecModule) {
    if (input.members().isEmpty()) {
      return inputCtor;
    }
    List<String> args = new ArrayList<>();
    for (MemberShape member : input.members()) {
      String field = BeamMemberNames.fieldName(sp, member);
      String memberName = member.getMemberName();
      boolean nullable = BeamMemberNullability.isMemberNullable(nullableIndex, input, member);
      HttpBinding label = findBinding(labels, memberName);
      HttpBinding query = findBinding(queries, memberName);
      HttpBinding header = findBinding(headers, memberName);
      String expr;
      if (label != null) {
        expr = labelExpr(memberName, nullable);
      } else if (query != null) {
        expr = queryExpr(model, query, nullable, codecModule);
      } else if (header != null) {
        expr = headerExpr(header, nullable);
      } else if (nullable) {
        expr = "option.None";
      } else {
        expr = fallbackRequired(model.expectShape(member.getTarget()));
      }
      args.add(field + ": " + expr);
    }
    return inputCtor + "(" + String.join(", ", args) + ")";
  }

  private static String fallbackRequired(Shape target) {
    if (target instanceof BooleanShape) {
      return "False";
    }
    if (target instanceof NumberShape) {
      return "0";
    }
    return "\"\"";
  }

  private static void appendLabelAssign(
      StringBuilder body,
      HttpBinding label,
      StructureShape input,
      SymbolProvider sp,
      NullableIndex nullableIndex) {
    String field = BeamMemberNames.fieldName(sp, label.getMember());
    boolean nullable =
        BeamMemberNullability.isMemberNullable(nullableIndex, input, label.getMember());
    body.append("let ")
        .append(field)
        .append(" = ")
        .append(labelExpr(label.getMember().getMemberName(), nullable))
        .append('\n');
  }

  private static void appendQueryAssign(
      StringBuilder body,
      HttpBinding query,
      StructureShape input,
      Model model,
      SymbolProvider sp,
      NullableIndex nullableIndex,
      String codecModule) {
    String field = BeamMemberNames.fieldName(sp, query.getMember());
    boolean nullable =
        BeamMemberNullability.isMemberNullable(nullableIndex, input, query.getMember());
    body.append("let ")
        .append(field)
        .append(" = ")
        .append(queryExpr(model, query, nullable, codecModule))
        .append('\n');
  }

  private static void appendHeaderAssign(
      StringBuilder body,
      HttpBinding header,
      StructureShape input,
      SymbolProvider sp,
      NullableIndex nullableIndex) {
    String field = BeamMemberNames.fieldName(sp, header.getMember());
    boolean nullable =
        BeamMemberNullability.isMemberNullable(nullableIndex, input, header.getMember());
    body.append("let ")
        .append(field)
        .append(" = ")
        .append(headerExpr(header, nullable))
        .append('\n');
  }

  private static String labelExpr(String memberName, boolean nullable) {
    if (nullable) {
      return "case dict.get(labels, \""
          + escape(memberName)
          + "\") { Ok(v) -> option.Some(v) Error(_) -> option.None }";
    }
    return "case dict.get(labels, \""
        + escape(memberName)
        + "\") { Ok(v) -> v Error(_) -> \"\" }";
  }

  private static String queryExpr(
      Model model, HttpBinding query, boolean nullable, String codecModule) {
    String location = query.getLocationName();
    Shape target = model.expectShape(query.getMember().getTarget());
    if (target instanceof BooleanShape) {
      return "query_bool(request, \"" + escape(location) + "\")";
    }
    if (target instanceof NumberShape && !(target instanceof IntEnumShape)) {
      String q = "query_int(request, \"" + escape(location) + "\")";
      return nullable ? q : "option.unwrap(" + q + ", 0)";
    }
    if (target instanceof StringShape && !(target instanceof EnumShape)) {
      String q = "query_string(request, \"" + escape(location) + "\")";
      return nullable ? q : "option.unwrap(" + q + ", \"\")";
    }
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String decoder = codecModule + "." + GleamCodecNames.decoderName(target) + "()";
      String parseArg =
          target instanceof IntEnumShape ? "raw" : "\"\\\"\" <> raw <> \"\\\"\"";
      return """
          case query_string(request, "%s") {
            option.Some(raw) ->
              case json.parse(%s, %s) {
                Ok(v) -> option.Some(v)
                Error(_) -> option.None
              }
            option.None -> option.None
          }"""
          .formatted(escape(location), parseArg, decoder);
    }
    // Complex query types: leave unset in this first-cut router.
    return nullable ? "option.None" : fallbackRequired(target);
  }

  private static String headerExpr(HttpBinding header, boolean nullable) {
    String headerName = header.getLocationName().toLowerCase(Locale.ROOT);
    if (nullable) {
      return "header_string(request, \"" + escape(headerName) + "\")";
    }
    return "option.unwrap(header_string(request, \"" + escape(headerName) + "\"), \"\")";
  }

  private static HttpBinding findBinding(List<HttpBinding> bindings, String memberName) {
    for (HttpBinding binding : bindings) {
      if (binding.getMember().getMemberName().equals(memberName)) {
        return binding;
      }
    }
    return null;
  }

  private static Function encodeOutputFunction(
      OperationShape op,
      Model model,
      SymbolProvider sp,
      String typesModule,
      String codecModule) {
    Symbol opSym = sp.toSymbol(op);
    String opName = opSym.getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputType = GleamTopDown.qualifiedType(typesModule, sp.toSymbol(output));
    String body =
        "bit_array.from_string(json.to_string("
            + codecModule
            + "."
            + GleamCodecNames.toJsonName(output)
            + "(output)))";
    return Function.priv(
        "encode_" + opName + "_output",
        List.of(Param.of("output", outputType)),
        "BitArray",
        RawExpr.of(body));
  }

  private static Function notFoundFunction() {
    return Function.priv(
        "not_found",
        List.of(Param.of("_request", "Request(BitArray)")),
        "Response(BitArray)",
        RawExpr.of(
            "response.new(404)\n"
                + "|> response.set_body(bit_array.from_string(\"not_found\"))"));
  }

  private static Function finishHandlerFunction() {
    return Function.priv(
        "finish_handler",
        List.of(
            Param.of("result", "Result(a, String)"),
            Param.of("status", "Int"),
            Param.of("encode", "fn(a) -> BitArray")),
        "Response(BitArray)",
        RawExpr.of(
            """
            case result {
              Ok(output) -> success_response(status, encode(output))
              Error(message) ->
                response.new(500)
                |> response.set_body(bit_array.from_string(message))
            }\
            """));
  }

  private static Function successResponseFunction() {
    return Function.priv(
        "success_response",
        List.of(Param.of("status", "Int"), Param.of("body", "BitArray")),
        "Response(BitArray)",
        RawExpr.of(
            """
            response.new(status)
            |> response.set_header("content-type", "application/json")
            |> response.set_body(body)\
            """));
  }

  private static Function queryParamsFunction() {
    return Function.priv(
        "query_params",
        List.of(Param.of("request", "Request(BitArray)")),
        "List(#(String, String))",
        RawExpr.of(
            """
            case request.query {
              option.Some(q) ->
                case uri.parse_query(q) {
                  Ok(params) -> params
                  Error(_) -> []
                }
              option.None -> []
            }\
            """));
  }

  private static Function queryStringFunction() {
    return Function.priv(
        "query_string",
        List.of(Param.of("request", "Request(BitArray)"), Param.of("name", "String")),
        "option.Option(String)",
        RawExpr.of(
            """
            case list.key_find(query_params(request), name) {
              Ok(value) -> option.Some(value)
              Error(_) -> option.None
            }\
            """));
  }

  private static Function queryBoolFunction() {
    return Function.priv(
        "query_bool",
        List.of(Param.of("request", "Request(BitArray)"), Param.of("name", "String")),
        "option.Option(Bool)",
        RawExpr.of(
            """
            case query_string(request, name) {
              option.Some("true") -> option.Some(True)
              option.Some("false") -> option.Some(False)
              _ -> option.None
            }\
            """));
  }

  private static Function queryIntFunction() {
    return Function.priv(
        "query_int",
        List.of(Param.of("request", "Request(BitArray)"), Param.of("name", "String")),
        "option.Option(Int)",
        RawExpr.of(
            """
            case query_string(request, name) {
              option.Some(raw) ->
                case int.parse(raw) {
                  Ok(n) -> option.Some(n)
                  Error(_) -> option.None
                }
              option.None -> option.None
            }\
            """));
  }

  private static Function headerStringFunction() {
    // Avoid shadowing gleam/http/request module: read headers from the record field.
    return Function.priv(
        "header_string",
        List.of(Param.of("request", "Request(BitArray)"), Param.of("name", "String")),
        "option.Option(String)",
        RawExpr.of(
            """
            case list.key_find(request.headers, string.lowercase(name)) {
              Ok(value) -> option.Some(value)
              Error(_) -> option.None
            }\
            """));
  }

  static boolean serviceHasLabelBindings(Model model, List<OperationShape> operations) {
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    for (OperationShape op : operations) {
      if (!httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL).isEmpty()) {
        return true;
      }
    }
    return false;
  }

  /** Label parsing helpers (parity with elixir/erlang router helpers). */
  static List<Function> labelParsingFunctions() {
    List<Function> functions = new ArrayList<>();
    functions.add(parseLabels());
    functions.add(segments());
    functions.add(matchSegments());
    functions.add(labelName());
    return functions;
  }

  static Function parseLabels() {
    return Function.priv(
        "parse_labels",
        List.of(Param.of("path", "String"), Param.of("template", "String")),
        "Result(dict.Dict(String, String), Nil)",
        RawExpr.of(
            """
            case match_segments(segments(path), segments(template), dict.new()) {
              Ok(labels) -> Ok(labels)
              Error(_) -> Error(Nil)
            }\
            """));
  }

  static Function segments() {
    return Function.priv(
        "segments",
        List.of(Param.of("path", "String")),
        "List(String)",
        RawExpr.of(
            """
            string.split(path, "/")
            |> list.filter(fn(seg) { seg != "" })\
            """));
  }

  static Function matchSegments() {
    return Function.priv(
        "match_segments",
        List.of(
            Param.of("path_segs", "List(String)"),
            Param.of("tpl_segs", "List(String)"),
            Param.of("acc", "dict.Dict(String, String)")),
        "Result(dict.Dict(String, String), Nil)",
        RawExpr.of(
            """
            case path_segs, tpl_segs {
              [], [] -> Ok(acc)
              [seg, ..rest_path], [tpl_seg, ..rest_tpl] ->
                case label_name(tpl_seg) {
                  Ok(key) -> {
                    let val = case uri.percent_decode(seg) {
                      Ok(decoded) -> decoded
                      Error(_) -> seg
                    }
                    match_segments(rest_path, rest_tpl, dict.insert(acc, key, val))
                  }
                  Error(_) if seg == tpl_seg ->
                    match_segments(rest_path, rest_tpl, acc)
                  Error(_) -> Error(Nil)
                }
              _, _ -> Error(Nil)
            }\
            """));
  }

  static Function labelName() {
    return Function.priv(
        "label_name",
        List.of(Param.of("tpl_seg", "String")),
        "Result(String, Nil)",
        RawExpr.of(
            """
            case string.starts_with(tpl_seg, "{") && string.ends_with(tpl_seg, "}") {
              True -> {
                let len = string.length(tpl_seg)
                Ok(string.slice(tpl_seg, at_index: 1, length: len - 2))
              }
              False -> Error(Nil)
            }\
            """));
  }

  private static String httpMethodCtor(String method) {
    return switch (method.toUpperCase(Locale.ROOT)) {
      case "GET" -> "http.Get";
      case "POST" -> "http.Post";
      case "PUT" -> "http.Put";
      case "PATCH" -> "http.Patch";
      case "DELETE" -> "http.Delete";
      case "HEAD" -> "http.Head";
      case "OPTIONS" -> "http.Options";
      default -> "http.Other(\"" + escape(method) + "\")";
    };
  }

  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
