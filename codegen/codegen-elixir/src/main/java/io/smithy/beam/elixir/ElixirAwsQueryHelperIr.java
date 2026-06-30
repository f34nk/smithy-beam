package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamXmlDecoder;
import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExBinaryTemplate;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExConsPattern;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExFor;
import io.smithy.beam.ir.elixir.ExForFilter;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExIf;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExListPattern;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExNil;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExPipeline;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.ArrayList;
import java.util.List;

final class ElixirAwsQueryHelperIr {
  private ElixirAwsQueryHelperIr() {}

  private static final ExVarPattern W = ExVarPattern.var("_");

  static List<ExFunction> queryHelperFunctions(boolean ec2Query) {
    return List.of(flattenMember(ec2Query), flattenStructure(), enc());
  }

  static List<ExFunction> xmlHelperFunctions(boolean ec2Query) {
    List<ExFunction> functions = new ArrayList<>();
    functions.add(unwrapQueryResult());
    functions.add(normalizeXmlElement());
    functions.add(queryResultElement());
    functions.addAll(awsQueryXmlElementHelpers());
    functions.add(awsQueryXmlChildStructList());
    functions.add(awsQueryXmlChildList());
    functions.add(decodeQueryError(ec2Query));
    return functions;
  }

  static List<ExFunction> serverDecodeHelpers(boolean ec2Query) {
    List<ExFunction> functions = new ArrayList<>();
    functions.add(parseQueryParams());
    functions.add(formValue());
    functions.add(ec2Query ? formListValuesEc2() : formListValuesAws());
    functions.add(indexedFormValues());
    return functions;
  }

  static List<ExFunction> serverXmlEncodeHelpers(boolean ec2Query) {
    List<ExFunction> functions = new ArrayList<>();
    if (!ec2Query) {
      functions.add(wrapAwsQueryResponse());
    }
    functions.addAll(ElixirXmlCodecIr.restXmlEncodeHelpers());
    return functions;
  }

  private static List<ExFunction> awsQueryXmlElementHelpers() {
    return List.of(
        elementContent(),
        awsQueryFindElement(),
        isElement(),
        awsQueryElementName(),
        xmlChildText(),
        ElixirXmlCodecIr.elementText(),
        ElixirXmlCodecIr.isElementString());
  }

