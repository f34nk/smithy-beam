package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamS3CustomizationIndex;
import io.smithy.beam.core.BeamXmlBindingIndex;
import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExIfInList;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExIntegerPattern;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExListPattern;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMapFieldPattern;
import io.smithy.beam.ir.elixir.ExMapPattern;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExPattern;
import io.smithy.beam.ir.elixir.ExPipeline;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStruct;
import io.smithy.beam.ir.elixir.ExStructAccess;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.HttpErrorTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.IdempotencyTokenTrait;

final class ElixirRestXmlOperationIr {
  private static final ExVarPattern W = ExVarPattern.var("_");
  private static final String DEFAULT_CONTENT_TYPE = "application/xml";

  private ElixirRestXmlOperationIr() {}

  static ExFunction buildDecodeRequest(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputStruct = structName(sp, input);
    String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
    List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
    List<HttpBinding> payloadMembers =
        httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);

    ExMapPattern bodyPattern =
        ExMapPattern.map(ExMapFieldPattern.field(ExAtom.atom("body"), ExVarPattern.var("body")));
    List<ExPattern> patterns =
        labels.isEmpty() ? List.of(bodyPattern) : List.of(ExVarPattern.var("labels"), bodyPattern);

    List<ExMapEntry> structFields = new ArrayList<>();
    for (HttpBinding lb : labels) {
      String field = fieldName(sp, lb.getMember());
      structFields.add(
          ExMapEntry.entry(
              ExAtom.atom(field),
              ExCall.call(
                  "Map", "get", ExVar.var("labels"), ExString.string(lb.getLocationName()))));
    }
    if (!payloadMembers.isEmpty()) {
      HttpBinding payload = payloadMembers.get(0);
      MemberShape member = payload.getMember();
      Shape target = model.expectShape(member.getTarget());
      String field = fieldName(sp, member);
      ExExpr value =
          target instanceof BlobShape || target instanceof StringShape
              ? ExVar.var("body")
              : payloadBodyDecodeExpr(
                  model,
                  target,
                  BeamXmlBindingIndex.payloadRootElementName(member, target),
                  sp,
                  typesMod);
      structFields.add(ExMapEntry.entry(ExAtom.atom(field), value));
    }

    ExExpr body =
        ExTuple.tuple(ExAtom.atom("ok"), ExStruct.struct("Types." + inputStruct, structFields));

    ExSpec spec =
        labels.isEmpty()
            ? ExSpec.functionSpec(
                "decode_" + opName + "_request",
                "map()",
                "{:ok, " + inputType + "} | {:error, term()}")
            : ExSpec.functionSpec(
                "decode_" + opName + "_request",
                "map(), map()",
                "{:ok, " + inputType + "} | {:error, term()}");

