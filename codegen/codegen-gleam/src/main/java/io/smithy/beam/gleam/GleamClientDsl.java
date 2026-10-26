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
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamGleamLayout;
import io.smithy.beam.core.BeamMemberNames;
import io.smithy.beam.core.BeamProtocolIds;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpTrait;

final class GleamClientDsl {

  private GleamClientDsl() {}

  static Module clientModule(
      BeamGleamLayout layout,
      ServiceShape service,
      Model model,
      SymbolProvider sp,
      String typesModuleName,
      ShapeId protocolTraitId) {
    boolean restJson =
        protocolTraitId != null && BeamProtocolIds.REST_JSON_1.equals(protocolTraitId);
    String codecModule =
        restJson ? layout.codecModuleName(protocolTraitId) : null;
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);

    List<Function> functions = new ArrayList<>();
    boolean needsBitArray = false;
    boolean needsOption = false;
    for (OperationShape op : GleamTopDown.containedOperationsSorted(model, service)) {
      if (restJson && op.hasTrait(HttpTrait.class)) {
        List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
        List<HttpBinding> documents =
            httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT);
        List<HttpBinding> payload =
            httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);
        if (!labels.isEmpty()) {
          needsOption = true;
        }
        if (!documents.isEmpty() || !payload.isEmpty()) {
          needsBitArray = true;
        }
        functions.add(wiredRequest(op, model, sp, typesModuleName, codecModule, httpIndex));
        functions.add(wiredResponse(op, model, sp, typesModuleName, codecModule, httpIndex));
      } else {
        functions.add(stubRequest(op, model, sp, typesModuleName));
        functions.add(stubResponse(op, model, sp, typesModuleName));
      }
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
                            + " parser on gleam/http types. Callers own HTTP send."));

    List<Import> imports = new ArrayList<>();
    imports.add(Import.of(typesModuleName));
    imports.add(Import.type("gleam/http/request", "Request"));
    imports.add(Import.type("gleam/http/response", "Response"));
    if (restJson) {
      imports.add(Import.of(codecModule));
      imports.add(Import.of("gleam/http"));
      if (needsBitArray) {
        imports.add(Import.of("gleam/bit_array"));
      }
      imports.add(Import.of("gleam/json"));
      if (needsOption) {
        imports.add(Import.of("gleam/option"));
      }
      imports.add(Import.of("gleam/string"));
      imports.add(Import.of("smithy_beam/http as beam_http"));
    }

    return Module.of(
        layout.clientModuleName(),
        moduleDoc,
        imports,
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

  private static Function wiredRequest(
      OperationShape op,
      Model model,
      SymbolProvider sp,
      String typesModuleName,
      String codecModule,
      HttpBindingIndex httpIndex) {
    Symbol opSym = sp.toSymbol(op);
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(input));
    String inputCtor = GleamCodecNames.structureConstructor(sp, input);
    HttpTrait http = op.expectTrait(HttpTrait.class);
    String method = httpMethodCtor(http.getMethod());
    String uri = http.getUri().toString();
    List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
    List<HttpBinding> documents = httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT);
    List<HttpBinding> payload = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);
    boolean hasJsonBody = !documents.isEmpty() || !payload.isEmpty();

    StringBuilder body = new StringBuilder();
    body.append("let ClientConfig(base_url:) = config\n");
    body.append("let ").append(typesModuleName).append('.').append(inputCtor).append("(");
    if (!labels.isEmpty()) {
      List<String> fields = new ArrayList<>();
      for (HttpBinding label : labels) {
        fields.add(BeamMemberNames.fieldName(sp, label.getMember()) + ":");
      }
      body.append(String.join(", ", fields)).append(", ..");
    } else {
      body.append("..");
    }
    body.append(") = input\n");
    body.append("let path = ");
    if (labels.isEmpty()) {
      body.append('"').append(escape(uri)).append('"');
    } else {
      body.append('"').append(escape(uri)).append('"');
      for (HttpBinding label : labels) {
        String field = BeamMemberNames.fieldName(sp, label.getMember());
        String token = "{" + label.getMember().getMemberName() + "}";
        // Input labels are Option under CLIENT NullableIndex; unwrap for path segments.
        body.append("\n  |> string.replace(\"")
            .append(escape(token))
            .append("\", option.unwrap(")
            .append(field)
            .append(", \"\"))");
      }
    }
    body.append('\n');
    if (hasJsonBody) {
      body.append("let json_body = json.to_string(")
          .append(codecModule)
          .append('.')
          .append(GleamCodecNames.toJsonName(input))
          .append("(input))\n");
      body.append("let body_bits = bit_array.from_string(json_body)\n");
    } else {
      body.append("let body_bits = <<>>\n");
    }
    body.append("request.new()\n");
    body.append("|> request.set_method(").append(method).append(")\n");
    body.append("|> beam_http.apply_base_url(base_url)\n");
    body.append("|> request.set_path(path)\n");
    body.append("|> request.set_body(body_bits)");

    DocComment doc =
        BeamDocumentation.forShape(op)
            .map(text -> DocComment.of(text + "\n\nBuilds a gleam/http Request."))
            .orElse(DocComment.of("Builds a gleam/http Request for " + opSym.getName() + "."));
    return Function.pub(
        opSym.getName() + "_request",
        List.of(Param.of("config", "ClientConfig"), Param.of("input", inputType)),
        "Request(BitArray)",
        RawExpr.of(body.toString()),
        doc);
  }

  private static Function wiredResponse(
      OperationShape op,
      Model model,
      SymbolProvider sp,
      String typesModuleName,
      String codecModule,
      HttpBindingIndex httpIndex) {
    Symbol opSym = sp.toSymbol(op);
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(output));
    HttpTrait http = op.expectTrait(HttpTrait.class);
    int successStatus = http.getCode();
    List<HttpBinding> documents = httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT);
    List<HttpBinding> payload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
    boolean hasJsonBody = !documents.isEmpty() || !payload.isEmpty() || !output.members().isEmpty();

    StringBuilder body = new StringBuilder();
    body.append("case response.status {\n");
    body.append("  ").append(successStatus).append(" ->\n");
    if (hasJsonBody) {
      body.append("    case json.parse_bits(response.body, ")
          .append(codecModule)
          .append('.')
          .append(GleamCodecNames.decoderName(output))
          .append("()) {\n");
      body.append("      Ok(out) -> Ok(out)\n");
      body.append(
          "      Error(_) -> Error(DecodeError(message: \"failed to decode response body\"))\n");
      body.append("    }\n");
    } else {
      // Empty output structure: construct with defaults via decoder on empty object.
      body.append("    case json.parse(\"{}\", ")
          .append(codecModule)
          .append('.')
          .append(GleamCodecNames.decoderName(output))
          .append("()) {\n");
      body.append("      Ok(out) -> Ok(out)\n");
      body.append(
          "      Error(_) -> Error(DecodeError(message: \"failed to decode empty response\"))\n");
      body.append("    }\n");
    }
    body.append(
        "  _ -> Error(UnexpectedStatus(status: response.status, body: response.body))\n");
    body.append('}');

    DocComment doc =
        BeamDocumentation.forShape(op)
            .map(text -> DocComment.of(text + "\n\nParses a gleam/http Response."))
            .orElse(DocComment.of("Parses a gleam/http Response for " + opSym.getName() + "."));
    return Function.pub(
        opSym.getName() + "_response",
        List.of(Param.of("response", "Response(BitArray)")),
        "Result(" + outputType + ", ClientError)",
        RawExpr.of(body.toString()),
        doc);
  }

  private static Function stubRequest(
      OperationShape op, Model model, SymbolProvider sp, String typesModuleName) {
    Symbol opSym = sp.toSymbol(op);
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(input));
    DocComment doc =
        BeamDocumentation.forShape(op)
            .map(text -> DocComment.of(text + "\n\nBuilds a gleam/http Request (stub)."))
            .orElse(DocComment.of("Builds a gleam/http Request for " + opSym.getName() + " (stub)."));
    return Function.pub(
        opSym.getName() + "_request",
        List.of(Param.of("_config", "ClientConfig"), Param.of("_input", inputType)),
        "Request(BitArray)",
        "todo",
        doc);
  }

  private static Function stubResponse(
      OperationShape op, Model model, SymbolProvider sp, String typesModuleName) {
    Symbol opSym = sp.toSymbol(op);
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputType = GleamTopDown.qualifiedType(typesModuleName, sp.toSymbol(output));
    DocComment doc =
        BeamDocumentation.forShape(op)
            .map(text -> DocComment.of(text + "\n\nParses a gleam/http Response (stub)."))
            .orElse(
                DocComment.of(
                    "Parses a gleam/http Response for " + opSym.getName() + " (stub)."));
    return Function.pub(
        opSym.getName() + "_response",
        List.of(Param.of("_response", "Response(BitArray)")),
        "Result(" + outputType + ", ClientError)",
        "Error(NotImplemented)",
        doc);
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
