package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AnonFun;
import io.beam.dsl.elixir.AnonFunClause;
import io.beam.dsl.elixir.AssignPattern;
import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.AtomPattern;
import io.beam.dsl.elixir.BlockExpr;
import io.beam.dsl.elixir.CaseExpr;
import io.beam.dsl.elixir.Clause;
import io.beam.dsl.elixir.DotCallExpr;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionDoc;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.InfixExpr;
import io.beam.dsl.elixir.IntegerExpr;
import io.beam.dsl.elixir.IntegerPattern;
import io.beam.dsl.elixir.ListExpr;
import io.beam.dsl.elixir.LocalCallExpr;
import io.beam.dsl.elixir.MapEntry;
import io.beam.dsl.elixir.MapExpr;
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
import io.beam.dsl.elixir.StructPattern;
import io.beam.dsl.elixir.StructPatternField;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.beam.dsl.elixir.WildcardPattern;
import io.smithy.beam.core.BeamAwsQueryFormEncoder;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamXmlBindingIndex;
import io.smithy.beam.core.BeamXmlDecoder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ByteShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.XmlNameTrait;

final class ElixirAwsQueryOperationIr {
  private static final Pattern W = WildcardPattern.of();
  private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

  private ElixirAwsQueryOperationIr() {}

