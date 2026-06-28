package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsQueryFormEncoder;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamXmlBindingIndex;
import io.smithy.beam.core.BeamXmlDecoder;
import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExDoc;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExIntegerPattern;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExNil;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExPipeline;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStruct;
import io.smithy.beam.ir.elixir.ExStructAccess;
import io.smithy.beam.ir.elixir.ExStructFieldPattern;
import io.smithy.beam.ir.elixir.ExStructPattern;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;
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

final class ElixirAwsQueryOperationIr {
  private static final ExVarPattern W = ExVarPattern.var("_");
  private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

  private ElixirAwsQueryOperationIr() {}

  static ExFunction buildEncodeRequest(
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

    ExSpec spec =
        ExSpec.functionSpec(
            "encode_" + opName + "_request", inputType, "%" + runtimeMod + ".HttpRequest{}");
    ExStructPattern inputPattern =
        new ExStructPattern("Types." + inputStruct, inputFieldPatterns(input, sp), "input");

    List<ExExpr> body = new ArrayList<>();
    body.add(
        ExMatch.match(
            ExVarPattern.var("pairs"),
            ExOp.op(
                "++",
                ExList.list(
                    ExTuple.tuple(ExString.string("Action"), ExString.string(action)),
                    ExTuple.tuple(ExString.string("Version"), ExString.string(version))),
                ExCallLocal.callLocal("flatten_query_input", ExVar.var("input")))));
    body.add(
        ExMatch.match(
            ExVarPattern.var("body"),
            ExPipeline.pipeChain(
                ExVar.var("pairs"),
                ExCall.call("Enum", "reject", rejectNilPairsFn()),
                ExCall.call("Enum", "map", encodePairsFn()),
                ExCall.call("URI", "encode_query"))));
    body.add(buildHttpRequestStruct(runtimeMod, ExVar.var("body")));

    return ExFunction.functionWithDocAndSpec(
        "def",
        "encode_" + opName + "_request",
        ExDoc.doc("Encode AWS Query request for " + op.getId() + "."),
        spec,
        List.of(
            ExClause.blockClause(
                List.of(inputPattern), body.toArray(ExExpr[]::new))));
  }

  static ExFunction buildFlattenQueryInput(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      List<StructureShape> inputs,
      boolean ec2Query) {
    List<ExClause> clauses = new ArrayList<>();
    for (StructureShape input : inputs) {
      clauses.add(buildFlattenInputClause(model, httpIndex, sp, input, ec2Query));
    }
    return ExFunction.defpFunction("flatten_query_input", clauses);
  }

