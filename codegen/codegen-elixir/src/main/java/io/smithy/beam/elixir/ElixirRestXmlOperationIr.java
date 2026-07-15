package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AnonFun;
import io.beam.dsl.elixir.AnonFunClause;
import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.AtomPattern;
import io.beam.dsl.elixir.BlockExpr;
import io.beam.dsl.elixir.CaseExpr;
import io.beam.dsl.elixir.Clause;
import io.beam.dsl.elixir.DotCallExpr;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.IfExpr;
import io.beam.dsl.elixir.InfixExpr;
import io.beam.dsl.elixir.IntegerExpr;
import io.beam.dsl.elixir.IntegerPattern;
import io.beam.dsl.elixir.ListExpr;
import io.beam.dsl.elixir.ListPattern;
import io.beam.dsl.elixir.LocalCallExpr;
import io.beam.dsl.elixir.MapEntry;
import io.beam.dsl.elixir.MapExpr;
import io.beam.dsl.elixir.MapPattern;
import io.beam.dsl.elixir.MapPatternEntry;
import io.beam.dsl.elixir.MatchExpr;
import io.beam.dsl.elixir.NilExpr;
import io.beam.dsl.elixir.NilPattern;
import io.beam.dsl.elixir.Pattern;
import io.beam.dsl.elixir.PipeExpr;
import io.beam.dsl.elixir.PipeStep;
import io.beam.dsl.elixir.RemoteCallExpr;
import io.beam.dsl.elixir.Spec;
import io.beam.dsl.elixir.StringExpr;
import io.beam.dsl.elixir.StructExpr;
import io.beam.dsl.elixir.StructField;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.beam.dsl.elixir.WildcardPattern;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamS3CustomizationIndex;
import io.smithy.beam.core.BeamXmlBindingIndex;
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
  private static final Pattern W = WildcardPattern.of();
  private static final String DEFAULT_CONTENT_TYPE = "application/xml";

  private ElixirRestXmlOperationIr() {}

  static List<Function> buildDecodeRequest(
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

    MapPattern bodyPattern =
        MapPattern.of(List.of(MapPatternEntry.of(AtomExpr.of("body"), VariablePattern.of("body"))));
    List<Pattern> patterns =
        labels.isEmpty()
            ? List.of(bodyPattern)
            : List.of(VariablePattern.of("labels"), bodyPattern);

    List<StructField> structFields = new ArrayList<>();
    for (HttpBinding lb : labels) {
      String field = fieldName(sp, lb.getMember());
      structFields.add(
          StructField.of(
              field,
              RemoteCallExpr.of(
                  "Map",
                  "get",
                  List.of(Variable.of("labels"), StringExpr.of(lb.getLocationName())))));
    }
    if (!payloadMembers.isEmpty()) {
      HttpBinding payload = payloadMembers.get(0);
      MemberShape member = payload.getMember();
      Shape target = model.expectShape(member.getTarget());
      String field = fieldName(sp, member);
      Expression value =
          target instanceof BlobShape || target instanceof StringShape
              ? Variable.of("body")
              : payloadBodyDecodeExpr(
                  model,
                  target,
                  BeamXmlBindingIndex.payloadRootElementName(member, target),
                  sp,
                  typesMod);
      structFields.add(StructField.of(field, value));
    }

    Expression body =
        TupleExpr.of(
            List.of(AtomExpr.of("ok"), StructExpr.of("Types." + inputStruct, structFields)));

    Spec spec =
        labels.isEmpty()
            ? Spec.of(
                "decode_"
                    + opName
                    + "_request(map()) :: {:ok, "
                    + inputType
                    + "} | {:error, term()}")
            : Spec.of(
                "decode_"
                    + opName
                    + "_request(map(), map()) :: {:ok, "
                    + inputType
                    + "} | {:error, term()}");

    return List.of(
        new Function(
            "decode_" + opName + "_request",
            false,
            List.of(FunctionHead.of(patterns)),
            body,
            spec,
            null,
            false));
  }

  static List<Function> buildDecodeResponse(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));
    int successCode = httpIndex.getResponseCode(op);

    MapPattern successPattern =
        MapPattern.of(
            List.of(
                MapPatternEntry.of(AtomExpr.of("status"), IntegerPattern.of(successCode)),
                MapPatternEntry.of(AtomExpr.of("headers"), VariablePattern.of("headers")),
                MapPatternEntry.of(AtomExpr.of("body"), VariablePattern.of("body"))));

    List<Function> functions = new ArrayList<>();
    functions.add(
        new Function(
            "decode_" + opName + "_response",
            false,
            List.of(FunctionHead.of(List.of(successPattern))),
            block(buildDecodeResponseSuccessBody(model, op, httpIndex, sp, typesMod, output)),
            Spec.of(
                "decode_"
                    + opName
                    + "_response(map()) :: {:ok, "
                    + outputType
                    + "} | {:error, term()}"),
            null,
            false));

    MapPattern fallbackPattern =
        MapPattern.of(
            List.of(
                MapPatternEntry.of(AtomExpr.of("status"), VariablePattern.of("status")),
                MapPatternEntry.of(AtomExpr.of("headers"), VariablePattern.of("headers")),
                MapPatternEntry.of(AtomExpr.of("body"), VariablePattern.of("body"))));

    if (op.getErrors().isEmpty()) {
      functions.add(
          new Function(
              "decode_" + opName + "_response",
              false,
              List.of(FunctionHead.of(List.of(fallbackPattern))),
              TupleExpr.of(
                  List.of(
                      AtomExpr.of("error"),
                      TupleExpr.of(
                          List.of(
                              AtomExpr.of("unknown_error"),
                              Variable.of("status"),
                              Variable.of("body"))))),
              null,
              null,
              true));
    } else {
      functions.add(
          new Function(
              "decode_" + opName + "_response",
              false,
              List.of(FunctionHead.of(List.of(fallbackPattern))),
              LocalCallExpr.of(
                  "decode_" + opName + "_response_error",
                  List.of(Variable.of("status"), Variable.of("headers"), Variable.of("body"))),
              null,
              null,
              true));
    }
    return functions;
  }

  static List<Function> buildErrorDispatch(
      Model model, OperationShape op, SymbolProvider sp, String typesMod) {
    String opName = sp.toSymbol(op).getName();
    List<ShapeId> errors = new ArrayList<>(op.getErrors());
    if (errors.isEmpty()) {
      return List.of();
    }

    Spec spec =
        Spec.of(
            "decode_" + opName + "_response_error(integer(), map(), term()) :: {:error, term()}");

    List<Function> functions = new ArrayList<>();
    boolean first = true;
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
      List<MapEntry> nilFields = new ArrayList<>();
      for (MemberShape member : errShape.members()) {
        if (member.getMemberName().equals("__beam_error_kind")) {
          continue;
        }
        nilFields.add(MapEntry.atomKey(fieldName(sp, member), AtomExpr.of("nil")));
      }
      functions.add(
          new Function(
              "decode_" + opName + "_response_error",
              true,
              List.of(
                  FunctionHead.of(
                      List.of(
                          IntegerPattern.of(httpStatus),
                          VariablePattern.of("_headers"),
                          VariablePattern.of("_body")))),
              TupleExpr.of(
                  List.of(
                      AtomExpr.of("error"),
                      LocalCallExpr.of(
                          "struct!",
                          List.of(Variable.of(typesMod + "." + modName), MapExpr.of(nilFields))))),
              first ? spec : null,
              null,
              true));
      first = false;
    }

    functions.add(
        defp(
            "decode_" + opName + "_response_error",
            List.of(
                VariablePattern.of("status"),
                VariablePattern.of("_headers"),
                VariablePattern.of("body")),
            TupleExpr.of(
                List.of(
                    AtomExpr.of("error"),
                    TupleExpr.of(
                        List.of(
                            AtomExpr.of("unknown_error"),
                            Variable.of("status"),
                            Variable.of("body"))))),
            true));

    return functions;
  }

  static List<Function> buildEncodeRequest(
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

    List<Pattern> patterns =
        encodeWithConfig
            ? List.of(VariablePattern.of("config"), VariablePattern.of("input"))
            : List.of(VariablePattern.of("input"));

    List<Expression> body = new ArrayList<>();
    body.addAll(buildIdempotencyTokenExprs(input, sp));

    BeamS3CustomizationIndex s3Index = BeamS3CustomizationIndex.of(model);
    boolean s3BucketAddressing =
        s3Index.isS3Service(service) && s3Index.bucketLabelBinding(op).isPresent();
    boolean setHost = s3BucketAddressing;

    if (s3BucketAddressing) {
      String bucketField = fieldName(sp, s3Index.bucketLabelBinding(op).orElseThrow().getMember());
      Expression keyExpr =
          s3Index
              .keyLabelBinding(op)
              .<Expression>map(
                  binding ->
                      RemoteCallExpr.of(
                          "Kernel",
                          "to_string",
                          List.of(
                              new DotCallExpr(
                                  Variable.of("input"),
                                  fieldName(sp, binding.getMember()),
                                  List.of()))))
              .orElse(StringExpr.of(""));
      List<Expression> resolveArgs = new ArrayList<>();
      if (encodeWithConfig) {
        resolveArgs.add(Variable.of("config"));
      } else {
        resolveArgs.add(MapExpr.of(List.of()));
      }
      resolveArgs.add(
          RemoteCallExpr.of(
              "Kernel",
              "to_string",
              List.of(new DotCallExpr(Variable.of("input"), bucketField, List.of()))));
      resolveArgs.add(keyExpr);
      body.add(
          MatchExpr.bind(
              TuplePattern.of(List.of(VariablePattern.of("host"), VariablePattern.of("path"))),
              RemoteCallExpr.of("S3Endpoint", "resolve_bucket_url", resolveArgs)));
    } else {
      body.add(
          MatchExpr.bind(
              "path", buildPathExpression(httpTrait.getUri().toString(), labels, sp, "input")));
    }

    body.addAll(buildQueryExprs(queries, sp));
    body.addAll(buildRequestHeadersExprs(model, headers, sp, "input"));
    body.addAll(buildRequestBodyExprs(model, payloadMembers, httpTrait.getMethod(), sp, typesMod));
    ElixirHttpChecksumIr.requestChecksumHeadersExpr(model, op, sp, "headers").ifPresent(body::add);

    List<StructField> requestFields = new ArrayList<>();
    requestFields.add(StructField.of("method", StringExpr.of(httpTrait.getMethod())));
    requestFields.add(StructField.of("path", Variable.of("path")));
    requestFields.add(StructField.of("query", Variable.of("query")));
    requestFields.add(StructField.of("headers", Variable.of("headers")));
    requestFields.add(StructField.of("body", Variable.of("body")));
    if (setHost) {
      requestFields.add(StructField.of("host", Variable.of("host")));
    }
    body.add(StructExpr.of(runtimeMod + ".HttpRequest", requestFields));

    Spec spec =
        encodeWithConfig
            ? Spec.of(
                "encode_" + opName + "_request(map(), " + inputType + ") :: " + httpRequestType)
            : Spec.of("encode_" + opName + "_request(" + inputType + ") :: " + httpRequestType);

    return List.of(
        new Function(
            "encode_" + opName + "_request",
            false,
            List.of(FunctionHead.of(patterns)),
            block(body),
            spec,
            null,
            false));
  }

  static List<Function> buildEncodeResponse(
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

    Spec spec = Spec.of("encode_" + opName + "_response(" + outputType + ") :: map()");

    return List.of(
        new Function(
            "encode_" + opName + "_response",
            false,
            List.of(FunctionHead.of(List.of(VariablePattern.of("output")))),
            block(
                buildEncodeResponseBodyExprs(
                    model,
                    output,
                    op,
                    httpIndex,
                    respPayload,
                    successCode,
                    sp,
                    typesMod,
                    runtimeMod)),
            spec,
            null,
            false));
  }

  private static List<Expression> buildDecodeResponseSuccessBody(
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

    List<Expression> body = new ArrayList<>();
    for (HttpBinding hb : respHeaders) {
      body.add(headerBindingDecodeExpr(model, sp, hb));
    }
    for (HttpBinding ph : respPrefixHeaders) {
      String field = fieldName(sp, ph.getMember());
      body.add(
          MatchExpr.bind(
              field,
              LocalCallExpr.of(
                  "prefix_headers_from_list",
                  List.of(Variable.of("headers"), StringExpr.of(ph.getLocationName())))));
    }

    if (!respPayload.isEmpty()) {
      HttpBinding pb = respPayload.get(0);
      MemberShape member = pb.getMember();
      Shape target = model.expectShape(member.getTarget());
      String field = fieldName(sp, member);
      if (target instanceof BlobShape || target instanceof StringShape) {
        body.add(MatchExpr.bind(field, Variable.of("body")));
      } else {
        String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
        body.add(
            MatchExpr.bind(field, payloadBodyDecodeExpr(model, target, rootElement, sp, typesMod)));
      }
    } else if (!xmlBodyMembers.isEmpty()) {
      String rootElement = BeamXmlBindingIndex.shapeElementName(output);
      body.add(
          MatchExpr.bind(
              "parsed",
              new CaseExpr(
                  LocalCallExpr.of(
                      "parse_xml_root", List.of(Variable.of("body"), StringExpr.of(rootElement))),
                  List.of(
                      Clause.of(
                          TuplePattern.of(
                              List.of(AtomPattern.of("ok"), VariablePattern.of("root"))),
                          Variable.of("root")),
                      Clause.of(
                          TuplePattern.of(List.of(AtomPattern.of("error"), W)),
                          AtomExpr.of("nil"))))));
      body.addAll(buildMembersFromXmlExprs(model, xmlBodyMembers, "parsed", sp, typesMod));
    }

    List<StructField> structFields = new ArrayList<>();
    Set<String> boundFields = new LinkedHashSet<>();
    for (HttpBinding hb : concat(respHeaders, respPrefixHeaders, respPayload)) {
      String field = fieldName(sp, hb.getMember());
      if (boundFields.add(field)) {
        structFields.add(StructField.of(field, Variable.of(field)));
      }
    }
    if (respPayload.isEmpty() && !xmlBodyMembers.isEmpty()) {
      for (MemberShape member : xmlBodyMembers) {
        String field = fieldName(sp, member);
        if (boundFields.add(field)) {
          structFields.add(StructField.of(field, Variable.of(field)));
        }
      }
    }

    Expression success =
        TupleExpr.of(
            List.of(
                AtomExpr.of("ok"), StructExpr.of("Types." + structName(sp, output), structFields)));
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

  private static MatchExpr headerBindingDecodeExpr(
      Model model, SymbolProvider sp, HttpBinding binding) {
    String field = fieldName(sp, binding.getMember());
    Expression headerValue =
        LocalCallExpr.of(
            "header_value",
            List.of(Variable.of("headers"), StringExpr.of(binding.getLocationName())));
    Shape target = model.expectShape(binding.getMember().getTarget());
    Expression value = headerValue;
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = BeamNameUtils.toSnakeCase(target.getId().getName());
      value = LocalCallExpr.of("decode_" + helperName, List.of(headerValue));
    }
    return MatchExpr.bind(field, value);
  }

  private static List<Expression> buildMembersFromXmlExprs(
      Model model,
      Iterable<MemberShape> members,
      String xmlVar,
      SymbolProvider sp,
      String typesMod) {
    List<Expression> exprs = new ArrayList<>();
    for (MemberShape member : members) {
      exprs.add(buildMemberFromXmlExpr(model, member, xmlVar, sp, typesMod));
    }
    return exprs;
  }

  private static MatchExpr buildMemberFromXmlExpr(
      Model model, MemberShape member, String xmlVar, SymbolProvider sp, String typesMod) {
    String field = fieldName(sp, member);
    Shape target = model.expectShape(member.getTarget());
    if (BeamXmlBindingIndex.isXmlAttribute(member)) {
      return MatchExpr.bind(
          field,
          new CaseExpr(
              Variable.of(xmlVar),
              List.of(
                  Clause.of(NilPattern.of(), AtomExpr.of("nil")),
                  Clause.of(
                      W,
                      LocalCallExpr.of(
                          "xml_attribute",
                          List.of(
                              Variable.of(xmlVar),
                              StringExpr.of(BeamXmlBindingIndex.memberElementName(member))))))));
    }
    if (target instanceof ListShape listShape) {
      return MatchExpr.bind(
          field,
          new CaseExpr(
              Variable.of(xmlVar),
              List.of(
                  Clause.of(NilPattern.of(), AtomExpr.of("nil")),
                  Clause.of(
                      W, decodeListFieldFromXml(model, member, listShape, xmlVar, sp, typesMod)))));
    }
    if (target instanceof StructureShape nested) {
      String element = BeamXmlBindingIndex.memberElementName(member);
      String nestedVar = field + "_xml";
      return MatchExpr.bind(
          field,
          new CaseExpr(
              Variable.of(xmlVar),
              List.of(
                  Clause.of(NilPattern.of(), AtomExpr.of("nil")),
                  Clause.of(
                      W,
                      new CaseExpr(
                          LocalCallExpr.of(
                              "find_element",
                              List.of(
                                  StringExpr.of(element),
                                  LocalCallExpr.of(
                                      "element_content", List.of(Variable.of(xmlVar))))),
                          List.of(
                              Clause.of(NilPattern.of(), AtomExpr.of("nil")),
                              Clause.of(
                                  VariablePattern.of(nestedVar),
                                  decodeStructureExpr(
                                      model, nested, nestedVar, sp, typesMod))))))));
    }
    return MatchExpr.bind(
        field,
        new CaseExpr(
            Variable.of(xmlVar),
            List.of(
                Clause.of(NilPattern.of(), AtomExpr.of("nil")),
                Clause.of(
                    W,
                    LocalCallExpr.of(
                        "xml_child_text",
                        List.of(
                            Variable.of(xmlVar),
                            StringExpr.of(BeamXmlBindingIndex.memberElementName(member))))))));
  }

  private static List<Expression> buildEncodeResponseBodyExprs(
      Model model,
      StructureShape output,
      OperationShape op,
      HttpBindingIndex httpIndex,
      List<HttpBinding> respPayload,
      int successCode,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod) {
    List<Expression> body = new ArrayList<>();
    body.add(
        MatchExpr.bind(
            "headers",
            ListExpr.of(
                List.of(
                    TupleExpr.of(
                        List.of(
                            StringExpr.of("Content-Type"),
                            StringExpr.of(DEFAULT_CONTENT_TYPE)))))));

    if (!respPayload.isEmpty()) {
      HttpBinding pb = respPayload.get(0);
      MemberShape member = pb.getMember();
      Shape target = model.expectShape(member.getTarget());
      String field = fieldName(sp, member);
      if (target instanceof BlobShape || target instanceof StringShape) {
        body.add(
            MatchExpr.bind(
                "body",
                InfixExpr.of(
                    RemoteCallExpr.of(
                        "Map", "get", List.of(Variable.of("output"), AtomExpr.of(field))),
                    "||",
                    StringExpr.of(""))));
      } else {
        String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
        body.addAll(
            buildPayloadEncodeBodyExprs(model, target, rootElement, field, sp, typesMod, "output"));
      }
    } else if (!output.members().isEmpty()) {
      String rootElement = BeamXmlBindingIndex.shapeElementName(output);
      List<MapEntry> entries = new ArrayList<>();
      for (MemberShape member : output.members()) {
        String wireName = BeamXmlBindingIndex.memberElementName(member);
        entries.add(
            MapEntry.stringKey(
                wireName,
                new DotCallExpr(Variable.of("output"), fieldName(sp, member), List.of())));
      }
      body.add(MatchExpr.bind("member_map", MapExpr.of(entries)));
      body.add(
          MatchExpr.bind(
              "body",
              LocalCallExpr.of(
                  "encode_xml",
                  List.of(
                      MapExpr.of(
                          List.of(MapEntry.stringKey(rootElement, Variable.of("member_map")))),
                      LocalCallExpr.of("xml_namespace", List.of())))));
    } else {
      body.add(MatchExpr.bind("body", StringExpr.of("")));
    }

    body.add(
        StructExpr.of(
            runtimeMod + ".HttpResponse",
            List.of(
                StructField.of("status", IntegerExpr.of(successCode)),
                StructField.of("headers", Variable.of("headers")),
                StructField.of("body", Variable.of("body")))));
    return body;
  }

  private static List<Expression> buildPayloadEncodeBodyExprs(
      Model model,
      Shape target,
      String rootElement,
      String field,
      SymbolProvider sp,
      String typesMod,
      String valueVar) {
    List<Expression> exprs = new ArrayList<>();
    Expression payloadCaseBody;
    if (target instanceof StructureShape structure) {
      payloadCaseBody =
          new BlockExpr(
              List.of(
                  MatchExpr.bind(
                      "member_map", buildStructureMap(model, structure, "payload_value", sp)),
                  LocalCallExpr.of(
                      "encode_xml",
                      List.of(
                          MapExpr.of(
                              List.of(MapEntry.stringKey(rootElement, Variable.of("member_map")))),
                          LocalCallExpr.of("xml_namespace", List.of())))));
    } else if (target instanceof UnionShape union) {
      payloadCaseBody = buildUnionPayloadEncodeExpr(model, union, rootElement, sp, typesMod);
    } else {
      payloadCaseBody =
          LocalCallExpr.of(
              "encode_xml",
              List.of(
                  MapExpr.of(
                      List.of(MapEntry.stringKey(rootElement, Variable.of("payload_value")))),
                  LocalCallExpr.of("xml_namespace", List.of())));
    }

    exprs.add(
        MatchExpr.bind(
            "body",
            new CaseExpr(
                new DotCallExpr(Variable.of(valueVar), field, List.of()),
                List.of(
                    Clause.of(NilPattern.of(), StringExpr.of("")),
                    Clause.of(VariablePattern.of("payload_value"), payloadCaseBody)))));
    return exprs;
  }

  private static Expression buildUnionPayloadEncodeExpr(
      Model model, UnionShape union, String rootElement, SymbolProvider sp, String typesMod) {
    List<Clause> branches = new ArrayList<>();
    for (MemberShape member : union.members()) {
      String element = BeamXmlBindingIndex.memberElementName(member);
      String tag = unionTagForMember(sp, member);
      Shape memberTarget = model.expectShape(member.getTarget());
      Expression innerValue;
      List<Expression> blockExprs = new ArrayList<>();
      if (memberTarget instanceof StructureShape structure) {
        blockExprs.add(MatchExpr.bind("inner_map", buildStructureMap(model, structure, "v", sp)));
        innerValue = Variable.of("inner_map");
      } else {
        innerValue = Variable.of("v");
      }
      blockExprs.add(
          LocalCallExpr.of(
              "encode_xml",
              List.of(
                  MapExpr.of(
                      List.of(
                          MapEntry.stringKey(
                              rootElement,
                              MapExpr.of(List.of(MapEntry.stringKey(element, innerValue)))))),
                  LocalCallExpr.of("xml_namespace", List.of()))));
      branches.add(
          Clause.of(
              TuplePattern.of(List.of(AtomPattern.of(tag), VariablePattern.of("v"))),
              blockExprs.size() == 1 ? blockExprs.get(0) : new BlockExpr(blockExprs)));
    }
    branches.add(Clause.of(NilPattern.of(), StringExpr.of("")));
    return new CaseExpr(Variable.of("payload_value"), branches);
  }

  private static List<Expression> buildRequestBodyExprs(
      Model model,
      List<HttpBinding> payloadMembers,
      String method,
      SymbolProvider sp,
      String typesMod) {
    List<Expression> exprs = new ArrayList<>();
    if (payloadMembers.isEmpty()
        || method.equals("GET")
        || method.equals("DELETE")
        || method.equals("HEAD")) {
      exprs.add(MatchExpr.bind("body", StringExpr.of("")));
      return exprs;
    }

    HttpBinding payload = payloadMembers.get(0);
    MemberShape member = payload.getMember();
    Shape target = model.expectShape(member.getTarget());
    String field = fieldName(sp, member);

    if (target instanceof BlobShape || target instanceof StringShape) {
      exprs.add(
          MatchExpr.bind(
              "body",
              new CaseExpr(
                  new DotCallExpr(Variable.of("input"), field, List.of()),
                  List.of(
                      Clause.of(NilPattern.of(), StringExpr.of("")),
                      Clause.of(VariablePattern.of("value"), Variable.of("value"))))));
      return exprs;
    }

    String rootElement = BeamXmlBindingIndex.payloadRootElementName(member, target);
    Expression payloadCaseBody;
    if (target instanceof StructureShape structure) {
      payloadCaseBody =
          new BlockExpr(
              List.of(
                  MatchExpr.bind(
                      "member_map", buildStructureMap(model, structure, "payload_value", sp)),
                  LocalCallExpr.of(
                      "encode_xml",
                      List.of(
                          MapExpr.of(
                              List.of(MapEntry.stringKey(rootElement, Variable.of("member_map")))),
                          LocalCallExpr.of("xml_namespace", List.of())))));
    } else if (target instanceof UnionShape union) {
      payloadCaseBody = buildUnionPayloadEncodeExpr(model, union, rootElement, sp, typesMod);
    } else {
      payloadCaseBody =
          LocalCallExpr.of(
              "encode_xml",
              List.of(
                  MapExpr.of(
                      List.of(MapEntry.stringKey(rootElement, Variable.of("payload_value")))),
                  LocalCallExpr.of("xml_namespace", List.of())));
    }

    exprs.add(
        MatchExpr.bind(
            "body",
            new CaseExpr(
                new DotCallExpr(Variable.of("input"), field, List.of()),
                List.of(
                    Clause.of(NilPattern.of(), StringExpr.of("")),
                    Clause.of(VariablePattern.of("payload_value"), payloadCaseBody)))));
    return exprs;
  }

  private static List<Expression> buildQueryExprs(List<HttpBinding> queries, SymbolProvider sp) {
    if (queries.isEmpty()) {
      return List.of(MatchExpr.bind("query", MapExpr.of(List.of())));
    }
    List<MapEntry> entries = new ArrayList<>();
    for (HttpBinding qb : queries) {
      String field = fieldName(sp, qb.getMember());
      entries.add(
          MapEntry.stringKey(
              qb.getLocationName(), new DotCallExpr(Variable.of("input"), field, List.of())));
    }
    return List.of(
        MatchExpr.bind("query", ElixirJsonCodecIr.rejectNilMapPipeline("query", entries)));
  }

  private static List<Expression> buildRequestHeadersExprs(
      Model model, List<HttpBinding> headers, SymbolProvider sp, String recordVar) {
    if (headers.isEmpty()) {
      return List.of(
          MatchExpr.bind(
              "headers",
              ListExpr.of(
                  List.of(
                      TupleExpr.of(
                          List.of(
                              StringExpr.of("Content-Type"),
                              StringExpr.of(DEFAULT_CONTENT_TYPE)))))));
    }
    List<Expression> exprs = new ArrayList<>();
    exprs.addAll(extraHeadersPipeline(model, sp, recordVar, headers));
    exprs.add(
        MatchExpr.bind(
            "headers",
            RemoteCallExpr.of(
                "Enum",
                "concat",
                List.of(
                    ListExpr.of(
                        List.of(
                            TupleExpr.of(
                                List.of(
                                    StringExpr.of("Content-Type"),
                                    StringExpr.of(DEFAULT_CONTENT_TYPE))))),
                    Variable.of("extra_headers")))));
    return exprs;
  }

  private static Expression encodeBindingWireValueExpr(
      Model model,
      SymbolProvider sp,
      MemberShape member,
      Expression valueExpr,
      boolean queryValues) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = BeamNameUtils.toSnakeCase(target.getId().getName());
      return LocalCallExpr.of("encode_" + helperName, List.of(valueExpr));
    }
    if (queryValues) {
      return LocalCallExpr.of("encode_query_value", List.of(valueExpr));
    }
    return RemoteCallExpr.of("Kernel", "to_string", List.of(valueExpr));
  }

  private static List<Expression> extraHeadersPipeline(
      Model model, SymbolProvider sp, String recordVar, List<HttpBinding> headers) {
    List<Expression> entries = new ArrayList<>();
    for (HttpBinding hb : headers) {
      String field = fieldName(sp, hb.getMember());
      Expression fieldValue = new DotCallExpr(Variable.of(recordVar), field, List.of());
      entries.add(
          new IfExpr(
              InfixExpr.of(fieldValue, "!=", AtomExpr.of("nil")),
              TupleExpr.of(
                  List.of(
                      StringExpr.of(hb.getLocationName()),
                      encodeBindingWireValueExpr(model, sp, hb.getMember(), fieldValue, false))),
              NilExpr.of(),
              false));
    }
    return List.of(
        MatchExpr.bind(
            "extra_headers",
            new PipeExpr(
                ListExpr.of(entries),
                List.of(
                    new PipeStep(
                        RemoteCallExpr.of(
                            "Enum",
                            "reject",
                            List.of(
                                new AnonFun(
                                    List.of(
                                        AnonFunClause.of(
                                            List.of(VariablePattern.of("x")),
                                            LocalCallExpr.of(
                                                "is_nil", List.of(Variable.of("x")))))))),
                        List.of())))));
  }

  private static List<Expression> buildIdempotencyTokenExprs(
      StructureShape input, SymbolProvider sp) {
    List<Expression> exprs = new ArrayList<>();
    for (MemberShape member : input.members()) {
      if (!member.hasTrait(IdempotencyTokenTrait.class)) {
        continue;
      }
      String field = fieldName(sp, member);
      exprs.add(
          MatchExpr.bind(
              "input",
              new CaseExpr(
                  new DotCallExpr(Variable.of("input"), field, List.of()),
                  List.of(
                      Clause.of(
                          NilPattern.of(),
                          RemoteCallExpr.of(
                              "Map",
                              "put",
                              List.of(
                                  Variable.of("input"),
                                  AtomExpr.of(field),
                                  LocalCallExpr.of("generate_uuid", List.of())))),
                      Clause.of(W, Variable.of("input"))))));
    }
    return exprs;
  }

  private static Expression payloadBodyDecodeExpr(
      Model model, Shape target, String rootElement, SymbolProvider sp, String typesMod) {
    return new CaseExpr(
        LocalCallExpr.of(
            "parse_xml_root", List.of(Variable.of("body"), StringExpr.of(rootElement))),
        List.of(
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("root"))),
                payloadDecodeExpr(model, target, "root", sp, typesMod)),
            Clause.of(TuplePattern.of(List.of(AtomPattern.of("error"), W)), AtomExpr.of("nil"))));
  }

  private static Expression payloadDecodeExpr(
      Model model, Shape target, String xmlVar, SymbolProvider sp, String typesMod) {
    if (target instanceof StructureShape structure) {
      return decodeStructureExpr(model, structure, xmlVar, sp, typesMod);
    }
    if (target instanceof UnionShape union) {
      return decodeUnionFromXml(model, union, xmlVar, sp, typesMod);
    }
    return LocalCallExpr.of("xml_child_text", List.of(Variable.of(xmlVar), StringExpr.of("")));
  }

  private static StructExpr decodeStructureExpr(
      Model model, StructureShape structure, String xmlVar, SymbolProvider sp, String typesMod) {
    List<StructField> fields = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      if (BeamXmlBindingIndex.isXmlAttribute(member)) {
        continue;
      }
      String field = fieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      Expression value;
      if (target instanceof ListShape listShape) {
        value = decodeListFieldFromXml(model, member, listShape, xmlVar, sp, typesMod);
      } else {
        value =
            LocalCallExpr.of(
                "xml_child_text",
                List.of(
                    Variable.of(xmlVar),
                    StringExpr.of(BeamXmlBindingIndex.memberElementName(member))));
      }
      fields.add(StructField.of(field, value));
    }
    return StructExpr.of("Types." + structName(sp, structure), fields);
  }

  private static Expression decodeListFieldFromXml(
      Model model,
      MemberShape member,
      ListShape listShape,
      String xmlVar,
      SymbolProvider sp,
      String typesMod) {
    String element = BeamXmlBindingIndex.memberElementName(member);
    String itemElement = BeamXmlBindingIndex.listItemElementName(member, listShape, model);
    Expression listNameArg =
        BeamXmlBindingIndex.isContainerMemberFlattened(member)
            ? AtomExpr.of("nil")
            : StringExpr.of(element);
    Shape listMember = model.expectShape(listShape.getMember().getTarget());
    if (listMember instanceof StructureShape nested) {
      return LocalCallExpr.of(
          "xml_child_struct_list",
          List.of(
              Variable.of(xmlVar),
              listNameArg,
              StringExpr.of(itemElement),
              new AnonFun(
                  List.of(
                      AnonFunClause.of(
                          List.of(VariablePattern.of("item")),
                          decodeStructureExpr(model, nested, "item", sp, typesMod))))));
    }
    return LocalCallExpr.of(
        "xml_child_list", List.of(Variable.of(xmlVar), listNameArg, StringExpr.of(itemElement)));
  }

  private static Expression decodeUnionFromXml(
      Model model, UnionShape union, String xmlVar, SymbolProvider sp, String typesMod) {
    return buildUnionDecodeCase(model, union, xmlVar, sp, typesMod, 0);
  }

  private static Expression buildUnionDecodeCase(
      Model model,
      UnionShape union,
      String xmlVar,
      SymbolProvider sp,
      String typesMod,
      int memberIndex) {
    List<MemberShape> members = new ArrayList<>(union.members());
    if (memberIndex >= members.size()) {
      return AtomExpr.of("nil");
    }
    MemberShape member = members.get(memberIndex);
    String element = BeamXmlBindingIndex.memberElementName(member);
    String tag = unionTagForMember(sp, member);
    Expression valueExpr = decodeUnionMemberValue(model, member, "element", sp, typesMod);
    Expression nextArm = buildUnionDecodeCase(model, union, xmlVar, sp, typesMod, memberIndex + 1);
    return new CaseExpr(
        LocalCallExpr.of(
            "find_element",
            List.of(
                StringExpr.of(element),
                LocalCallExpr.of("element_content", List.of(Variable.of(xmlVar))))),
        List.of(
            Clause.of(NilPattern.of(), nextArm),
            Clause.of(
                VariablePattern.of("element"),
                TupleExpr.of(List.of(AtomExpr.of(tag), valueExpr)))));
  }

  private static Expression decodeUnionMemberValue(
      Model model, MemberShape member, String elementVar, SymbolProvider sp, String typesMod) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof StructureShape structure) {
      return decodeStructureExpr(model, structure, elementVar, sp, typesMod);
    }
    if (target instanceof ListShape listShape) {
      String itemElement = BeamXmlBindingIndex.listItemElementName(member, listShape, model);
      Shape listMember = model.expectShape(listShape.getMember().getTarget());
      if (listMember instanceof StructureShape nested) {
        return LocalCallExpr.of(
            "xml_child_struct_list",
            List.of(
                Variable.of(elementVar),
                AtomExpr.of("nil"),
                StringExpr.of(itemElement),
                new AnonFun(
                    List.of(
                        AnonFunClause.of(
                            List.of(VariablePattern.of("item")),
                            decodeStructureExpr(model, nested, "item", sp, typesMod))))));
      }
      return LocalCallExpr.of(
          "xml_child_list",
          List.of(Variable.of(elementVar), AtomExpr.of("nil"), StringExpr.of(itemElement)));
    }
    return new CaseExpr(
        LocalCallExpr.of("element_text", List.of(Variable.of(elementVar))),
        List.of(
            Clause.of(ListPattern.of(List.of()), AtomExpr.of("nil")),
            Clause.of(VariablePattern.of("text"), Variable.of("text"))));
  }

  private static MapExpr buildStructureMap(
      Model model, StructureShape structure, String varName, SymbolProvider sp) {
    List<MapEntry> entries = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      if (BeamXmlBindingIndex.isXmlAttribute(member)) {
        continue;
      }
      String field = fieldName(sp, member);
      String wireName = BeamXmlBindingIndex.memberElementName(member);
      entries.add(
          MapEntry.stringKey(wireName, new DotCallExpr(Variable.of(varName), field, List.of())));
    }
    return MapExpr.of(entries);
  }

  private static Expression buildPathExpression(
      String uriTemplate, List<HttpBinding> labels, SymbolProvider sp, String inputVar) {
    if (labels.isEmpty()) {
      return StringExpr.of(uriTemplate);
    }
    Map<String, HttpBinding> byLocation = new HashMap<>();
    for (HttpBinding lb : labels) {
      byLocation.put(lb.getLocationName(), lb);
    }
    Expression expr = null;
    int pos = 0;
    while (pos < uriTemplate.length()) {
      int start = uriTemplate.indexOf('{', pos);
      if (start < 0) {
        expr = appendPathSegment(expr, StringExpr.of(uriTemplate.substring(pos)));
        break;
      }
      if (start > pos) {
        expr = appendPathSegment(expr, StringExpr.of(uriTemplate.substring(pos, start)));
      }
      int end = uriTemplate.indexOf('}', start);
      String labelName = uriTemplate.substring(start + 1, end);
      HttpBinding lb = byLocation.get(labelName);
      if (lb != null) {
        String field = fieldName(sp, lb.getMember());
        expr =
            appendPathSegment(
                expr,
                RemoteCallExpr.of(
                    "URI",
                    "encode",
                    List.of(
                        RemoteCallExpr.of(
                            "Kernel",
                            "to_string",
                            List.of(new DotCallExpr(Variable.of(inputVar), field, List.of()))))));
      } else {
        expr = appendPathSegment(expr, StringExpr.of("{" + labelName + "}"));
      }
      pos = end + 1;
    }
    return expr;
  }

  private static Expression appendPathSegment(Expression current, Expression segment) {
    return current == null ? segment : InfixExpr.of(current, "<>", segment);
  }

  private static Function defp(
      String name, List<Pattern> params, Expression body, boolean oneLiner) {
    return new Function(name, true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
  }

  private static Expression block(List<Expression> statements) {
    if (statements.isEmpty()) {
      return NilExpr.of();
    }
    if (statements.size() == 1) {
      return statements.get(0);
    }
    return new BlockExpr(statements);
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