  static List<Function> buildEncodeRequest(
      Model model,
      ServiceShape service,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputStruct = structName(sp, input);
    String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));
    String action = BeamAwsQueryFormEncoder.operationAction(op, service);
    String version = BeamAwsQueryFormEncoder.serviceVersion(service);

    Spec spec =
        Spec.of(
            "encode_"
                + opName
                + "_request("
                + inputType
                + ") :: %"
                + runtimeMod
                + ".HttpRequest{}");

    List<Expression> body = new ArrayList<>();
    body.add(
        MatchExpr.bind(
            "pairs",
            ListExpr.of(
                List.of(
                    TupleExpr.of(List.of(StringExpr.of("Action"), StringExpr.of(action))),
                    TupleExpr.of(List.of(StringExpr.of("Version"), StringExpr.of(version)))),
                LocalCallExpr.of("flatten_query_input", List.of(Variable.of("input"))))));
    body.add(
        MatchExpr.bind(
            "body",
            PipeExpr.of(
                Variable.of("pairs"),
                List.of(
                    PipeStep.of(
                        RemoteCallExpr.of("Enum", "reject", List.of(rejectNilPairsFn())),
                        List.of()),
                    PipeStep.of(
                        RemoteCallExpr.of("Enum", "map", List.of(encodePairsFn())), List.of()),
                    PipeStep.of(
                        RemoteCallExpr.of("URI", "encode_query", List.of()), List.of())))));
    body.add(buildHttpRequestStruct(runtimeMod, Variable.of("body")));

    return List.of(
        def(
            "encode_" + opName + "_request",
            List.of(AssignPattern.of("input", inputPattern(input, sp, true))),
            block(body),
            spec,
            FunctionDoc.of("Encode AWS Query request for " + op.getId() + "."),
            false));
  }

  static List<Function> buildFlattenQueryInput(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      List<StructureShape> inputs,
      boolean ec2Query) {
    List<Function> functions = new ArrayList<>();
    for (StructureShape input : inputs) {
      functions.add(buildFlattenInputFunction(model, httpIndex, sp, input, ec2Query));
    }
    return functions;
  }

  static List<Function> buildFlattenStructure(
      SymbolProvider sp, Set<StructureShape> structures, boolean ec2Query) {
    List<Function> functions = new ArrayList<>();
    for (StructureShape structure : structures) {
      functions.add(buildFlattenStructureFunction(sp, structure, ec2Query));
    }
    functions.add(
        defp(
            "flatten_structure",
            List.of(VariablePattern.of("_wire_prefix"), NilPattern.of()),
            ListExpr.of(List.of()),
            true));
    functions.add(
        defp(
            "flatten_structure",
            List.of(VariablePattern.of("_wire_prefix"), VariablePattern.of("_value")),
            ListExpr.of(List.of()),
            true));
    return functions;
  }

  static Set<StructureShape> nestedQueryStructures(Model model, List<StructureShape> inputs) {
    Set<StructureShape> nested = new LinkedHashSet<>();
    Deque<StructureShape> queue = new ArrayDeque<>(inputs);
    while (!queue.isEmpty()) {
      StructureShape shape = queue.removeFirst();
      for (MemberShape member : shape.members()) {
        Shape target = model.expectShape(member.getTarget());
        if (target instanceof StructureShape structureShape) {
          if (nested.add(structureShape)) {
            queue.addLast(structureShape);
          }
        } else if (target instanceof ListShape listShape) {
          Shape listMember = model.expectShape(listShape.getMember().getTarget());
          if (listMember instanceof StructureShape structureShape) {
            if (nested.add(structureShape)) {
              queue.addLast(structureShape);
            }
          }
        }
      }
    }
    return nested;
  }

  static List<Function> buildDecodeResponse(
      Model model,
      ServiceShape service,
      OperationShape op,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      boolean ec2Query) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputStruct = structName(sp, output);
    String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));
    String resultElement =
        ec2Query
            ? BeamXmlDecoder.ec2QueryResultElementName(op, service)
            : BeamXmlDecoder.queryResultElementName(op, service);

    Spec spec =
        Spec.of(
            "decode_"
                + opName
                + "_response(map()) :: {:ok, "
                + outputType
                + "} | {:error, term()}");

    Pattern successPattern =
        StructPattern.of(
            runtimeMod + ".HttpResponse",
            List.of(
                StructPatternField.of("status", IntegerPattern.of(200)),
                StructPatternField.of("body", VariablePattern.of("body"))));

    Pattern fallbackPattern =
        StructPattern.of(
            runtimeMod + ".HttpResponse",
            List.of(
                StructPatternField.of("status", VariablePattern.of("status")),
                StructPatternField.of("body", VariablePattern.of("body"))));

    return List.of(
        def(
            "decode_" + opName + "_response",
            List.of(successPattern),
            buildDecodeSuccessBody(model, output, outputStruct, resultElement, sp, ec2Query),
            spec,
            FunctionDoc.of("Decode AWS Query response for " + op.getId() + "."),
            false),
        def(
            "decode_" + opName + "_response",
            List.of(fallbackPattern),
            LocalCallExpr.of(
                "decode_query_error", List.of(Variable.of("status"), Variable.of("body"))),
            null,
            null,
            true));
  }

  static List<Function> buildServerDecodeRequest(
      Model model, OperationShape op, SymbolProvider sp, String typesMod, String runtimeMod) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));

    Spec spec = Spec.of("decode_" + opName + "_request(map()) :: " + inputType);
    Pattern pattern =
        StructPattern.of(
            runtimeMod + ".HttpRequest",
            List.of(StructPatternField.of("body", VariablePattern.of("body"))));

    Expression body =
        PipeExpr.of(
            Variable.of("body"),
            List.of(
                PipeStep.of(LocalCallExpr.of("parse_query_params", List.of()), List.of()),
                PipeStep.of(
                    LocalCallExpr.of(
                        "parse_" + recordName(sp.toSymbol(input)) + "_input", List.of()),
                    List.of())));

    return List.of(
        def(
            "decode_" + opName + "_request",
            List.of(pattern),
            body,
            spec,
            FunctionDoc.of("Decode AWS Query server request for " + op.getId() + "."),
            false));
  }

  static List<Function> buildServerEncodeResponse(
      Model model,
      ServiceShape service,
      OperationShape op,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      boolean ec2Query) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputStruct = structName(sp, output);
    String outputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(output));
    String resultElement =
        ec2Query
            ? BeamXmlDecoder.ec2QueryResultElementName(op, service)
            : BeamXmlDecoder.queryResultElementName(op, service);
    String responseElement = operationWireName(op, service) + "Response";

    Spec spec =
        Spec.of(
            "encode_"
                + opName
                + "_response("
                + outputType
                + ") :: %"
                + runtimeMod
                + ".HttpResponse{}");
    Pattern pattern = outputPattern(output, sp, true);

    List<Expression> body = new ArrayList<>();
    body.add(
        MatchExpr.bind(
            "result_content",
            PipeExpr.of(
                LocalCallExpr.of(
                    recordName(sp.toSymbol(output)) + "_to_result_map",
                    List.of(Variable.of("output"))),
                List.of(
                    PipeStep.of(
                        RemoteCallExpr.of("Enum", "reject", List.of(rejectNilMapFn())), List.of()),
                    PipeStep.of(RemoteCallExpr.of("Map", "new", List.of()), List.of())))));
    if (ec2Query) {
      body.add(
          MatchExpr.bind(
              "body",
              LocalCallExpr.of(
                  "encode_xml",
                  List.of(
                      MapExpr.of(
                          List.of(
                              MapEntry.stringKey(resultElement, Variable.of("result_content")))),
                      LocalCallExpr.of("xml_namespace", List.of())))));
    } else {
      body.add(
          MatchExpr.bind(
              "body",
              LocalCallExpr.of(
                  "wrap_aws_query_response",
                  List.of(
                      StringExpr.of(resultElement),
                      Variable.of("result_content"),
                      StringExpr.of(responseElement),
                      LocalCallExpr.of("xml_namespace", List.of())))));
    }
    body.add(
        StructExpr.of(
            runtimeMod + ".HttpResponse",
            List.of(
                StructField.of("status", IntegerExpr.of(200)),
                StructField.of(
                    "headers",
                    ListExpr.of(
                        List.of(
                            TupleExpr.of(
                                List.of(
                                    StringExpr.of("Content-Type"), StringExpr.of("text/xml")))))),
                StructField.of("body", Variable.of("body")))));

    return List.of(
        def(
            "encode_" + opName + "_response",
            List.of(pattern),
            block(body),
            spec,
            FunctionDoc.of("Encode AWS Query server response for " + op.getId() + "."),
            false));
  }

  static List<Function> buildParseInputFromForm(
      Model model, SymbolProvider sp, String typesMod, StructureShape input, boolean ec2Query) {
    String inputRecord = recordName(sp.toSymbol(input));
    List<MemberShape> members = new ArrayList<>(input.members());

    List<MapEntry> fields = new ArrayList<>();
    for (MemberShape member : members) {
      String field = fieldName(sp, member);
      String wireKey = queryFormKey(member, ec2Query);
      Shape target = model.expectShape(member.getTarget());
      Expression valueExpr;
      if (target instanceof ListShape) {
        valueExpr =
            ec2Query
                ? LocalCallExpr.of(
                    "form_list_values_ec2", List.of(Variable.of("params"), StringExpr.of(wireKey)))
                : LocalCallExpr.of(
                    "form_list_values_aws", List.of(Variable.of("params"), StringExpr.of(wireKey)));
      } else {
        valueExpr =
            LocalCallExpr.of("form_value", List.of(Variable.of("params"), StringExpr.of(wireKey)));
      }
      fields.add(MapEntry.atomKey(field, valueExpr));
    }

    return List.of(
        defp(
            "parse_" + inputRecord + "_input",
            List.of(VariablePattern.of("params")),
            StructExpr.of("Types." + structName(sp, input), structFields(fields)),
            true));
  }

  static List<Function> buildOutputToResultMap(
      Model model, SymbolProvider sp, StructureShape output) {
    String outputRecord = recordName(sp.toSymbol(output));
    List<MapEntry> entries = new ArrayList<>();
    for (MemberShape member : output.members()) {
      String field = fieldName(sp, member);
      String element = BeamXmlDecoder.memberElementName(member);
      entries.add(
          MapEntry.stringKey(element, DotCallExpr.of(Variable.of("output"), field, List.of())));
    }
    return List.of(
        defp(
            outputRecord + "_to_result_map",
            List.of(outputPattern(output, sp, false)),
            MapExpr.of(entries),
            true));
  }

  private static Function buildFlattenInputFunction(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      StructureShape input,
      boolean ec2Query) {
    List<MemberShape> members = documentMembers(httpIndex, input);
    Expression body;
    if (members.isEmpty()) {
      body = ListExpr.of(List.of());
    } else {
      List<Expression> memberCalls = new ArrayList<>();
      for (MemberShape member : members) {
        String field = fieldName(sp, member);
        String wireKey = queryFormKey(member, ec2Query);
        memberCalls.add(
            LocalCallExpr.of(
                "flatten_member", List.of(StringExpr.of(wireKey), Variable.of(field))));
      }
      body =
          PipeExpr.of(
              ListExpr.of(memberCalls),
              List.of(PipeStep.of(RemoteCallExpr.of("List", "flatten", List.of()), List.of())));
    }
    return defp(
        "flatten_query_input", List.of(inputPattern(input, sp, false)), body, members.isEmpty());
  }

  private static Function buildFlattenStructureFunction(
      SymbolProvider sp, StructureShape structure, boolean ec2Query) {
    List<Expression> memberCalls = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String field = fieldName(sp, member);
      String wireKey = queryFormKey(member, ec2Query);
      Expression memberKey =
          InfixExpr.of(
              InfixExpr.of(Variable.of("wire_prefix"), "<>", StringExpr.of(".")),
              "<>",
              StringExpr.of(wireKey));
      memberCalls.add(LocalCallExpr.of("flatten_member", List.of(memberKey, Variable.of(field))));
    }
    Expression body =
        memberCalls.isEmpty()
            ? ListExpr.of(List.of())
            : PipeExpr.of(
                ListExpr.of(memberCalls),
                List.of(PipeStep.of(RemoteCallExpr.of("List", "flatten", List.of()), List.of())));
    return defp(
        "flatten_structure",
        List.of(VariablePattern.of("wire_prefix"), inputPattern(structure, sp, false)),
        body,
        memberCalls.isEmpty());
  }

  private static Expression buildDecodeSuccessBody(
      Model model,
      StructureShape output,
      String outputStruct,
      String resultElement,
      SymbolProvider sp,
      boolean ec2Query) {
    Expression unwrap =
        LocalCallExpr.of(
            "unwrap_query_result", List.of(Variable.of("body"), StringExpr.of(resultElement)));
    if (output.members().isEmpty()) {
      return CaseExpr.of(
          unwrap,
          List.of(
              Clause.of(
                  TuplePattern.of(List.of(AtomPattern.of("ok"), W)),
                  TupleExpr.of(
                      List.of(
                          AtomExpr.of("ok"), StructExpr.of("Types." + outputStruct, List.of())))),
              Clause.of(
                  TuplePattern.of(
                      List.of(
                          AtomPattern.of("error"),
                          TuplePattern.of(List.of(AtomPattern.of("missing_result"), W)))),
                  TupleExpr.of(
                      List.of(
                          AtomExpr.of("ok"), StructExpr.of("Types." + outputStruct, List.of())))),
              Clause.of(
                  TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("reason"))),
                  TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("reason"))))));
    }
    Expression okStruct =
        StructExpr.of(
            "Types." + outputStruct,
            structFields(buildOutputStructFields(model, sp, output, "result", ec2Query)));
    return CaseExpr.of(
        unwrap,
        List.of(
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("result"))),
                TupleExpr.of(List.of(AtomExpr.of("ok"), okStruct))),
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("reason"))),
                TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("reason"))))));
  }

  private static List<MapEntry> buildOutputStructFields(
      Model model, SymbolProvider sp, StructureShape output, String resultVar, boolean ec2Query) {
    List<MapEntry> fields = new ArrayList<>();
    for (MemberShape member : output.members()) {
      String field = fieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof ListShape listShape) {
        fields.add(
            MapEntry.atomKey(
                field,
                buildDecodeListFieldExpr(model, member, listShape, resultVar, sp, ec2Query)));
      } else if (target instanceof StructureShape nested) {
        String element = BeamXmlDecoder.memberElementName(member);
        String nestedVar = xmlVarForElement(element);
        fields.add(
            MapEntry.atomKey(
                field,
                CaseExpr.of(
                    LocalCallExpr.of(
                        "find_element",
                        List.of(
                            StringExpr.of(element),
                            LocalCallExpr.of("element_content", List.of(Variable.of(resultVar))))),
                    List.of(
                        Clause.of(NilPattern.of(), NilExpr.of()),
                        Clause.of(
                            VariablePattern.of(nestedVar),
                            buildDecodeStructureExpr(model, nested, nestedVar, sp, ec2Query))))));
      } else {
        fields.add(MapEntry.atomKey(field, decodeXmlChildText(model, sp, member, resultVar)));
      }
    }
    return fields;
  }

  private static StructExpr buildDecodeStructureExpr(
      Model model, StructureShape structure, String xmlVar, SymbolProvider sp, boolean ec2Query) {
    return StructExpr.of(
        "Types." + structName(sp, structure),
        structFields(buildNestedStructFields(model, structure, xmlVar, sp, ec2Query)));
  }

  private static List<MapEntry> buildNestedStructFields(
      Model model, StructureShape structure, String xmlVar, SymbolProvider sp, boolean ec2Query) {
    List<MapEntry> fields = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String field = fieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof ListShape listShape) {
        fields.add(
            MapEntry.atomKey(
                field, buildDecodeListFieldExpr(model, member, listShape, xmlVar, sp, ec2Query)));
      } else if (target instanceof StructureShape nested) {
        String element = BeamXmlDecoder.memberElementName(member);
        String nestedVar = xmlVarForElement(element);
        fields.add(
            MapEntry.atomKey(
                field,
                CaseExpr.of(
                    LocalCallExpr.of(
                        "find_element",
                        List.of(
                            StringExpr.of(element),
                            LocalCallExpr.of("element_content", List.of(Variable.of(xmlVar))))),
                    List.of(
                        Clause.of(NilPattern.of(), NilExpr.of()),
                        Clause.of(
                            VariablePattern.of(nestedVar),
                            buildDecodeStructureExpr(model, nested, nestedVar, sp, ec2Query))))));
      } else {
        fields.add(MapEntry.atomKey(field, decodeXmlChildText(model, sp, member, xmlVar)));
      }
    }
    return fields;
  }

  private static Expression buildDecodeListFieldExpr(
      Model model,
      MemberShape member,
      ListShape listShape,
      String xmlVar,
      SymbolProvider sp,
      boolean ec2Query) {
    String element = BeamXmlDecoder.memberElementName(member);
    String itemElement =
        listShape.getMember().hasTrait(XmlNameTrait.class)
            ? BeamXmlDecoder.memberElementName(listShape.getMember())
            : BeamXmlBindingIndex.listItemElementName(member, listShape, model);
    Shape listMember = model.expectShape(listShape.getMember().getTarget());
    if (listMember instanceof StructureShape nested) {
      return LocalCallExpr.of(
          "xml_child_struct_list",
          List.of(
              Variable.of(xmlVar),
              StringExpr.of(element),
              StringExpr.of(itemElement),
              AnonFun.of(
                  List.of(
                      AnonFunClause.of(
                          List.of(VariablePattern.of("item")),
                          buildDecodeStructureExpr(model, nested, "item", sp, ec2Query))))));
    }
    return LocalCallExpr.of(
        "xml_child_list",
        List.of(Variable.of(xmlVar), StringExpr.of(element), StringExpr.of(itemElement)));
  }

  private static StructExpr buildHttpRequestStruct(String runtimeMod, Expression body) {
    return StructExpr.of(
        runtimeMod + ".HttpRequest",
        List.of(
            StructField.of("method", StringExpr.of("POST")),
            StructField.of("path", StringExpr.of("/")),
            StructField.of("query", MapExpr.of(List.of())),
            StructField.of(
                "headers",
                ListExpr.of(
                    List.of(
                        TupleExpr.of(
                            List.of(StringExpr.of("Content-Type"), StringExpr.of(CONTENT_TYPE)))))),
            StructField.of("body", body)));
  }

  private static AnonFun rejectNilPairsFn() {
    return AnonFun.of(
        List.of(
            AnonFunClause.of(
                List.of(TuplePattern.of(List.of(W, VariablePattern.of("v")))),
                RemoteCallExpr.of("Kernel", "is_nil", List.of(Variable.of("v"))))));
  }

  private static AnonFun encodePairsFn() {
    return AnonFun.of(
        List.of(
            AnonFunClause.of(
                List.of(TuplePattern.of(List.of(VariablePattern.of("k"), VariablePattern.of("v")))),
                TupleExpr.of(
                    List.of(
                        Variable.of("k"), LocalCallExpr.of("enc", List.of(Variable.of("v"))))))));
  }

  private static AnonFun rejectNilMapFn() {
    return AnonFun.of(
        List.of(
            AnonFunClause.of(
                List.of(TuplePattern.of(List.of(W, VariablePattern.of("v")))),
                RemoteCallExpr.of("Kernel", "is_nil", List.of(Variable.of("v"))))));
  }

  private static Pattern inputPattern(StructureShape input, SymbolProvider sp, boolean unused) {
    List<StructPatternField> fields = new ArrayList<>();
    for (MemberShape member : input.members()) {
      String field = fieldName(sp, member);
      Pattern var = unused ? VariablePattern.of("_" + field) : VariablePattern.of(field);
      fields.add(StructPatternField.of(field, var));
    }
    return StructPattern.of("Types." + structName(sp, input), fields);
  }

  private static Pattern outputPattern(StructureShape output, SymbolProvider sp, boolean unused) {
    List<StructPatternField> fields = new ArrayList<>();
    for (MemberShape member : output.members()) {
      String field = fieldName(sp, member);
      Pattern var = unused ? VariablePattern.of("_" + field) : VariablePattern.of(field);
      fields.add(StructPatternField.of(field, var));
    }
    return StructPattern.of("Types." + structName(sp, output), fields);
  }

  private static List<StructField> structFields(List<MapEntry> entries) {
    List<StructField> fields = new ArrayList<>();
    for (MapEntry entry : entries) {
      String name =
          entry.key() instanceof AtomExpr atom ? atom.value() : ((StringExpr) entry.key()).value();
      fields.add(StructField.of(name, entry.value()));
    }
    return fields;
  }

  private static List<MemberShape> documentMembers(
      HttpBindingIndex httpIndex, StructureShape structure) {
    return new ArrayList<>(structure.members());
  }

  private static String queryFormKey(MemberShape member, boolean ec2Query) {
    return ec2Query
        ? BeamAwsQueryFormEncoder.ec2QueryFormKey(member)
        : BeamAwsQueryFormEncoder.awsQueryFormKey(member);
  }

  private static String structName(SymbolProvider sp, StructureShape shape) {
    return sp.toSymbol(shape).getName();
  }

  private static String shapeName(SymbolProvider sp, Shape shape) {
    return sp.toSymbol(shape).getName();
  }

  private static String recordName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }

  private static String fieldName(SymbolProvider sp, MemberShape member) {
    return sp.toSymbol(member).getProperty("fieldName", String.class).orElseThrow();
  }

  private static String xmlVarForElement(String element) {
    if (element.isEmpty()) {
      return "nested_xml";
    }
    return BeamNameUtils.toSnakeCase(element) + "_xml";
  }

  private static Expression decodeXmlChildText(
      Model model, SymbolProvider sp, MemberShape member, String xmlVar) {
    Expression text =
        LocalCallExpr.of(
            "xml_child_text",
            List.of(Variable.of(xmlVar), StringExpr.of(BeamXmlDecoder.memberElementName(member))));
    return decodeXmlTextValue(model, sp, member, text);
  }

  private static Expression decodeXmlTextValue(
      Model model, SymbolProvider sp, MemberShape member, Expression textExpr) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof BooleanShape) {
      return LocalCallExpr.of("decode_xml_boolean", List.of(textExpr));
    }
    if (target instanceof EnumShape enumShape) {
      return decodeXmlTextWithConversion(
          textExpr,
          RemoteCallExpr.of(
              "Types." + shapeName(sp, enumShape), "from_string", List.of(Variable.of("text"))));
    }
    if (target instanceof IntEnumShape intEnumShape) {
      return decodeXmlTextWithConversion(
          textExpr,
          RemoteCallExpr.of(
              "Types." + shapeName(sp, intEnumShape),
              "from_integer",
              List.of(RemoteCallExpr.of("String", "to_integer", List.of(Variable.of("text"))))));
    }
    if (target instanceof ByteShape
        || target instanceof ShortShape
        || target instanceof IntegerShape
        || target instanceof LongShape
        || target instanceof BigIntegerShape) {
      return LocalCallExpr.of("decode_xml_integer", List.of(textExpr));
    }
    if (target instanceof FloatShape || target instanceof DoubleShape) {
      return LocalCallExpr.of("decode_xml_float", List.of(textExpr));
    }
    return textExpr;
  }

  private static Expression decodeXmlTextWithConversion(
      Expression textExpr, Expression convertedExpr) {
    return CaseExpr.of(
        textExpr,
        List.of(
            Clause.of(NilPattern.of(), NilExpr.of()),
            Clause.of(VariablePattern.of("text"), convertedExpr)));
  }

  private static String operationWireName(OperationShape operation, ServiceShape service) {
    return operation.getId().getName(service);
  }

  private static Function def(
      String name,
      List<Pattern> params,
      Expression body,
      Spec spec,
      FunctionDoc doc,
      boolean oneLiner) {
    return Function.of(name, false, List.of(FunctionHead.of(params)), body, spec, doc, oneLiner);
  }

  private static Function defp(
      String name, List<Pattern> params, Expression body, boolean oneLiner) {
    return Function.of(name, true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
  }

  private static Expression block(List<Expression> statements) {
    if (statements.isEmpty()) {
      return NilExpr.of();
    }
    if (statements.size() == 1) {
      return statements.get(0);
    }
    return BlockExpr.of(statements);
  }
}