  static ExFunction buildDecodeResponse(
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

    ExSpec spec =
        ExSpec.functionSpec(
            "decode_" + opName + "_response",
            "map()",
            "{:ok, " + outputType + "} | {:error, term()}");

    ExStructPattern successPattern =
        new ExStructPattern(
            runtimeMod + ".HttpResponse",
            List.of(
                ExStructFieldPattern.fieldPattern("status", ExIntegerPattern.integer(200)),
                ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body"))));

    ExStructPattern fallbackPattern =
        new ExStructPattern(
            runtimeMod + ".HttpResponse",
            List.of(
                ExStructFieldPattern.fieldPattern("status", ExVarPattern.var("status")),
                ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body"))));

    return ExFunction.functionWithDocAndSpec(
        "def",
        "decode_" + opName + "_response",
        ExDoc.doc("Decode AWS Query response for " + op.getId() + "."),
        spec,
        List.of(
            ExClause.blockClause(
                List.of(successPattern),
                buildDecodeSuccessBody(model, output, outputStruct, resultElement, sp, ec2Query)),
            ExClause.blockClause(
                List.of(fallbackPattern),
                ExCallLocal.callLocal(
                    "decode_query_error", ExVar.var("status"), ExVar.var("body")))));
  }

  static ExFunction buildServerDecodeRequest(
      Model model,
      OperationShape op,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputType = ElixirTopDown.structureSpecType(typesMod, sp.toSymbol(input));

    ExSpec spec = ExSpec.functionSpec("decode_" + opName + "_request", "map()", inputType);
    ExStructPattern pattern =
        new ExStructPattern(
            runtimeMod + ".HttpRequest",
            List.of(ExStructFieldPattern.fieldPattern("body", ExVarPattern.var("body"))));

    List<ExExpr> body =
        List.of(
            ExPipeline.pipeChain(
                ExVar.var("body"),
                ExCallLocal.callLocal("parse_query_params"),
                ExCallLocal.callLocal(
                    "parse_" + recordName(sp.toSymbol(input)) + "_input")));

    return ExFunction.functionWithDocAndSpec(
        "def",
        "decode_" + opName + "_request",
        ExDoc.doc("Decode AWS Query server request for " + op.getId() + "."),
        spec,
        List.of(ExClause.blockClause(List.of(pattern), body.toArray(ExExpr[]::new))));
  }

  static ExFunction buildServerEncodeResponse(
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

    ExSpec spec =
        ExSpec.functionSpec(
            "encode_" + opName + "_response", outputType, "%" + runtimeMod + ".HttpResponse{}");
    ExStructPattern pattern =
        new ExStructPattern(
            "Types." + outputStruct, outputFieldPatterns(output, sp), "output");

    List<ExExpr> body = new ArrayList<>();
    body.add(
        ExMatch.match(
            ExVarPattern.var("result_content"),
            ExPipeline.pipeChain(
                ExCallLocal.callLocal(
                    recordName(sp.toSymbol(output)) + "_to_result_map", ExVar.var("output")),
                ExCall.call("Enum", "reject", rejectNilMapFn()),
                ExCall.call("Map", "new"))));
    if (ec2Query) {
      body.add(
          ExMatch.match(
              ExVarPattern.var("body"),
              ExCallLocal.callLocal(
                  "encode_xml",
                  ExMap.map(
                      ExMapEntry.entry(
                          ExString.string(resultElement), ExVar.var("result_content"))),
                  ExCallLocal.callLocal("xml_namespace"))));
    } else {
      body.add(
          ExMatch.match(
              ExVarPattern.var("body"),
              ExCallLocal.callLocal(
                  "wrap_aws_query_response",
                  ExString.string(resultElement),
                  ExVar.var("result_content"),
                  ExString.string(responseElement),
                  ExCallLocal.callLocal("xml_namespace"))));
    }
    body.add(
        ExStruct.struct(
            runtimeMod + ".HttpResponse",
            ExMapEntry.entry(ExAtom.atom("status"), ExInteger.integer(200)),
            ExMapEntry.entry(
                ExAtom.atom("headers"),
                ExList.list(
                    ExTuple.tuple(ExString.string("Content-Type"), ExString.string("text/xml")))),
            ExMapEntry.entry(ExAtom.atom("body"), ExVar.var("body"))));

    return ExFunction.functionWithDocAndSpec(
        "def",
        "encode_" + opName + "_response",
        ExDoc.doc("Encode AWS Query server response for " + op.getId() + "."),
        spec,
        List.of(ExClause.blockClause(List.of(pattern), body.toArray(ExExpr[]::new))));
  }

  static ExFunction buildParseInputFromForm(
      Model model, SymbolProvider sp, String typesMod, StructureShape input, boolean ec2Query) {
    String inputRecord = recordName(sp.toSymbol(input));
    List<MemberShape> members = new ArrayList<>(input.members());

    List<ExMapEntry> fields = new ArrayList<>();
    for (MemberShape member : members) {
      String field = fieldName(sp, member);
      String wireKey = queryFormKey(member, ec2Query);
      Shape target = model.expectShape(member.getTarget());
      ExExpr valueExpr;
      if (target instanceof ListShape) {
        valueExpr =
            ec2Query
                ? ExCallLocal.callLocal(
                    "form_list_values_ec2", ExVar.var("params"), ExString.string(wireKey))
                : ExCallLocal.callLocal(
                    "form_list_values_aws", ExVar.var("params"), ExString.string(wireKey));
      } else {
        valueExpr =
            ExCallLocal.callLocal("form_value", ExVar.var("params"), ExString.string(wireKey));
      }
      fields.add(ExMapEntry.entry(ExAtom.atom(field), valueExpr));
    }

    return ExFunction.defpFunction(
        "parse_" + inputRecord + "_input",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("params")),
                ExStruct.struct("Types." + structName(sp, input), fields))));
  }

  static ExFunction buildOutputToResultMap(
      Model model, SymbolProvider sp, StructureShape output) {
    String outputRecord = recordName(sp.toSymbol(output));
    List<ExMapEntry> entries = new ArrayList<>();
    for (MemberShape member : output.members()) {
      String field = fieldName(sp, member);
      String element = BeamXmlDecoder.memberElementName(member);
      entries.add(
          ExMapEntry.entry(
              ExString.string(element),
              ExStructAccess.structAccess(ExVar.var("output"), field)));
    }
    return ExFunction.defpFunction(
        outputRecord + "_to_result_map",
        List.of(
            ExClause.inlineClause(
                List.of(
                    new ExStructPattern(
                        "Types." + structName(sp, output), List.of(), "output")),
                ExMap.map(entries.toArray(ExMapEntry[]::new)))));
  }

  private static ExClause buildFlattenInputClause(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      StructureShape input,
      boolean ec2Query) {
    List<MemberShape> members = documentMembers(httpIndex, input);
    ExStructPattern pattern =
        new ExStructPattern("Types." + structName(sp, input), inputFieldPatterns(input, sp));

    ExExpr body;
    if (members.isEmpty()) {
      body = ExList.list();
    } else {
      List<ExExpr> memberCalls = new ArrayList<>();
      for (MemberShape member : members) {
        String field = fieldName(sp, member);
        String wireKey = queryFormKey(member, ec2Query);
        memberCalls.add(
            ExCallLocal.callLocal(
                "flatten_member", ExString.string(wireKey), ExVar.var(field)));
      }
      body =
          ExPipeline.pipeChain(
              ExList.list(memberCalls.toArray(ExExpr[]::new)), ExCall.call("List", "flatten"));
    }
    return ExClause.blockClause(List.of(pattern), body);
  }

  private static ExExpr buildDecodeSuccessBody(
      Model model,
      StructureShape output,
      String outputStruct,
      String resultElement,
      SymbolProvider sp,
      boolean ec2Query) {
    ExCallLocal unwrap =
        ExCallLocal.callLocal(
            "unwrap_query_result", ExVar.var("body"), ExString.string(resultElement));
    if (output.members().isEmpty()) {
      return ExCase.caseExpr(
          unwrap,
          ExCaseBranch.branch(
              ExTuplePattern.tuple(ExAtomPattern.atom("ok"), W),
              ExTuple.tuple(ExAtom.atom("ok"), ExStruct.struct("Types." + outputStruct, List.of()))),
          ExCaseBranch.branch(
              ExTuplePattern.tuple(
                  ExAtomPattern.atom("error"),
                  ExTuplePattern.tuple(ExAtomPattern.atom("missing_result"), W)),
              ExTuple.tuple(ExAtom.atom("ok"), ExStruct.struct("Types." + outputStruct, List.of()))),
          ExCaseBranch.branch(
              ExTuplePattern.tuple(ExAtomPattern.atom("error"), ExVarPattern.var("reason")),
              ExTuple.tuple(ExAtom.atom("error"), ExVar.var("reason"))));
    }
    List<ExMapEntry> fields = buildOutputStructFields(model, sp, output, "result", ec2Query);
    ExExpr okStruct = ExStruct.struct("Types." + outputStruct, fields);
    return ExCase.caseExpr(
        unwrap,
        ExCaseBranch.branch(
            ExTuplePattern.tuple(ExAtomPattern.atom("ok"), ExVarPattern.var("result")),
            ExTuple.tuple(ExAtom.atom("ok"), okStruct)),
        ExCaseBranch.branch(
            ExTuplePattern.tuple(ExAtomPattern.atom("error"), ExVarPattern.var("reason")),
            ExTuple.tuple(ExAtom.atom("error"), ExVar.var("reason"))));
  }

  private static List<ExMapEntry> buildOutputStructFields(
      Model model, SymbolProvider sp, StructureShape output, String resultVar, boolean ec2Query) {
    List<ExMapEntry> fields = new ArrayList<>();
    for (MemberShape member : output.members()) {
      String field = fieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof ListShape listShape) {
        String element = BeamXmlDecoder.memberElementName(member);
        String itemElement =
            listShape.getMember().hasTrait(XmlNameTrait.class)
                ? BeamXmlDecoder.memberElementName(listShape.getMember())
                : BeamXmlBindingIndex.listItemElementName(member, listShape, model);
        fields.add(
            ExMapEntry.entry(
                ExAtom.atom(field),
                ExCallLocal.callLocal(
                    "xml_child_list",
                    ExVar.var(resultVar),
                    ExString.string(element),
                    ExString.string(itemElement))));
      } else if (target instanceof StructureShape nested) {
        String element = BeamXmlDecoder.memberElementName(member);
        String nestedVar = xmlVarForElement(element);
        fields.add(
            ExMapEntry.entry(
                ExAtom.atom(field),
                ExCase.caseExpr(
                    ExCallLocal.callLocal(
                        "find_element",
                        ExString.string(element),
                        ExCallLocal.callLocal("element_content", ExVar.var(resultVar))),
                    ExCaseBranch.branch(ExNilPattern.nil(), ExNil.nil()),
                    ExCaseBranch.branch(
                        ExVarPattern.var(nestedVar),
                        buildDecodeStructureExpr(model, nested, nestedVar, sp, ec2Query)))));
      } else {
        fields.add(
            ExMapEntry.entry(
                ExAtom.atom(field),
                ExCallLocal.callLocal(
                    "xml_child_text",
                    ExVar.var(resultVar),
                    ExString.string(BeamXmlDecoder.memberElementName(member)))));
      }
    }
    return fields;
  }