  private static ExFunction flattenMember(boolean ec2Query) {
    String listSuffix = ec2Query ? "." : ".member.";
    ExExpr listBody =
        ExCall.call("List", "flatten", flattenMemberListComprehension(listSuffix));
    ExExpr mapBody = ExCall.call("List", "flatten", flattenMemberMapComprehension());

    return ExFunction.defpFunction(
        "flatten_member",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("_key"), ExNilPattern.nil()), ExList.list()),
            ExClause.blockClause(
                List.of(ExVarPattern.var("key"), ExVarPattern.var("value")),
                List.of(ExGuard.guard("is_list", ExVar.var("value"))),
                listBody),
            ExClause.blockClause(
                List.of(ExVarPattern.var("key"), ExVarPattern.var("value")),
                List.of(ExGuard.guard("is_map", ExVar.var("value"))),
                mapBody),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("key"), ExVarPattern.var("value")),
                ExList.list(ExTuple.tuple(ExVar.var("key"), ExVar.var("value"))))));
  }

  private static ExFor flattenMemberListComprehension(String listSuffix) {
    return ExFor.forExpr(
        ExCallLocal.callLocal(
            "flatten_member", flattenMemberIndexedKey(listSuffix), ExVar.var("v")),
        ExTuplePattern.tuple(ExVarPattern.var("i"), ExVarPattern.var("v")),
        ExCall.call("Enum", "with_index", ExVar.var("value"), ExInteger.integer(1)),
        ExForFilter.filter(ExOp.op("!=", ExVar.var("v"), ExAtom.atom("nil"))));
  }

  private static ExFor flattenMemberMapComprehension() {
    return ExFor.forExpr(
        ExOp.op(
            "++",
            ExCallLocal.callLocal(
                "flatten_member", flattenMemberEntryKey(".key"), ExVar.var("k")),
            ExCallLocal.callLocal(
                "flatten_member", flattenMemberEntryKey(".value"), ExVar.var("v"))),
        ExTuplePattern.tuple(
            ExVarPattern.var("i"),
            ExTuplePattern.tuple(ExVarPattern.var("k"), ExVarPattern.var("v"))),
        ExCall.call(
            "Enum",
            "with_index",
            ExCall.call("Map", "to_list", ExVar.var("value")),
            ExInteger.integer(1)),
        ExForFilter.filter(ExOp.op("!=", ExVar.var("k"), ExAtom.atom("nil"))),
        ExForFilter.filter(ExOp.op("!=", ExVar.var("v"), ExAtom.atom("nil"))));
  }

  private static ExBinaryTemplate flattenMemberIndexedKey(String listSuffix) {
    return ExBinaryTemplate.binaryTemplate(
        ExVar.var("key"),
        ExString.string(listSuffix),
        ExCall.call("Integer", "to_string", ExVar.var("i")));
  }

  private static ExBinaryTemplate flattenMemberEntryKey(String suffix) {
    return ExBinaryTemplate.binaryTemplate(
        ExVar.var("key"),
        ExString.string(".entry."),
        ExCall.call("Integer", "to_string", ExVar.var("i")),
        ExString.string(suffix));
  }

  private static ExFunction flattenStructure() {
    return ExFunction.defpFunction(
        "flatten_structure",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("_key"), ExNilPattern.nil()), ExList.list()),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("_key"), ExVarPattern.var("_value")), ExList.list())));
  }

  private static ExFunction enc() {
    return ExFunction.defpFunction(
        "enc",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("value")),
                List.of(ExGuard.guard("is_boolean", ExVar.var("value"))),
                ExCall.call("Atom", "to_string", ExVar.var("value"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("value")),
                List.of(ExGuard.guard("is_integer", ExVar.var("value"))),
                ExCall.call("Integer", "to_string", ExVar.var("value"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("value")),
                List.of(ExGuard.guard("is_float", ExVar.var("value"))),
                ExCall.call("Float", "to_string", ExVar.var("value"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("value")),
                List.of(ExGuard.guard("is_binary", ExVar.var("value"))),
                ExVar.var("value")),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("value")),
                List.of(ExGuard.guard("is_atom", ExVar.var("value"))),
                ExCall.call("Atom", "to_string", ExVar.var("value")))));
  }

  private static ExFunction parseQueryParams() {
    return ExFunction.defpFunction(
        "parse_query_params",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("body")),
                ExPipeline.pipeChain(
                    ExVar.var("body"),
                    ExCall.call("URI", "decode_query"),
                    ExCall.call("Map", "new")))));
  }

  private static ExFunction formValue() {
    return ExFunction.defpFunction(
        "form_value",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("params"), ExVarPattern.var("key")),
                ExCall.call("Map", "get", ExVar.var("params"), ExVar.var("key")))));
  }

  private static ExFunction formListValuesAws() {
    return ExFunction.defpFunction(
        "form_list_values_aws",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("params"), ExVarPattern.var("key")),
                ExMatch.match(
                    ExVarPattern.var("prefix"),
                    ExBinaryTemplate.binaryTemplate(
                        ExVar.var("key"), ExString.string(".member."))),
                ExCallLocal.callLocal(
                    "indexed_form_values", ExVar.var("params"), ExVar.var("prefix")))));
  }

  private static ExFunction formListValuesEc2() {
    return ExFunction.defpFunction(
        "form_list_values_ec2",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("params"), ExVarPattern.var("key")),
                ExMatch.match(
                    ExVarPattern.var("prefix"),
                    ExBinaryTemplate.binaryTemplate(ExVar.var("key"), ExString.string("."))),
                ExCallLocal.callLocal(
                    "indexed_form_values", ExVar.var("params"), ExVar.var("prefix")))));
  }

  private static ExFunction indexedFormValues() {
    ExAnonymousFn startsWithFn =
        ExAnonymousFn.compactFn(
            ExClause.inlineClause(
                List.of(ExTuplePattern.tuple(ExVarPattern.var("k"), W)),
                ExCall.call(
                    "String",
                    "starts_with?",
                    ExVar.var("k"),
                    ExVar.var("prefix"))));
    ExAnonymousFn sortFn =
        ExAnonymousFn.compactFn(
            ExClause.inlineClause(
                List.of(ExTuplePattern.tuple(ExVarPattern.var("k"), W)),
                ExCall.call(
                    "String",
                    "to_integer",
                    ExCall.call(
                        "String",
                        "replace_prefix",
                        ExVar.var("k"),
                        ExVar.var("prefix"),
                        ExString.string("")))));
    ExAnonymousFn mapFn =
        ExAnonymousFn.compactFn(
            ExClause.inlineClause(
                List.of(ExTuplePattern.tuple(W, ExVarPattern.var("v"))), ExVar.var("v")));
    return ExFunction.defpFunction(
        "indexed_form_values",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("params"), ExVarPattern.var("prefix")),
                ExPipeline.pipeline(
                    "values",
                    ExVar.var("params"),
                    ExCall.call("Enum", "filter", startsWithFn),
                    ExCall.call("Enum", "sort_by", sortFn),
                    ExCall.call("Enum", "map", mapFn)),
                ExCase.caseExpr(
                    ExVar.var("values"),
                    ExCaseBranch.branch(ExListPattern.list(), ExNil.nil()),
                    ExCaseBranch.branch(ExVarPattern.var("values"), ExVar.var("values"))))));
  }

  private static ExFunction wrapAwsQueryResponse() {
    return ExFunction.defpFunction(
        "wrap_aws_query_response",
        List.of(
            ExClause.inlineClause(
                List.of(
                    ExVarPattern.var("result_name"),
                    ExVarPattern.var("result_content"),
                    ExVarPattern.var("response_name"),
                    ExVarPattern.var("xml_ns")),
                ExCallLocal.callLocal(
                    "encode_xml",
                    ExMap.map(
                        ExMapEntry.entry(
                            ExVar.var("response_name"),
                            ExMap.map(
                                ExMapEntry.entry(
                                    ExVar.var("result_name"), ExVar.var("result_content"))))),
                    ExVar.var("xml_ns")))));
  }

  private static ExFunction unwrapQueryResult() {
    ExTuplePattern xmlElementPattern =
        ExTuplePattern.tuple(
            ExAtomPattern.atom("xmlElement"),
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W);

    ExCase resultLookup =
        ExCase.caseExpr(
            ExCallLocal.callLocal(
                "query_result_element", ExVar.var("root"), ExVar.var("result_name")),
            ExCaseBranch.branch(ExNilPattern.nil(), errorMissingResult()),
            ExCaseBranch.branch(
                ExVarPattern.var("result"),
                ExTuple.tuple(ExAtom.atom("ok"), ExVar.var("result"))));

    ExCase scanCase =
        ExCase.caseExpr(
            ExCall.call(
                ":xmerl_scan",
                "string",
                ExCall.call(":erlang", "binary_to_list", ExVar.var("body"))),
            ExCaseBranch.branch(
                ExTuplePattern.tuple(
                    ExTuplePattern.tuple(xmlElementPattern, ExVarPattern.var("xml")), W),
                ExExprBlock.block(
                    ExMatch.match(
                        ExVarPattern.var("root"), normalizeXmlElementBody(ExVar.var("xml"))),
                    resultLookup)),
            ExCaseBranch.branch(W, ExTuple.tuple(ExAtom.atom("error"), ExAtom.atom("xml_parse_error"))));

    return ExFunction.defpFunction(
        "unwrap_query_result",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("body"), ExVarPattern.var("result_name")), scanCase)));
  }

  private static ExTuple errorMissingResult() {
    return ExTuple.tuple(
        ExAtom.atom("error"),
        ExTuple.tuple(ExAtom.atom("missing_result"), ExVar.var("result_name")));
  }

  private static ExCallLocal normalizeXmlElementBody(ExExpr element) {
    return ExCallLocal.callLocal("normalize_xml_element", element);
  }

  private static ExFunction normalizeXmlElement() {
    return ExFunction.defpFunction(
        "normalize_xml_element",
        List.of(
            ExClause.blockClause(
                List.of(ExConsPattern.consPattern(ExVarPattern.var("h"), W)),
                ExCallLocal.callLocal("normalize_xml_element", ExVar.var("h"))),
            ExClause.inlineClause(List.of(ExVarPattern.var("element")), ExVar.var("element"))));
  }

  private static ExFunction queryResultElement() {
    return ExFunction.defpFunction(
        "query_result_element",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("element"), ExVarPattern.var("result_name")),
                ExIf.ifBlock(
                    ExOp.op(
                        "and",
                        ExCallLocal.callLocal("is_element", ExVar.var("element")),
                        ExOp.op(
                            "==",
                            ExCallLocal.callLocal("element_name", ExVar.var("element")),
                            ExVar.var("result_name"))),
                    ExVar.var("element"),
                    ExCallLocal.callLocal(
                        "find_element",
                        ExVar.var("result_name"),
                        ExCallLocal.callLocal("element_content", ExVar.var("element")))))));
  }

  private static ExFunction elementContent() {
    ExTuplePattern xmlElementContent =
        ExTuplePattern.tuple(
            ExAtomPattern.atom("xmlElement"),
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            ExVarPattern.var("content"),
            W,
            W,
            W);
    ExTuplePattern sixTupleContent =
        ExTuplePattern.tuple(W, W, ExVarPattern.var("content"), W, W, W);

    return ExFunction.defpFunction(
        "element_content",
        List.of(
            ExClause.inlineClause(List.of(xmlElementContent), ExVar.var("content")),
            ExClause.inlineClause(
                List.of(sixTupleContent),
                List.of(ExGuard.guard("is_list", ExVar.var("content"))),
                ExVar.var("content")),
            ExClause.blockClause(
                List.of(ExConsPattern.consPattern(ExVarPattern.var("h"), W)),
                ExCallLocal.callLocal("element_content", ExVar.var("h"))),
            ExClause.inlineClause(List.of(W), ExList.list())));
  }

  private static ExFunction awsQueryFindElement() {
    ExAnonymousFn findFn =
        ExAnonymousFn.compactFn(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("item")),
                ExOp.op(
                    "and",
                    ExCallLocal.callLocal("is_element", ExVar.var("item")),
                    ExOp.op(
                        "==",
                        ExCallLocal.callLocal("element_name", ExVar.var("item")),
                        ExVar.var("name")))));

    return ExFunction.defpFunction(
        "find_element",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("name"), ExVarPattern.var("content")),
                ExCall.call("Enum", "find", ExVar.var("content"), findFn))));
  }

  private static ExFunction isElement() {
    ExTuplePattern xmlElement =
        ExTuplePattern.tuple(
            ExAtomPattern.atom("xmlElement"),
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W);
    ExTuplePattern sixTuple =
        ExTuplePattern.tuple(W, W, ExVarPattern.var("content"), W, W, W);

    return ExFunction.defpFunction(
        "is_element",
        List.of(
            ExClause.inlineClause(List.of(xmlElement), ExAtom.atom("true")),
            ExClause.inlineClause(
                List.of(sixTuple),
                List.of(ExGuard.guard("is_list", ExVar.var("content"))),
                ExAtom.atom("true")),
            ExClause.inlineClause(List.of(W), ExAtom.atom("false"))));
  }

  private static ExFunction awsQueryElementName() {
    ExTuplePattern xmlElementName =
        ExTuplePattern.tuple(
            ExAtomPattern.atom("xmlElement"),
            ExVarPattern.var("name"),
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W);
    ExTuplePattern sixTupleName =
        ExTuplePattern.tuple(ExVarPattern.var("name"), W, W, W, W, W);

    return ExFunction.defpFunction(
        "element_name",
        List.of(
            ExClause.inlineClause(
                List.of(xmlElementName),
                List.of(ExGuard.guard("is_atom", ExVar.var("name"))),
                ExCall.call("Atom", "to_string", ExVar.var("name"))),
            ExClause.inlineClause(
                List.of(xmlElementName),
                List.of(ExGuard.guard("is_list", ExVar.var("name"))),
                ExCall.call("List", "to_string", ExVar.var("name"))),
            ExClause.inlineClause(
                List.of(xmlElementName),
                List.of(ExGuard.guard("is_binary", ExVar.var("name"))),
                ExVar.var("name")),
            ExClause.inlineClause(
                List.of(sixTupleName),
                List.of(ExGuard.guard("is_atom", ExVar.var("name"))),
                ExCall.call("Atom", "to_string", ExVar.var("name"))),
            ExClause.inlineClause(
                List.of(sixTupleName),
                List.of(ExGuard.guard("is_list", ExVar.var("name"))),
                ExCall.call("List", "to_string", ExVar.var("name"))),
            ExClause.inlineClause(
                List.of(sixTupleName),
                List.of(ExGuard.guard("is_binary", ExVar.var("name"))),
                ExVar.var("name"))));
  }

  private static ExFunction xmlChildText() {
    return ExFunction.defpFunction(
        "xml_child_text",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("parent"), ExVarPattern.var("name")),
                ExCase.caseExpr(
                    ExCallLocal.callLocal(
                        "find_element",
                        ExVar.var("name"),
                        ExCallLocal.callLocal("element_content", ExVar.var("parent"))),
                    ExCaseBranch.branch(ExNilPattern.nil(), ExNil.nil()),
                    ExCaseBranch.branch(
                        ExVarPattern.var("element"),
                        ExCase.caseExpr(
                            ExCallLocal.callLocal("element_text", ExVar.var("element")),
                            ExCaseBranch.branch(ExListPattern.list(), ExNil.nil()),
                            ExCaseBranch.branch(
                                ExListPattern.cons(ExVarPattern.var("text"), W),
                                ExCall.call("List", "to_string", ExVar.var("text")))))))));
  }

  private static ExFunction awsQueryXmlChildStructList() {
    ExAnonymousFn filterFn =
        ExAnonymousFn.compactFn(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("item")),
                ExOp.op(
                    "and",
                    ExCallLocal.callLocal("is_element", ExVar.var("item")),
                    ExOp.op(
                        "==",
                        ExCallLocal.callLocal("element_name", ExVar.var("item")),
                        ExVar.var("item_name")))));

    ExPipeline decodePipeline =
        ExPipeline.pipeChain(
            ExCallLocal.callLocal("element_content", ExVar.var("list_element")),
            ExCall.call("Enum", "filter", filterFn),
            ExCall.call("Enum", "map", ExVar.var("decode_fun")));

    return ExFunction.defpFunction(
        "xml_child_struct_list",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("parent"),
                    ExVarPattern.var("list_name"),
                    ExVarPattern.var("item_name"),
                    ExVarPattern.var("decode_fun")),
                ExCase.caseExpr(
                    ExCallLocal.callLocal(
                        "find_element",
                        ExVar.var("list_name"),
                        ExCallLocal.callLocal("element_content", ExVar.var("parent"))),
                    ExCaseBranch.branch(ExNilPattern.nil(), ExNil.nil()),
                    ExCaseBranch.branch(ExVarPattern.var("list_element"), decodePipeline)))));
  }

  private static ExFunction awsQueryXmlChildList() {
    ExAnonymousFn mapFn =
        ExAnonymousFn.fn(
            ExClause.blockClause(
                List.of(ExVarPattern.var("item")),
                ExCase.caseExpr(
                    ExCallLocal.callLocal("element_text", ExVar.var("item")),
                    ExCaseBranch.branch(ExListPattern.list(), ExNil.nil()),
                    ExCaseBranch.branch(
                        ExListPattern.cons(ExVarPattern.var("text"), W),
                        ExCall.call("List", "to_string", ExVar.var("text"))))));

    ExAnonymousFn rejectFn =
        ExAnonymousFn.compactFn(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("x")),
                ExCall.call("Kernel", "is_nil", ExVar.var("x"))));

    ExPipeline childPipeline =
        ExPipeline.pipeChain(
            ExCallLocal.callLocal("element_content", ExVar.var("list_element")),
            ExCall.call("Enum", "filter", filterFnForChildList()),
            ExCall.call("Enum", "map", mapFn),
            ExCall.call("Enum", "reject", rejectFn));

    return ExFunction.defpFunction(
        "xml_child_list",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("parent"),
                    ExVarPattern.var("list_name"),
                    ExVarPattern.var("item_name")),
                ExCase.caseExpr(
                    ExCallLocal.callLocal(
                        "find_element",
                        ExVar.var("list_name"),
                        ExCallLocal.callLocal("element_content", ExVar.var("parent"))),
                    ExCaseBranch.branch(ExNilPattern.nil(), ExNil.nil()),
                    ExCaseBranch.branch(ExVarPattern.var("list_element"), childPipeline)))));
  }

  private static ExAnonymousFn filterFnForChildList() {
    return ExAnonymousFn.compactFn(
        ExClause.inlineClause(
            List.of(ExVarPattern.var("item")),
            ExOp.op(
                "and",
                ExCallLocal.callLocal("is_element", ExVar.var("item")),
                ExOp.op(
                    "==",
                    ExCallLocal.callLocal("element_name", ExVar.var("item")),
                    ExVar.var("item_name")))));
  }

  private static ExFunction decodeQueryError(boolean ec2Query) {
    if (ec2Query) {
      return decodeEc2QueryError();
    }
    return decodeAwsQueryError();
  }

  private static ExFunction decodeAwsQueryError() {
    return ExFunction.defpFunction(
        "decode_query_error",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("status"), ExVarPattern.var("body")),
                decodeQueryErrorBody(
                    BeamXmlDecoder.ERROR_RESPONSE_ELEMENT,
                    BeamXmlDecoder.ERROR_ELEMENT,
                    BeamXmlDecoder.ERROR_CODE_ELEMENT,
                    BeamXmlDecoder.ERROR_MESSAGE_ELEMENT))));
  }

  private static ExFunction decodeEc2QueryError() {
    return ExFunction.defpFunction(
        "decode_query_error",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("status"), ExVarPattern.var("body")),
                decodeQueryErrorBodyEc2())));
  }

  private static ExCase decodeQueryErrorBody(
      String responseElement, String errorElement, String codeElement, String messageElement) {
    ExCase errorLookup =
        ExCase.caseExpr(
            ExCallLocal.callLocal(
                "find_element",
                ExString.string(errorElement),
                ExCallLocal.callLocal("element_content", ExVar.var("error_response"))),
            ExCaseBranch.branch(W, unknownQueryError()),
            ExCaseBranch.branch(
                ExVarPattern.var("error"),
                ExTuple.tuple(
                    ExAtom.atom("error"),
                    ExTuple.tuple(
                        ExCallLocal.callLocal(
                            "xml_child_text", ExVar.var("error"), ExString.string(codeElement)),
                        ExCallLocal.callLocal(
                            "xml_child_text",
                            ExVar.var("error"),
                            ExString.string(messageElement))))));

    ExCase responseLookup =
        ExCase.caseExpr(
            ExCallLocal.callLocal(
                "query_result_element",
                ExVar.var("xml"),
                ExString.string(responseElement)),
            ExCaseBranch.branch(W, unknownQueryError()),
            ExCaseBranch.branch(ExVarPattern.var("error_response"), errorLookup));

    return scanAndLookup(responseLookup);
  }

  private static ExCase decodeQueryErrorBodyEc2() {
    ExCase errorLookup =
        ExCase.caseExpr(
            ExCallLocal.callLocal(
                "find_element",
                ExString.string(BeamXmlDecoder.ERROR_ELEMENT),
                ExCallLocal.callLocal("element_content", ExVar.var("error"))),
            ExCaseBranch.branch(W, unknownQueryError()),
            ExCaseBranch.branch(
                ExVarPattern.var("error"),
                ExTuple.tuple(
                    ExAtom.atom("error"),
                    ExTuple.tuple(
                        ExCallLocal.callLocal(
                            "xml_child_text",
                            ExVar.var("error"),
                            ExString.string(BeamXmlDecoder.ERROR_CODE_ELEMENT)),
                        ExCallLocal.callLocal(
                            "xml_child_text",
                            ExVar.var("error"),
                            ExString.string(BeamXmlDecoder.ERROR_MESSAGE_ELEMENT))))));

    ExCase errorsLookup =
        ExCase.caseExpr(
            ExCallLocal.callLocal(
                "find_element",
                ExString.string(BeamXmlDecoder.EC2_ERRORS_ELEMENT),
                ExCallLocal.callLocal("element_content", ExVar.var("response"))),
            ExCaseBranch.branch(W, unknownQueryError()),
            ExCaseBranch.branch(
                ExVarPattern.var("errors"),
                ExCase.caseExpr(
                    ExCallLocal.callLocal(
                        "find_element",
                        ExString.string(BeamXmlDecoder.ERROR_ELEMENT),
                        ExCallLocal.callLocal("element_content", ExVar.var("errors"))),
                    ExCaseBranch.branch(W, unknownQueryError()),
                    ExCaseBranch.branch(ExVarPattern.var("error"), errorLookup))));

    ExCase responseLookup =
        ExCase.caseExpr(
            ExCallLocal.callLocal(
                "query_result_element",
                ExVar.var("root"),
                ExString.string(BeamXmlDecoder.EC2_RESPONSE_ELEMENT)),
            ExCaseBranch.branch(W, unknownQueryError()),
            ExCaseBranch.branch(ExVarPattern.var("response"), errorsLookup));

    return scanAndLookup(responseLookup);
  }

  private static ExCase scanAndLookup(ExCase lookup) {
    ExTuplePattern xmlElementPattern =
        ExTuplePattern.tuple(
            ExAtomPattern.atom("xmlElement"),
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            W);

    return ExCase.caseExpr(
        ExCall.call(
            ":xmerl_scan",
            "string",
            ExCall.call(":erlang", "binary_to_list", ExVar.var("body"))),
        ExCaseBranch.branch(
            ExTuplePattern.tuple(
                ExTuplePattern.tuple(xmlElementPattern, ExVarPattern.var("xml")), W),
            ExExprBlock.block(
                ExMatch.match(ExVarPattern.var("root"), normalizeXmlElementBody(ExVar.var("xml"))),
                lookup)),
        ExCaseBranch.branch(W, unknownQueryError()));
  }

  private static ExTuple unknownQueryError() {
    return ExTuple.tuple(
        ExAtom.atom("error"),
        ExTuple.tuple(ExAtom.atom("unknown_error"), ExVar.var("status"), ExVar.var("body")));
  }
}