    return ExFunction.functionWithSpec(
        "def",
        "decode_" + opName + "_request",
        spec,
        List.of(ExClause.blockClause(patterns, body)));
  }

  static ExFunction buildDecodeResponse(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));
    int successCode = httpIndex.getResponseCode(op);

    ExMapPattern successPattern =
        ExMapPattern.map(
            ExMapFieldPattern.field(ExAtom.atom("status"), ExIntegerPattern.integer(successCode)),
            ExMapFieldPattern.field(ExAtom.atom("headers"), ExVarPattern.var("headers")),
            ExMapFieldPattern.field(ExAtom.atom("body"), ExVarPattern.var("body")));

    ExClause successClause =
        ExClause.blockClause(
            List.of(successPattern),
            buildDecodeResponseSuccessBody(model, op, httpIndex, sp, typesMod, output)
                .toArray(ExExpr[]::new));

    List<ExClause> clauses = new ArrayList<>();
    clauses.add(successClause);

    if (op.getErrors().isEmpty()) {
      clauses.add(
          ExClause.blockClause(
              List.of(
                  ExMapPattern.map(
                      ExMapFieldPattern.field(ExAtom.atom("status"), ExVarPattern.var("status")),
                      ExMapFieldPattern.field(ExAtom.atom("body"), ExVarPattern.var("body")))),
              ExTuple.tuple(
                  ExAtom.atom("error"),
                  ExTuple.tuple(
                      ExAtom.atom("unknown_error"), ExVar.var("status"), ExVar.var("body")))));
    } else {
      clauses.add(
          ExClause.blockClauseSingleLineHead(
              List.of(
                  ExMapPattern.map(
                      ExMapFieldPattern.field(ExAtom.atom("status"), ExVarPattern.var("status")),
                      ExMapFieldPattern.field(ExAtom.atom("headers"), ExVarPattern.var("headers")),
                      ExMapFieldPattern.field(ExAtom.atom("body"), ExVarPattern.var("body")))),
              ExCallLocal.callLocal(
                  "decode_" + opName + "_response_error",
                  ExVar.var("status"),
                  ExVar.var("headers"),
                  ExVar.var("body"))));
    }

    ExSpec spec =
        ExSpec.functionSpec(
            "decode_" + opName + "_response",
            "map()",
            "{:ok, " + outputType + "} | {:error, term()}");

    return ExFunction.functionWithSpec("def", "decode_" + opName + "_response", spec, clauses);
  }

  static ExFunction buildErrorDispatch(
      Model model, OperationShape op, SymbolProvider sp, String typesMod) {
    String opName = sp.toSymbol(op).getName();
    List<ShapeId> errors = new ArrayList<>(op.getErrors());
    if (errors.isEmpty()) {
      return null;
    }

    List<ExClause> clauses = new ArrayList<>();
    for (ShapeId errorId : errors) {
      StructureShape errShape = model.expectShape(errorId, StructureShape.class);
      String modName = sp.toSymbol(errShape).getName();
      int httpStatus =
          errShape.hasTrait(HttpErrorTrait.class)
              ? errShape.expectTrait(HttpErrorTrait.class).getCode()
              : -1;
      if (httpStatus <= 0) {
        continue;
      }
      List<ExMapEntry> nilFields = new ArrayList<>();
      for (MemberShape member : errShape.members()) {
        if (member.getMemberName().equals("__beam_error_kind")) {
          continue;
        }
        nilFields.add(ExMapEntry.entry(ExAtom.atom(fieldName(sp, member)), ExAtom.atom("nil")));
      }
      clauses.add(
          ExClause.blockClause(
              List.of(
                  ExIntegerPattern.integer(httpStatus),
                  ExVarPattern.var("_headers"),
                  ExVarPattern.var("_body")),
              ExTuple.tuple(
                  ExAtom.atom("error"),
                  ExCallLocal.callLocal(
                      "struct!",
                      ExVar.var(typesMod + "." + modName),
                      ExMap.map(nilFields.toArray(ExMapEntry[]::new))))));
    }

    clauses.add(
        ExClause.blockClause(
            List.of(
                ExVarPattern.var("status"), ExVarPattern.var("_headers"), ExVarPattern.var("body")),
            ExTuple.tuple(
                ExAtom.atom("error"),
                ExTuple.tuple(
                    ExAtom.atom("unknown_error"), ExVar.var("status"), ExVar.var("body")))));

    ExSpec spec =
        ExSpec.functionSpec(
            "decode_" + opName + "_response_error", "integer(), map(), term()", "{:error, term()}");

    return ExFunction.functionWithSpec(
        "defp", "decode_" + opName + "_response_error", spec, clauses);
  }

  static ExFunction buildEncodeRequest(
      Model model,
      ServiceShape service,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      boolean encodeWithConfig) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    HttpTrait httpTrait = op.expectTrait(HttpTrait.class);
    String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
    String httpRequestType = "%" + runtimeMod + ".HttpRequest{}";

    List<HttpBinding> labels = httpIndex.getRequestBindings(op, HttpBinding.Location.LABEL);
    List<HttpBinding> queries = httpIndex.getRequestBindings(op, HttpBinding.Location.QUERY);
    List<HttpBinding> headers = httpIndex.getRequestBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> payloadMembers =
        httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);

    List<ExPattern> patterns =
        encodeWithConfig
            ? List.of(ExVarPattern.var("config"), ExVarPattern.var("input"))
            : List.of(ExVarPattern.var("input"));

    List<ExExpr> body = new ArrayList<>();
    body.addAll(buildIdempotencyTokenExprs(input, sp));

    BeamS3CustomizationIndex s3Index = BeamS3CustomizationIndex.of(model);
    boolean s3BucketAddressing =
        s3Index.isS3Service(service) && s3Index.bucketLabelBinding(op).isPresent();
    boolean setHost = s3BucketAddressing;

    if (s3BucketAddressing) {
      String bucketField = fieldName(sp, s3Index.bucketLabelBinding(op).orElseThrow().getMember());
      ExExpr keyExpr =
          s3Index
              .keyLabelBinding(op)
              .<ExExpr>map(
                  binding ->
                      ExCall.call(
                          "Kernel",
                          "to_string",
                          ExStructAccess.structAccess(
                              ExVar.var("input"), fieldName(sp, binding.getMember()))))
              .orElse(ExString.string(""));
      if (encodeWithConfig) {
        body.add(
            ExMatch.match(
                ExTuplePattern.tuple(ExVarPattern.var("host"), ExVarPattern.var("path")),
                ExCall.call(
                    "S3Endpoint",
                    "resolve_bucket_url",
                    ExVar.var("config"),
                    ExCall.call(
                        "Kernel",
                        "to_string",
                        ExStructAccess.structAccess(ExVar.var("input"), bucketField)),
                    keyExpr)));
      } else {
        body.add(
            ExMatch.match(
                ExTuplePattern.tuple(ExVarPattern.var("host"), ExVarPattern.var("path")),
                ExCall.call(
                    "S3Endpoint",
                    "resolve_bucket_url",
                    ExMap.map(),
                    ExCall.call(
                        "Kernel",
                        "to_string",
                        ExStructAccess.structAccess(ExVar.var("input"), bucketField)),
                    keyExpr)));
      }
    } else {
      body.add(
          ExMatch.match(
              ExVarPattern.var("path"),
              buildPathExpression(httpTrait.getUri().toString(), labels, sp, "input")));
    }

    body.addAll(buildQueryExprs(queries, sp));
    body.addAll(buildRequestHeadersExprs(model, headers, sp, "input"));
    body.addAll(buildRequestBodyExprs(model, payloadMembers, httpTrait.getMethod(), sp, typesMod));
    ElixirHttpChecksumIr.requestChecksumHeadersExpr(model, op, sp, "headers").ifPresent(body::add);

    List<ExMapEntry> requestFields = new ArrayList<>();
    requestFields.add(
        ExMapEntry.entry(ExAtom.atom("method"), ExString.string(httpTrait.getMethod())));
    requestFields.add(ExMapEntry.entry(ExAtom.atom("path"), ExVar.var("path")));
    requestFields.add(ExMapEntry.entry(ExAtom.atom("query"), ExVar.var("query")));
    requestFields.add(ExMapEntry.entry(ExAtom.atom("headers"), ExVar.var("headers")));
    requestFields.add(ExMapEntry.entry(ExAtom.atom("body"), ExVar.var("body")));
    if (setHost) {
      requestFields.add(ExMapEntry.entry(ExAtom.atom("host"), ExVar.var("host")));
    }
    body.add(ExStruct.struct(runtimeMod + ".HttpRequest", requestFields));

    ExSpec spec =
        encodeWithConfig
            ? ExSpec.functionSpec(
                "encode_" + opName + "_request", "map(), " + inputType, httpRequestType)
            : ExSpec.functionSpec("encode_" + opName + "_request", inputType, httpRequestType);

    return ExFunction.functionWithSpec(
        "def",
        "encode_" + opName + "_request",
        spec,
        List.of(
            encodeWithConfig
                ? ExClause.blockClauseSingleLineHead(patterns, body.toArray(ExExpr[]::new))
                : ExClause.blockClause(patterns, body.toArray(ExExpr[]::new))));
  }

  static ExFunction buildEncodeResponse(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));
    int successCode = httpIndex.getResponseCode(op);
    List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);

    ExSpec spec = ExSpec.functionSpec("encode_" + opName + "_response", outputType, "map()");

    return ExFunction.functionWithSpec(
        "def",
        "encode_" + opName + "_response",
        spec,
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("output")),
                buildEncodeResponseBodyExprs(
                        model,
                        output,
                        op,
                        httpIndex,
                        respPayload,
                        successCode,
                        sp,
                        typesMod,
                        runtimeMod)
                    .toArray(ExExpr[]::new))));
  }

  private static List<ExExpr> buildDecodeResponseSuccessBody(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      StructureShape output) {
    List<HttpBinding> respHeaders = httpIndex.getResponseBindings(op, HttpBinding.Location.HEADER);
    List<HttpBinding> respPrefixHeaders =
        httpIndex.getResponseBindings(op, HttpBinding.Location.PREFIX_HEADERS);
    List<HttpBinding> respPayload = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
    Set<String> httpBoundMembers = new LinkedHashSet<>();
    for (HttpBinding binding : concat(respHeaders, respPrefixHeaders, respPayload)) {
      httpBoundMembers.add(binding.getMember().getMemberName());
    }
    List<MemberShape> xmlBodyMembers =
        output.members().stream()
            .filter(member -> !httpBoundMembers.contains(member.getMemberName()))
            .toList();

    List<ExExpr> body = new ArrayList<>();
    for (HttpBinding hb : respHeaders) {
      body.add(headerBindingDecodeExpr(model, sp, hb));
    }
    for (HttpBinding ph : respPrefixHeaders) {
      String field = fieldName(sp, ph.getMember());
      body.add(
          ExMatch.match(
              ExVarPattern.var(field),
              ExCallLocal.callLocal(
                  "prefix_headers_from_list",
                  ExVar.var("headers"),
                  ExString.string(ph.getLocationName()))));
    }

    if (!respPayload.isEmpty()) {
      HttpBinding pb = respPayload.get(0);
      MemberShape member = pb.getMember();
      Shape target = model.expectShape(member.getTarget());
      String field = fieldName(sp, member);
      if (target instanceof BlobShape || target instanceof StringShape) {
        body.add(ExMatch.match(ExVarPattern.var(field), ExVar.var("body")));
      } else {
        String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
        body.add(
            ExMatch.match(
                ExVarPattern.var(field),
                payloadBodyDecodeExpr(model, target, rootElement, sp, typesMod)));
      }
    } else if (!xmlBodyMembers.isEmpty()) {
      String rootElement = BeamXmlBindingIndex.shapeElementName(output);
      body.add(
          ExMatch.match(
              ExVarPattern.var("parsed"),
              ExCase.caseExpr(
                  ExCallLocal.callLocal(
                      "parse_xml_root", ExVar.var("body"), ExString.string(rootElement)),
                  ExCaseBranch.branch(
                      ExTuplePattern.tuple(ExAtomPattern.atom("ok"), ExVarPattern.var("root")),
                      ExVar.var("root")),
                  ExCaseBranch.branch(
                      ExTuplePattern.tuple(ExAtomPattern.atom("error"), W), ExAtom.atom("nil")))));
      body.addAll(buildMembersFromXmlExprs(model, xmlBodyMembers, "parsed", sp, typesMod));
    }

    List<ExMapEntry> structFields = new ArrayList<>();
    Set<String> boundFields = new LinkedHashSet<>();
    for (HttpBinding hb : concat(respHeaders, respPrefixHeaders, respPayload)) {
      String field = fieldName(sp, hb.getMember());
      if (boundFields.add(field)) {
        structFields.add(ExMapEntry.entry(ExAtom.atom(field), ExVar.var(field)));
      }
    }
    if (respPayload.isEmpty() && !xmlBodyMembers.isEmpty()) {
      for (MemberShape member : xmlBodyMembers) {
        String field = fieldName(sp, member);
        if (boundFields.add(field)) {
          structFields.add(ExMapEntry.entry(ExAtom.atom(field), ExVar.var(field)));
        }
      }
    }

    ExExpr success =
        ExTuple.tuple(
            ExAtom.atom("ok"), ExStruct.struct("Types." + structName(sp, output), structFields));
    body.add(ElixirHttpChecksumIr.responseChecksumGuardExpr(model, op, success));
    return body;
  }

  @SafeVarargs
  private static List<HttpBinding> concat(List<HttpBinding>... lists) {
    List<HttpBinding> result = new ArrayList<>();
    for (List<HttpBinding> list : lists) {
      result.addAll(list);
    }
    return result;
  }

  private static ExMatch headerBindingDecodeExpr(
      Model model, SymbolProvider sp, HttpBinding binding) {
    String field = fieldName(sp, binding.getMember());
    ExExpr headerValue =
        ExCallLocal.callLocal(
            "header_value", ExVar.var("headers"), ExString.string(binding.getLocationName()));
    Shape target = model.expectShape(binding.getMember().getTarget());
    ExExpr value = headerValue;
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = BeamNameUtils.toSnakeCase(target.getId().getName());
      value = ExCallLocal.callLocal("decode_" + helperName, headerValue);
    }
    return ExMatch.match(ExVarPattern.var(field), value);
  }

  private static List<ExExpr> buildMembersFromXmlExprs(
      Model model,
      Iterable<MemberShape> members,
      String xmlVar,
      SymbolProvider sp,
      String typesMod) {
    List<ExExpr> exprs = new ArrayList<>();
    for (MemberShape member : members) {
      exprs.add(buildMemberFromXmlExpr(model, member, xmlVar, sp, typesMod));
    }
    return exprs;
  }

  private static ExMatch buildMemberFromXmlExpr(
      Model model, MemberShape member, String xmlVar, SymbolProvider sp, String typesMod) {
    String field = fieldName(sp, member);
    Shape target = model.expectShape(member.getTarget());
    if (BeamXmlBindingIndex.isXmlAttribute(member)) {
      return ExMatch.match(
          ExVarPattern.var(field),
          ExCase.caseExpr(
              ExVar.var(xmlVar),
              ExCaseBranch.branch(ExNilPattern.nil(), ExAtom.atom("nil")),
              ExCaseBranch.branch(
                  W,
                  ExCallLocal.callLocal(
                      "xml_attribute",
                      ExVar.var(xmlVar),
                      ExString.string(BeamXmlBindingIndex.memberElementName(member))))));
    }
    if (target instanceof ListShape listShape) {
      return ExMatch.match(
          ExVarPattern.var(field),
          ExCase.caseExpr(
              ExVar.var(xmlVar),
              ExCaseBranch.branch(ExNilPattern.nil(), ExAtom.atom("nil")),
              ExCaseBranch.branch(
                  W, decodeListFieldFromXml(model, member, listShape, xmlVar, sp, typesMod))));
    }
    if (target instanceof StructureShape nested) {
      String element = BeamXmlBindingIndex.memberElementName(member);
      String nestedVar = field + "_xml";
      return ExMatch.match(
          ExVarPattern.var(field),
          ExCase.caseExpr(
              ExVar.var(xmlVar),
              ExCaseBranch.branch(ExNilPattern.nil(), ExAtom.atom("nil")),
              ExCaseBranch.branch(
                  W,
                  ExCase.caseExpr(
                      ExCallLocal.callLocal(
                          "find_element",
                          ExString.string(element),
                          ExCallLocal.callLocal("element_content", ExVar.var(xmlVar))),
                      ExCaseBranch.branch(ExNilPattern.nil(), ExAtom.atom("nil")),
                      ExCaseBranch.branch(
                          ExVarPattern.var(nestedVar),
                          decodeStructureExpr(model, nested, nestedVar, sp, typesMod))))));
    }
    return ExMatch.match(
        ExVarPattern.var(field),
        ExCase.caseExpr(
            ExVar.var(xmlVar),
            ExCaseBranch.branch(ExNilPattern.nil(), ExAtom.atom("nil")),
            ExCaseBranch.branch(
                W,
                ExCallLocal.callLocal(
                    "xml_child_text",
                    ExVar.var(xmlVar),
                    ExString.string(BeamXmlBindingIndex.memberElementName(member))))));
  }

  private static List<ExExpr> buildEncodeResponseBodyExprs(
      Model model,
      StructureShape output,
      OperationShape op,
      HttpBindingIndex httpIndex,
      List<HttpBinding> respPayload,
      int successCode,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod) {
    List<ExExpr> body = new ArrayList<>();
    body.add(
        ExMatch.match(
            ExVarPattern.var("headers"),
            ExList.list(
                ExTuple.tuple(
                    ExString.string("Content-Type"), ExString.string(DEFAULT_CONTENT_TYPE)))));

    if (!respPayload.isEmpty()) {
      HttpBinding pb = respPayload.get(0);
      MemberShape member = pb.getMember();
      Shape target = model.expectShape(member.getTarget());
      String field = fieldName(sp, member);
      if (target instanceof BlobShape || target instanceof StringShape) {
        body.add(
            ExMatch.match(
                ExVarPattern.var("body"),
                ExOp.op(
                    "||",
                    ExCall.call("Map", "get", ExVar.var("output"), ExAtom.atom(field)),
                    ExString.string(""))));
      } else {
        String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
        body.addAll(
            buildPayloadEncodeBodyExprs(model, target, rootElement, field, sp, typesMod, "output"));
      }
    } else if (!output.members().isEmpty()) {
      String rootElement = BeamXmlBindingIndex.shapeElementName(output);
      List<ExMapEntry> entries = new ArrayList<>();
      for (MemberShape member : output.members()) {
        String wireName = BeamXmlBindingIndex.memberElementName(member);
        entries.add(
            ExMapEntry.entry(
                ExString.string(wireName),
                ExStructAccess.structAccess(ExVar.var("output"), fieldName(sp, member))));
      }
      body.add(
          ExMatch.match(
              ExVarPattern.var("member_map"), ExMap.map(entries.toArray(ExMapEntry[]::new))));
      body.add(
          ExMatch.match(
              ExVarPattern.var("body"),
              ExCallLocal.callLocal(
                  "encode_xml",
                  ExMap.map(
                      ExMapEntry.entry(ExString.string(rootElement), ExVar.var("member_map"))),
                  ExCallLocal.callLocal("xml_namespace"))));
    } else {
      body.add(ExMatch.match(ExVarPattern.var("body"), ExString.string("")));
    }

    body.add(
        ExStruct.struct(
            runtimeMod + ".HttpResponse",
            ExMapEntry.entry(ExAtom.atom("status"), ExInteger.integer(successCode)),
            ExMapEntry.entry(ExAtom.atom("headers"), ExVar.var("headers")),
            ExMapEntry.entry(ExAtom.atom("body"), ExVar.var("body"))));
    return body;
  }

  private static List<ExExpr> buildPayloadEncodeBodyExprs(
      Model model,
      Shape target,
      String rootElement,
      String field,
      SymbolProvider sp,
      String typesMod,
      String valueVar) {
    List<ExExpr> exprs = new ArrayList<>();
    ExExpr payloadCaseBody;
    if (target instanceof StructureShape structure) {
      payloadCaseBody =
          ExExprBlock.block(
              ExMatch.match(
                  ExVarPattern.var("member_map"),
                  buildStructureMap(model, structure, "payload_value", sp)),
              ExCallLocal.callLocal(
                  "encode_xml",
                  ExMap.map(
                      ExMapEntry.entry(ExString.string(rootElement), ExVar.var("member_map"))),
                  ExCallLocal.callLocal("xml_namespace")));
    } else if (target instanceof UnionShape union) {
      payloadCaseBody = buildUnionPayloadEncodeExpr(model, union, rootElement, sp, typesMod);
    } else {
      payloadCaseBody =
          ExCallLocal.callLocal(
              "encode_xml",
              ExMap.map(ExMapEntry.entry(ExString.string(rootElement), ExVar.var("payload_value"))),
              ExCallLocal.callLocal("xml_namespace"));
    }

    exprs.add(
        ExMatch.match(
            ExVarPattern.var("body"),
            ExCase.caseExpr(
                ExCall.call("Map", "get", ExVar.var(valueVar), ExAtom.atom(field)),
                ExCaseBranch.branch(ExNilPattern.nil(), ExString.string("")),
                ExCaseBranch.branch(ExVarPattern.var("payload_value"), payloadCaseBody))));
    return exprs;
  }

  private static ExExpr buildUnionPayloadEncodeExpr(
      Model model, UnionShape union, String rootElement, SymbolProvider sp, String typesMod) {
    List<ExCaseBranch> branches = new ArrayList<>();
    for (MemberShape member : union.members()) {
      String element = BeamXmlBindingIndex.memberElementName(member);
      String tag = unionTagForMember(sp, member);
      Shape memberTarget = model.expectShape(member.getTarget());
      ExExpr innerValue;
      List<ExExpr> blockExprs = new ArrayList<>();
      if (memberTarget instanceof StructureShape structure) {
        blockExprs.add(
            ExMatch.match(
                ExVarPattern.var("inner_map"), buildStructureMap(model, structure, "v", sp)));
        innerValue = ExVar.var("inner_map");
      } else {
        innerValue = ExVar.var("v");
      }
      blockExprs.add(
          ExCallLocal.callLocal(
              "encode_xml",
              ExMap.map(
                  ExMapEntry.entry(
                      ExString.string(rootElement),
                      ExMap.map(ExMapEntry.entry(ExString.string(element), innerValue)))),
              ExCallLocal.callLocal("xml_namespace")));
      branches.add(
          ExCaseBranch.branch(
              ExTuplePattern.tuple(ExAtomPattern.atom(tag), ExVarPattern.var("v")),
              blockExprs.size() == 1
                  ? blockExprs.get(0)
                  : ExExprBlock.block(blockExprs.toArray(ExExpr[]::new))));
    }
    branches.add(ExCaseBranch.branch(ExNilPattern.nil(), ExString.string("")));
    return ExCase.caseExpr(ExVar.var("payload_value"), branches.toArray(ExCaseBranch[]::new));
  }

  private static List<ExExpr> buildRequestBodyExprs(
      Model model,
      List<HttpBinding> payloadMembers,
      String method,
      SymbolProvider sp,
      String typesMod) {
    List<ExExpr> exprs = new ArrayList<>();
    if (payloadMembers.isEmpty()
        || method.equals("GET")
        || method.equals("DELETE")
        || method.equals("HEAD")) {
      exprs.add(ExMatch.match(ExVarPattern.var("body"), ExString.string("")));
      return exprs;
    }

    HttpBinding payload = payloadMembers.get(0);
    MemberShape member = payload.getMember();
    Shape target = model.expectShape(member.getTarget());
    String field = fieldName(sp, member);

    if (target instanceof BlobShape || target instanceof StringShape) {
      exprs.add(
          ExMatch.match(
              ExVarPattern.var("body"),
              ExCase.caseExpr(
                  ExStructAccess.structAccess(ExVar.var("input"), field),
                  ExCaseBranch.branch(ExNilPattern.nil(), ExString.string("")),
                  ExCaseBranch.branch(ExVarPattern.var("value"), ExVar.var("value")))));
      return exprs;
    }

    String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
    ExExpr payloadCaseBody;
    if (target instanceof StructureShape structure) {
      payloadCaseBody =
          ExExprBlock.block(
              ExMatch.match(
                  ExVarPattern.var("member_map"),
                  buildStructureMap(model, structure, "payload_value", sp)),
              ExCallLocal.callLocal(
                  "encode_xml",
                  ExMap.map(
                      ExMapEntry.entry(ExString.string(rootElement), ExVar.var("member_map"))),
                  ExCallLocal.callLocal("xml_namespace")));
    } else if (target instanceof UnionShape union) {
      payloadCaseBody = buildUnionPayloadEncodeExpr(model, union, rootElement, sp, typesMod);
    } else {
      payloadCaseBody =
          ExCallLocal.callLocal(
              "encode_xml",
              ExMap.map(ExMapEntry.entry(ExString.string(rootElement), ExVar.var("payload_value"))),
              ExCallLocal.callLocal("xml_namespace"));
    }

    exprs.add(
        ExMatch.match(
            ExVarPattern.var("body"),
            ExCase.caseExpr(
                ExStructAccess.structAccess(ExVar.var("input"), field),
                ExCaseBranch.branch(ExNilPattern.nil(), ExString.string("")),
                ExCaseBranch.branch(ExVarPattern.var("payload_value"), payloadCaseBody))));
    return exprs;
  }

  private static List<ExExpr> buildQueryExprs(List<HttpBinding> queries, SymbolProvider sp) {
    if (queries.isEmpty()) {
      return List.of(ExMatch.match(ExVarPattern.var("query"), ExMap.map()));
    }
    List<ExMapEntry> entries = new ArrayList<>();
    for (HttpBinding qb : queries) {
      String field = fieldName(sp, qb.getMember());
      entries.add(
          ExMapEntry.entry(
              ExString.string(qb.getLocationName()),
              ExStructAccess.structAccess(ExVar.var("input"), field)));
    }
    return List.of(ElixirJsonCodecIr.rejectNilMapPipeline("query", entries));
  }

  private static List<ExExpr> buildRequestHeadersExprs(
      Model model, List<HttpBinding> headers, SymbolProvider sp, String recordVar) {
    if (headers.isEmpty()) {
      return List.of(
          ExMatch.match(
              ExVarPattern.var("headers"),
              ExList.list(
                  ExTuple.tuple(
                      ExString.string("Content-Type"), ExString.string(DEFAULT_CONTENT_TYPE)))));
    }
    List<ExExpr> exprs = new ArrayList<>();
    exprs.addAll(extraHeadersPipeline(model, sp, recordVar, headers));
    exprs.add(
        ExMatch.match(
            ExVarPattern.var("headers"),
            ExList.cons(
                ExTuple.tuple(
                    ExString.string("Content-Type"), ExString.string(DEFAULT_CONTENT_TYPE)),
                ExVar.var("extra_headers"))));
    return exprs;
  }

  private static ExExpr encodeBindingWireValueExpr(
      Model model, SymbolProvider sp, MemberShape member, ExExpr valueExpr, boolean queryValues) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = BeamNameUtils.toSnakeCase(target.getId().getName());
      return ExCallLocal.callLocal("encode_" + helperName, valueExpr);
    }
    if (queryValues) {
      return ExCallLocal.callLocal("encode_query_value", valueExpr);
    }
    return ExCall.call("Kernel", "to_string", valueExpr);
  }

  private static List<ExExpr> extraHeadersPipeline(
      Model model, SymbolProvider sp, String recordVar, List<HttpBinding> headers) {
    List<ExExpr> entries = new ArrayList<>();
    for (HttpBinding hb : headers) {
      String field = fieldName(sp, hb.getMember());
      ExExpr fieldValue = ExStructAccess.structAccess(ExVar.var(recordVar), field);
      entries.add(
          ExIfInList.ifInList(
              ExOp.op("!=", fieldValue, ExAtom.atom("nil")),
              ExTuple.tuple(
                  ExString.string(hb.getLocationName()),
                  encodeBindingWireValueExpr(model, sp, hb.getMember(), fieldValue, false))));
    }
    return List.of(
        ExPipeline.pipeline(
            "extra_headers",
            ExList.list(entries.toArray(ExExpr[]::new)),
            ExCall.call(
                "Enum",
                "reject",
                ExAnonymousFn.compactFn(
                    ExClause.inlineClause(
                        List.of(ExVarPattern.var("x")),
                        ExCall.call("Kernel", "is_nil", ExVar.var("x")))))));
  }

  private static List<ExExpr> buildIdempotencyTokenExprs(StructureShape input, SymbolProvider sp) {
    List<ExExpr> exprs = new ArrayList<>();
    for (MemberShape member : input.members()) {
      if (!member.hasTrait(IdempotencyTokenTrait.class)) {
        continue;
      }
      String field = fieldName(sp, member);
      exprs.add(
          ExMatch.match(
              ExVarPattern.var("input"),
              ExCase.caseExpr(
                  ExStructAccess.structAccess(ExVar.var("input"), field),
                  ExCaseBranch.branch(
                      ExNilPattern.nil(),
                      ExCall.call(
                          "Map",
                          "put",
                          ExVar.var("input"),
                          ExAtom.atom(field),
                          ExCallLocal.callLocal("generate_uuid"))),
                  ExCaseBranch.branch(ExVarPattern.var("_"), ExVar.var("input")))));
    }
    return exprs;
  }

  private static ExExpr payloadBodyDecodeExpr(
      Model model, Shape target, String rootElement, SymbolProvider sp, String typesMod) {
    return ExCase.caseExpr(
        ExCallLocal.callLocal("parse_xml_root", ExVar.var("body"), ExString.string(rootElement)),
        ExCaseBranch.branch(
            ExTuplePattern.tuple(ExAtomPattern.atom("ok"), ExVarPattern.var("root")),
            payloadDecodeExpr(model, target, "root", sp, typesMod)),
        ExCaseBranch.branch(
            ExTuplePattern.tuple(ExAtomPattern.atom("error"), W), ExAtom.atom("nil")));
  }

  private static ExExpr payloadDecodeExpr(
      Model model, Shape target, String xmlVar, SymbolProvider sp, String typesMod) {
    if (target instanceof StructureShape structure) {
      return decodeStructureExpr(model, structure, xmlVar, sp, typesMod);
    }
    if (target instanceof UnionShape union) {
      return decodeUnionFromXml(model, union, xmlVar, sp, typesMod);
    }
    return ExCallLocal.callLocal("xml_child_text", ExVar.var(xmlVar), ExString.string(""));
  }

  private static ExStruct decodeStructureExpr(
      Model model, StructureShape structure, String xmlVar, SymbolProvider sp, String typesMod) {
    List<ExMapEntry> fields = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      if (BeamXmlBindingIndex.isXmlAttribute(member)) {
        continue;
      }
      String field = fieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      ExExpr value;
      if (target instanceof ListShape listShape) {
        value = decodeListFieldFromXml(model, member, listShape, xmlVar, sp, typesMod);
      } else {
        value =
            ExCallLocal.callLocal(
                "xml_child_text",
                ExVar.var(xmlVar),
                ExString.string(BeamXmlBindingIndex.memberElementName(member)));
      }
      fields.add(ExMapEntry.entry(ExAtom.atom(field), value));
    }
    return ExStruct.struct("Types." + structName(sp, structure), fields);
  }

  private static ExExpr decodeListFieldFromXml(
      Model model,
      MemberShape member,
      ListShape listShape,
      String xmlVar,
      SymbolProvider sp,
      String typesMod) {
    String element = BeamXmlBindingIndex.memberElementName(member);
    String itemElement = BeamXmlBindingIndex.listItemElementName(member, listShape, model);
    ExExpr listNameArg =
        BeamXmlBindingIndex.isContainerMemberFlattened(member)
            ? ExAtom.atom("nil")
            : ExString.string(element);
    Shape listMember = model.expectShape(listShape.getMember().getTarget());
    if (listMember instanceof StructureShape nested) {
      return ExCallLocal.callLocal(
          "xml_child_struct_list",
          ExVar.var(xmlVar),
          listNameArg,
          ExString.string(itemElement),
          ExAnonymousFn.fn(
              ExClause.inlineClause(
                  List.of(ExVarPattern.var("item")),
                  decodeStructureExpr(model, nested, "item", sp, typesMod))));
    }
    return ExCallLocal.callLocal(
        "xml_child_list", ExVar.var(xmlVar), listNameArg, ExString.string(itemElement));
  }

  private static ExExpr decodeUnionFromXml(
      Model model, UnionShape union, String xmlVar, SymbolProvider sp, String typesMod) {
    return buildUnionDecodeCase(model, union, xmlVar, sp, typesMod, 0);
  }

  private static ExExpr buildUnionDecodeCase(
      Model model,
      UnionShape union,
      String xmlVar,
      SymbolProvider sp,
      String typesMod,
      int memberIndex) {
    List<MemberShape> members = new ArrayList<>(union.members());
    if (memberIndex >= members.size()) {
      return ExAtom.atom("nil");
    }
    MemberShape member = members.get(memberIndex);
    String element = BeamXmlBindingIndex.memberElementName(member);
    String tag = unionTagForMember(sp, member);
    ExExpr valueExpr = decodeUnionMemberValue(model, member, "element", sp, typesMod);
    ExExpr nextArm = buildUnionDecodeCase(model, union, xmlVar, sp, typesMod, memberIndex + 1);
    return ExCase.caseExpr(
        ExCallLocal.callLocal(
            "find_element",
            ExString.string(element),
            ExCallLocal.callLocal("element_content", ExVar.var(xmlVar))),
        ExCaseBranch.branch(ExNilPattern.nil(), nextArm),
        ExCaseBranch.branch(
            ExVarPattern.var("element"), ExTuple.tuple(ExAtom.atom(tag), valueExpr)));
  }

  private static ExExpr decodeUnionMemberValue(
      Model model, MemberShape member, String elementVar, SymbolProvider sp, String typesMod) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof StructureShape structure) {
      return decodeStructureExpr(model, structure, elementVar, sp, typesMod);
    }
    if (target instanceof ListShape listShape) {
      String itemElement = BeamXmlBindingIndex.listItemElementName(member, listShape, model);
      Shape listMember = model.expectShape(listShape.getMember().getTarget());
      if (listMember instanceof StructureShape nested) {
        return ExCallLocal.callLocal(
            "xml_child_struct_list",
            ExVar.var(elementVar),
            ExAtom.atom("nil"),
            ExString.string(itemElement),
            ExAnonymousFn.fn(
                ExClause.inlineClause(
                    List.of(ExVarPattern.var("item")),
                    decodeStructureExpr(model, nested, "item", sp, typesMod))));
      }
      return ExCallLocal.callLocal(
          "xml_child_list",
          ExVar.var(elementVar),
          ExAtom.atom("nil"),
          ExString.string(itemElement));
    }
    return ExCase.caseExpr(
        ExCallLocal.callLocal("element_text", ExVar.var(elementVar)),
        ExCaseBranch.branch(ExListPattern.list(), ExAtom.atom("nil")),
        ExCaseBranch.branch(ExVarPattern.var("text"), ExVar.var("text")));
  }

  private static ExMap buildStructureMap(
      Model model, StructureShape structure, String varName, SymbolProvider sp) {
    List<ExMapEntry> entries = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      if (BeamXmlBindingIndex.isXmlAttribute(member)) {
        continue;
      }
      String field = fieldName(sp, member);
      String wireName = BeamXmlBindingIndex.memberElementName(member);
      entries.add(
          ExMapEntry.entry(
              ExString.string(wireName), ExStructAccess.structAccess(ExVar.var(varName), field)));
    }
    return ExMap.map(entries.toArray(ExMapEntry[]::new));
  }

  private static ExExpr buildPathExpression(
      String uriTemplate, List<HttpBinding> labels, SymbolProvider sp, String inputVar) {
    if (labels.isEmpty()) {
      return ExString.string(uriTemplate);
    }
    Map<String, HttpBinding> byLocation = new HashMap<>();
    for (HttpBinding lb : labels) {
      byLocation.put(lb.getLocationName(), lb);
    }
    ExExpr expr = null;
    int pos = 0;
    while (pos < uriTemplate.length()) {
      int start = uriTemplate.indexOf('{', pos);
      if (start < 0) {
        expr = appendPathSegment(expr, ExString.string(uriTemplate.substring(pos)));
        break;
      }
      if (start > pos) {
        expr = appendPathSegment(expr, ExString.string(uriTemplate.substring(pos, start)));
      }
      int end = uriTemplate.indexOf('}', start);
      String labelName = uriTemplate.substring(start + 1, end);
      HttpBinding lb = byLocation.get(labelName);
      if (lb != null) {
        String field = fieldName(sp, lb.getMember());
        expr =
            appendPathSegment(
                expr,
                ExCall.call(
                    "URI",
                    "encode",
                    ExCall.call(
                        "Kernel",
                        "to_string",
                        ExStructAccess.structAccess(ExVar.var(inputVar), field))));
      } else {
        expr = appendPathSegment(expr, ExString.string("{" + labelName + "}"));
      }
      pos = end + 1;
    }
    return expr;
  }

  private static ExExpr appendPathSegment(ExExpr current, ExExpr segment) {
    return current == null ? segment : ExOp.op("<>", current, segment);
  }

  private static String structName(SymbolProvider sp, StructureShape shape) {
    return sp.toSymbol(shape).getName();
  }

  private static String fieldName(SymbolProvider sp, MemberShape member) {
    Symbol sym = sp.toSymbol(member);
    return sym.getProperty("fieldName", String.class)
        .orElseGet(() -> BeamNameUtils.toSnakeCase(member.getMemberName()));
  }

  private static String unionTagForMember(SymbolProvider sp, MemberShape member) {
    return sp.toSymbol(member).getProperty("unionTag", String.class).orElseThrow();
  }
}