  private static ExStruct buildDecodeStructureExpr(
      Model model, StructureShape structure, String xmlVar, SymbolProvider sp, boolean ec2Query) {
    List<ExMapEntry> fields = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String field = fieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof ListShape listShape) {
        String element = BeamXmlDecoder.memberElementName(member);
        String itemElement =
            listShape.getMember().hasTrait(XmlNameTrait.class)
                ? BeamXmlDecoder.memberElementName(listShape.getMember())
                : BeamXmlBindingIndex.listItemElementName(member, listShape, model);
        fields.add(
            ExMapEntry.entry(
                ExAtom.atom(field),
                ExCallLocal.callLocal(
                    "xml_child_list",
                    ExVar.var(xmlVar),
                    ExString.string(element),
                    ExString.string(itemElement))));
      } else if (target instanceof StructureShape nested) {
        String element = BeamXmlDecoder.memberElementName(member);
        String nestedVar = xmlVarForElement(element);
        fields.add(
            ExMapEntry.entry(
                ExAtom.atom(field),
                ExCase.caseExpr(
                    ExCallLocal.callLocal(
                        "find_element",
                        ExString.string(element),
                        ExCallLocal.callLocal("element_content", ExVar.var(xmlVar))),
                    ExCaseBranch.branch(ExNilPattern.nil(), ExNil.nil()),
                    ExCaseBranch.branch(
                        ExVarPattern.var(nestedVar),
                        buildDecodeStructureExpr(model, nested, nestedVar, sp, ec2Query)))));
      } else {
        fields.add(
            ExMapEntry.entry(
                ExAtom.atom(field),
                ExCallLocal.callLocal(
                    "xml_child_text",
                    ExVar.var(xmlVar),
                    ExString.string(BeamXmlDecoder.memberElementName(member)))));
      }
    }
    return ExStruct.struct("Types." + structName(sp, structure), fields);
  }

  private static ExStruct buildHttpRequestStruct(String runtimeMod, ExExpr body) {
    return ExStruct.struct(
        runtimeMod + ".HttpRequest",
        ExMapEntry.entry(ExAtom.atom("method"), ExString.string("POST")),
        ExMapEntry.entry(ExAtom.atom("path"), ExString.string("/")),
        ExMapEntry.entry(ExAtom.atom("query"), ExMap.map()),
        ExMapEntry.entry(
            ExAtom.atom("headers"),
            ExList.list(
                ExTuple.tuple(ExString.string("Content-Type"), ExString.string(CONTENT_TYPE)))),
        ExMapEntry.entry(ExAtom.atom("body"), body));
  }

  private static ExAnonymousFn rejectNilPairsFn() {
    return ExAnonymousFn.compactFn(
        ExClause.inlineClause(
            List.of(ExTuplePattern.tuple(W, ExVarPattern.var("v"))),
            ExOp.op("!=", ExVar.var("v"), ExAtom.atom("nil"))));
  }

  private static ExAnonymousFn encodePairsFn() {
    return ExAnonymousFn.compactFn(
        ExClause.inlineClause(
            List.of(ExTuplePattern.tuple(ExVarPattern.var("k"), ExVarPattern.var("v"))),
            ExTuple.tuple(ExVar.var("k"), ExCallLocal.callLocal("enc", ExVar.var("v")))));
  }

  private static ExAnonymousFn rejectNilMapFn() {
    return ExAnonymousFn.compactFn(
        ExClause.inlineClause(
            List.of(ExTuplePattern.tuple(W, ExVarPattern.var("v"))),
            ExOp.op("!=", ExVar.var("v"), ExAtom.atom("nil"))));
  }

  private static List<ExStructFieldPattern> inputFieldPatterns(
      StructureShape input, SymbolProvider sp) {
    List<ExStructFieldPattern> fields = new ArrayList<>();
    for (MemberShape member : input.members()) {
      String field = fieldName(sp, member);
      fields.add(ExStructFieldPattern.fieldPattern(field, ExVarPattern.var(field)));
    }
    return fields;
  }

  private static List<ExStructFieldPattern> outputFieldPatterns(
      StructureShape output, SymbolProvider sp) {
    List<ExStructFieldPattern> fields = new ArrayList<>();
    for (MemberShape member : output.members()) {
      String field = fieldName(sp, member);
      fields.add(ExStructFieldPattern.fieldPattern(field, ExVarPattern.var(field)));
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

  private static String recordName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }

  private static String fieldName(SymbolProvider sp, MemberShape member) {
    return BeamNameUtils.toSnakeCase(member.getMemberName());
  }

  private static String xmlVarForElement(String element) {
    if (element.isEmpty()) {
      return "nested_xml";
    }
    return BeamNameUtils.toSnakeCase(element) + "_xml";
  }

  private static String operationWireName(OperationShape operation, ServiceShape service) {
    return operation.getId().getName(service);
  }
}
