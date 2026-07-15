package io.smithy.beam.erlang;

import io.beam.dsl.erlang.AtomExpr;
import io.beam.dsl.erlang.AtomPattern;
import io.beam.dsl.erlang.BinaryExpr;
import io.beam.dsl.erlang.BinarySegmentExpr;
import io.beam.dsl.erlang.BlockExpr;
import io.beam.dsl.erlang.CaseExpr;
import io.beam.dsl.erlang.Clause;
import io.beam.dsl.erlang.Edoc;
import io.beam.dsl.erlang.Expression;
import io.beam.dsl.erlang.Fun;
import io.beam.dsl.erlang.FunClause;
import io.beam.dsl.erlang.Function;
import io.beam.dsl.erlang.FunctionClause;
import io.beam.dsl.erlang.InfixExpr;
import io.beam.dsl.erlang.IntegerExpr;
import io.beam.dsl.erlang.IntegerPattern;
import io.beam.dsl.erlang.ListComprehensionExpr;
import io.beam.dsl.erlang.ListExpr;
import io.beam.dsl.erlang.LocalCallExpr;
import io.beam.dsl.erlang.MapEntry;
import io.beam.dsl.erlang.MapExpr;
import io.beam.dsl.erlang.MatchExpr;
import io.beam.dsl.erlang.RecordExpr;
import io.beam.dsl.erlang.RecordField;
import io.beam.dsl.erlang.RecordPattern;
import io.beam.dsl.erlang.RecordPatternField;
import io.beam.dsl.erlang.RemoteCallExpr;
import io.beam.dsl.erlang.Spec;
import io.beam.dsl.erlang.TupleExpr;
import io.beam.dsl.erlang.TuplePattern;
import io.beam.dsl.erlang.Variable;
import io.beam.dsl.erlang.VariablePattern;
import io.beam.dsl.erlang.WildcardPattern;
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
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.XmlNameTrait;

final class ErlangAwsQueryOperationIr {
  private ErlangAwsQueryOperationIr() {}

  static Function buildEncodeRequest(
      Model model,
      ServiceShape service,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputRecord = recordName(sp.toSymbol(input));
    String inputType = sp.toSymbol(input).getName();
    String action = BeamAwsQueryFormEncoder.operationAction(op, service);
    String version = BeamAwsQueryFormEncoder.serviceVersion(service);

    RecordPattern inputPattern = inputBindingHead("Input", inputRecord, input, sp);

    List<Expression> body = new ArrayList<>();
    body.add(
        MatchExpr.bindValue(
            "Pairs",
            ListExpr.of(
                List.of(
                    TupleExpr.of(List.of(BinaryExpr.of("Action"), BinaryExpr.of(action))),
                    TupleExpr.of(List.of(BinaryExpr.of("Version"), BinaryExpr.of(version)))),
                LocalCallExpr.of("flatten_query_input", List.of(Variable.of("Input"))))));
    body.add(
        MatchExpr.bindValue(
            "Body",
            RemoteCallExpr.of(
                "uri_string",
                "compose_query",
                List.of(
                    ListComprehensionExpr.of(
                        TupleExpr.of(
                            List.of(
                                Variable.of("K"),
                                LocalCallExpr.of("enc", List.of(Variable.of("V"))))),
                        TuplePattern.of(List.of(VariablePattern.of("K"), VariablePattern.of("V"))),
                        Variable.of("Pairs"),
                        InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("undefined")))))));
    body.add(
        RecordExpr.of(
            "http_request",
            List.of(
                RecordField.of("method", BinaryExpr.of("POST")),
                RecordField.of("path", BinaryExpr.of("/")),
                RecordField.of("query", MapExpr.of(List.of())),
                RecordField.of(
                    "headers",
                    ListExpr.of(
                        List.of(
                            TupleExpr.of(
                                List.of(
                                    BinaryExpr.of("Content-Type"),
                                    BinaryExpr.of("application/x-www-form-urlencoded")))))),
                RecordField.of("body", Variable.of("Body")))));

    return Function.of(
        "encode_" + opName + "_request",
        List.of(FunctionClause.of(List.of(inputPattern), BlockExpr.commaSeparated(body, false))),
        Spec.of("encode_" + opName + "_request(" + inputType + ") -> #http_request{}"),
        Edoc.of("Encode AWS Query request for " + op.getId() + "."));
  }

  static Function buildFlattenQueryInput(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      List<StructureShape> inputs,
      boolean ec2Query) {
    List<FunctionClause> clauses = new ArrayList<>();
    for (StructureShape input : inputs) {
      clauses.add(buildFlattenInputClause(model, httpIndex, sp, input, ec2Query));
    }
    return Function.of("flatten_query_input", clauses);
  }

  static Function buildFlattenStructure(
      SymbolProvider sp, Set<StructureShape> structures, boolean ec2Query) {
    List<FunctionClause> clauses = new ArrayList<>();
    for (StructureShape structure : structures) {
      clauses.add(buildFlattenStructureClause(sp, structure, ec2Query));
    }
    clauses.add(
        FunctionClause.of(
            List.of(VariablePattern.of("_WirePrefix"), AtomPattern.of("undefined")),
            ListExpr.of(List.of())));
    clauses.add(
        FunctionClause.of(
            List.of(VariablePattern.of("_WirePrefix"), VariablePattern.of("_Value")),
            ListExpr.of(List.of())));
    return Function.of("flatten_structure", clauses);
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

  private static FunctionClause buildFlattenStructureClause(
      SymbolProvider sp, StructureShape structure, boolean ec2Query) {
    String record = recordName(sp.toSymbol(structure));
    List<RecordPatternField> fieldPatterns = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String field = memberFieldName(sp, member);
      fieldPatterns.add(RecordPatternField.of(field, VariablePattern.of(toBindingVar(field))));
    }
    RecordPattern pattern = RecordPattern.of(record, fieldPatterns);

    Expression body;
    if (structure.members().isEmpty()) {
      body = ListExpr.of(List.of());
    } else {
      List<Expression> appendArgs = new ArrayList<>();
      for (MemberShape member : structure.members()) {
        String field = memberFieldName(sp, member);
        String wireKey = queryFormKey(member, ec2Query);
        appendArgs.add(
            LocalCallExpr.of(
                "flatten_member",
                List.of(flattenStructureMemberKey(wireKey), Variable.of(toBindingVar(field)))));
      }
      body = RemoteCallExpr.of("lists", "append", List.of(ListExpr.of(appendArgs)));
    }
    return FunctionClause.of(List.of(VariablePattern.of("WirePrefix"), pattern), body);
  }

  private static Expression flattenStructureMemberKey(String wireKey) {
    return BinaryExpr.of(
        List.of(
            BinarySegmentExpr.of(Variable.of("WirePrefix"), "binary"),
            BinarySegmentExpr.literal("." + wireKey)));
  }

  private static FunctionClause buildFlattenInputClause(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      StructureShape input,
      boolean ec2Query) {
    String inputRecord = recordName(sp.toSymbol(input));
    List<MemberShape> members = documentMembers(httpIndex, input);

    List<RecordPatternField> fieldPatterns = new ArrayList<>();
    for (MemberShape member : members) {
      String field = memberFieldName(sp, member);
      fieldPatterns.add(RecordPatternField.of(field, VariablePattern.of(toBindingVar(field))));
    }
    RecordPattern pattern = RecordPattern.of(inputRecord, fieldPatterns);

    Expression body;
    if (members.isEmpty()) {
      body = ListExpr.of(List.of());
    } else {
      List<Expression> appendArgs = new ArrayList<>();
      for (MemberShape member : members) {
        String field = memberFieldName(sp, member);
        String wireKey = queryFormKey(member, ec2Query);
        appendArgs.add(
            LocalCallExpr.of(
                "flatten_member",
                List.of(BinaryExpr.of(wireKey), Variable.of(toBindingVar(field)))));
      }
      body = RemoteCallExpr.of("lists", "append", List.of(ListExpr.of(appendArgs)));
    }
    return FunctionClause.of(List.of(pattern), body);
  }

  static Function buildDecodeResponse(
      Model model, ServiceShape service, OperationShape op, SymbolProvider sp, boolean ec2Query) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputRecord = recordName(sp.toSymbol(output));
    String outputType = sp.toSymbol(output).getName();
    String resultElement =
        ec2Query
            ? BeamXmlDecoder.ec2QueryResultElementName(op, service)
            : BeamXmlDecoder.queryResultElementName(op, service);

    RecordPattern successPattern =
        RecordPattern.of(
            "http_response",
            List.of(
                RecordPatternField.of("status", IntegerPattern.of(200)),
                RecordPatternField.of("body", VariablePattern.of("Body"))));

    RecordPattern fallbackPattern =
        RecordPattern.of(
            "http_response",
            List.of(
                RecordPatternField.of("status", VariablePattern.of("Status")),
                RecordPatternField.of("body", VariablePattern.of("Body"))));

    List<FunctionClause> clauses =
        List.of(
            FunctionClause.of(
                List.of(successPattern),
                buildDecodeSuccessBody(output, outputRecord, resultElement, model, sp, ec2Query)),
            FunctionClause.of(
                List.of(fallbackPattern),
                LocalCallExpr.of(
                    "decode_query_error", List.of(Variable.of("Status"), Variable.of("Body")))));

    return Function.of(
        "decode_" + opName + "_response",
        clauses,
        Spec.of(
            "decode_"
                + opName
                + "_response(#http_response{}) -> {'ok', "
                + outputType
                + "} | {'error', term()}"),
        Edoc.of("Decode AWS Query response for " + op.getId() + "."));
  }

  private static Expression buildDecodeSuccessBody(
      StructureShape output,
      String outputRecord,
      String resultElement,
      Model model,
      SymbolProvider sp,
      boolean ec2Query) {
    Expression unwrap =
        LocalCallExpr.of(
            "unwrap_query_result", List.of(Variable.of("Body"), BinaryExpr.of(resultElement)));
    if (output.members().isEmpty()) {
      Expression emptyOutput = RecordExpr.of(outputRecord, List.of());
      return CaseExpr.of(
          unwrap,
          List.of(
              Clause.of(
                  TuplePattern.of(List.of(AtomPattern.of("ok"), WildcardPattern.of())),
                  TupleExpr.of(List.of(AtomExpr.of("ok"), emptyOutput))),
              Clause.of(
                  TuplePattern.of(
                      List.of(
                          AtomPattern.of("error"),
                          TuplePattern.of(
                              List.of(AtomPattern.of("missing_result"), WildcardPattern.of())))),
                  TupleExpr.of(List.of(AtomExpr.of("ok"), emptyOutput))),
              Clause.of(
                  TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
                  TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("Reason"))))));
    }
    List<RecordField> fields = buildOutputRecordFields(model, sp, output, "Result", ec2Query);
    Expression okRecord =
        TupleExpr.of(List.of(AtomExpr.of("ok"), RecordExpr.of(outputRecord, fields)));
    return CaseExpr.of(
        unwrap,
        List.of(
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("ok"), VariablePattern.of("Result"))),
                okRecord),
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
                TupleExpr.of(List.of(AtomExpr.of("error"), Variable.of("Reason"))))));
  }

  static Function buildServerDecodeRequest(
      Model model, OperationShape op, SymbolProvider sp, boolean ec2Query) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputType = sp.toSymbol(input).getName();

    RecordPattern pattern =
        RecordPattern.of(
            "http_request", List.of(RecordPatternField.of("body", VariablePattern.of("Body"))));

    List<Expression> body =
        List.of(
            MatchExpr.bindValue(
                "Params", LocalCallExpr.of("parse_query_params", List.of(Variable.of("Body")))),
            LocalCallExpr.of(
                "parse_" + recordName(sp.toSymbol(input)) + "_input",
                List.of(Variable.of("Params"))));

    return Function.of(
        "decode_" + opName + "_request",
        List.of(FunctionClause.of(List.of(pattern), BlockExpr.commaSeparated(body, false))),
        Spec.of("decode_" + opName + "_request(#http_request{}) -> " + inputType),
        Edoc.of("Decode AWS Query server request for " + op.getId() + "."));
  }

  static Function buildServerEncodeResponse(
      Model model, ServiceShape service, OperationShape op, SymbolProvider sp, boolean ec2Query) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputRecord = recordName(sp.toSymbol(output));
    String outputType = sp.toSymbol(output).getName();
    String resultElement =
        ec2Query
            ? BeamXmlDecoder.ec2QueryResultElementName(op, service)
            : BeamXmlDecoder.queryResultElementName(op, service);
    String responseElement = operationWireName(op, service) + "Response";

    RecordPattern pattern = outputBindingHead(outputRecord, output, sp);

    Fun filterUndefined =
        Fun.of(
            List.of(
                FunClause.of(
                    List.of(WildcardPattern.of(), VariablePattern.of("V")),
                    InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("undefined")))));

    List<Expression> body = new ArrayList<>();
    body.add(
        MatchExpr.bindValue(
            "ResultContent",
            RemoteCallExpr.of(
                "maps",
                "filter",
                List.of(filterUndefined, MapExpr.of(buildOutputMapEntries(model, sp, output))))));
    if (ec2Query) {
      body.add(
          MatchExpr.bindValue(
              "Body",
              LocalCallExpr.of(
                  "encode_xml",
                  List.of(
                      MapExpr.of(
                          List.of(
                              MapEntry.of(
                                  BinaryExpr.of(resultElement), Variable.of("ResultContent")))),
                      LocalCallExpr.of("xml_namespace", List.of())))));
    } else {
      body.add(
          MatchExpr.bindValue(
              "Body",
              LocalCallExpr.of(
                  "wrap_aws_query_response",
                  List.of(
                      BinaryExpr.of(resultElement),
                      Variable.of("ResultContent"),
                      BinaryExpr.of(responseElement),
                      LocalCallExpr.of("xml_namespace", List.of())))));
    }
    body.add(
        RecordExpr.of(
            "http_response",
            List.of(
                RecordField.of("status", IntegerExpr.of(200)),
                RecordField.of(
                    "headers",
                    ListExpr.of(
                        List.of(
                            TupleExpr.of(
                                List.of(
                                    BinaryExpr.of("Content-Type"), BinaryExpr.of("text/xml")))))),
                RecordField.of("body", Variable.of("Body")))));

    return Function.of(
        "encode_" + opName + "_response",
        List.of(FunctionClause.of(List.of(pattern), BlockExpr.commaSeparated(body, false))),
        Spec.of("encode_" + opName + "_response(" + outputType + ") -> #http_response{}"),
        Edoc.of("Encode AWS Query server response for " + op.getId() + "."));
  }

  static Function buildParseInputFromForm(
      Model model, SymbolProvider sp, StructureShape input, boolean ec2Query) {
    String inputRecord = recordName(sp.toSymbol(input));
    List<MemberShape> members = new ArrayList<>(input.members());

    List<RecordField> fields = new ArrayList<>();
    for (MemberShape member : members) {
      String field = memberFieldName(sp, member);
      String wireKey = queryFormKey(member, ec2Query);
      Shape target = model.expectShape(member.getTarget());
      Expression valueExpr;
      if (target instanceof ListShape) {
        valueExpr =
            ec2Query
                ? LocalCallExpr.of(
                    "form_list_values_ec2", List.of(Variable.of("Params"), BinaryExpr.of(wireKey)))
                : LocalCallExpr.of(
                    "form_list_values_aws", List.of(Variable.of("Params"), BinaryExpr.of(wireKey)));
      } else {
        valueExpr =
            LocalCallExpr.of("form_value", List.of(Variable.of("Params"), BinaryExpr.of(wireKey)));
      }
      fields.add(RecordField.of(field, valueExpr));
    }

    return Function.of(
        "parse_" + inputRecord + "_input",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Params")), RecordExpr.of(inputRecord, fields))));
  }

  private static RecordPattern inputBindingHead(
      String alias, String recordName, StructureShape input, SymbolProvider sp) {
    List<RecordPatternField> fields = new ArrayList<>();
    for (MemberShape member : input.members()) {
      String field = memberFieldName(sp, member);
      fields.add(RecordPatternField.of(field, VariablePattern.of(toBindingVar(field))));
    }
    return RecordPattern.bind(alias, recordName, fields);
  }

  private static RecordPattern outputBindingHead(
      String recordName, StructureShape output, SymbolProvider sp) {
    List<RecordPatternField> fields = new ArrayList<>();
    for (MemberShape member : output.members()) {
      String field = memberFieldName(sp, member);
      fields.add(RecordPatternField.of(field, VariablePattern.of(toBindingVar(field))));
    }
    return RecordPattern.of(recordName, fields);
  }

  private static List<RecordField> buildOutputRecordFields(
      Model model, SymbolProvider sp, StructureShape output, String resultVar, boolean ec2Query) {
    List<RecordField> fields = new ArrayList<>();
    for (MemberShape member : output.members()) {
      String field = memberFieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof ListShape listShape) {
        fields.add(
            RecordField.of(
                field,
                buildDecodeListFieldExpr(model, member, listShape, resultVar, sp, ec2Query)));
      } else if (target instanceof StructureShape nested) {
        String element = BeamXmlDecoder.memberElementName(member);
        String nestedVar = xmlVarForElement(element);
        fields.add(
            RecordField.of(
                field,
                CaseExpr.of(
                    LocalCallExpr.of(
                        "find_element",
                        List.of(
                            BinaryExpr.of(element),
                            LocalCallExpr.of("element_content", List.of(Variable.of(resultVar))))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                        Clause.of(
                            VariablePattern.of(nestedVar),
                            buildDecodeStructureExpr(model, nested, nestedVar, sp, ec2Query))))));
      } else {
        fields.add(
            RecordField.of(
                field,
                LocalCallExpr.of(
                    "xml_child_text",
                    List.of(
                        Variable.of(resultVar),
                        BinaryExpr.of(BeamXmlDecoder.memberElementName(member))))));
      }
    }
    return fields;
  }

  private static Expression buildDecodeStructureExpr(
      Model model, StructureShape structure, String xmlVar, SymbolProvider sp, boolean ec2Query) {
    String recordTag = recordName(sp.toSymbol(structure));
    List<RecordField> fields = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String field = memberFieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof ListShape listShape) {
        fields.add(
            RecordField.of(
                field, buildDecodeListFieldExpr(model, member, listShape, xmlVar, sp, ec2Query)));
      } else if (target instanceof StructureShape nested) {
        String element = BeamXmlDecoder.memberElementName(member);
        String nestedVar = xmlVarForElement(element);
        fields.add(
            RecordField.of(
                field,
                CaseExpr.of(
                    LocalCallExpr.of(
                        "find_element",
                        List.of(
                            BinaryExpr.of(element),
                            LocalCallExpr.of("element_content", List.of(Variable.of(xmlVar))))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                        Clause.of(
                            VariablePattern.of(nestedVar),
                            buildDecodeStructureExpr(model, nested, nestedVar, sp, ec2Query))))));
      } else {
        fields.add(
            RecordField.of(
                field,
                LocalCallExpr.of(
                    "xml_child_text",
                    List.of(
                        Variable.of(xmlVar),
                        BinaryExpr.of(BeamXmlDecoder.memberElementName(member))))));
      }
    }
    if (fields.isEmpty()) {
      return RecordExpr.of(recordTag, List.of());
    }
    return RecordExpr.of(recordTag, fields);
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
    Expression listNameExpr = BinaryExpr.of(element);
    Shape listMember = model.expectShape(listShape.getMember().getTarget());
    if (listMember instanceof StructureShape nested) {
      Fun decodeFun =
          Fun.of(
              List.of(
                  FunClause.of(
                      VariablePattern.of("Item"),
                      buildDecodeStructureExpr(model, nested, "Item", sp, ec2Query))));
      return LocalCallExpr.of(
          "xml_child_struct_list",
          List.of(Variable.of(xmlVar), listNameExpr, BinaryExpr.of(itemElement), decodeFun));
    }
    return LocalCallExpr.of(
        "xml_child_list", List.of(Variable.of(xmlVar), listNameExpr, BinaryExpr.of(itemElement)));
  }

  private static List<MapEntry> buildOutputMapEntries(
      Model model, SymbolProvider sp, StructureShape output) {
    List<MapEntry> entries = new ArrayList<>();
    for (MemberShape member : output.members()) {
      String field = memberFieldName(sp, member);
      String element = BeamXmlDecoder.memberElementName(member);
      entries.add(MapEntry.of(BinaryExpr.of(element), Variable.of(toBindingVar(field))));
    }
    return entries;
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

  private static String recordName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }

  private static String toBindingVar(String snakeField) {
    return BeamNameUtils.toCamelCaseVariable(snakeField);
  }

  private static String memberFieldName(SymbolProvider sp, MemberShape member) {
    return sp.toSymbol(member).getProperty("fieldName", String.class).orElseThrow();
  }

  private static String xmlVarForElement(String element) {
    if (element.isEmpty()) {
      return "NestedXml";
    }
    return Character.toUpperCase(element.charAt(0)) + element.substring(1) + "Xml";
  }

  private static String operationWireName(OperationShape operation, ServiceShape service) {
    return operation.getId().getName(service);
  }
}
