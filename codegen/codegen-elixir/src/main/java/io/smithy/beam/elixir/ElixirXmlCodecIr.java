package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExConsPattern;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExFor;
import io.smithy.beam.ir.elixir.ExForFilter;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExIf;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExListPattern;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMapFieldPattern;
import io.smithy.beam.ir.elixir.ExMapPattern;
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
import java.util.Optional;

final class ElixirXmlCodecIr {
  private ElixirXmlCodecIr() {}

  private static final ExVarPattern W = ExVarPattern.var("_");

  static List<ExFunction> restXmlDecodeHelpers() {
    List<ExFunction> functions = new ArrayList<>();
    functions.add(parseXmlRoot());
    functions.add(decodePayload());
    functions.add(xmlElementNamed());
    functions.add(elementContent());
    functions.add(findElement());
    functions.add(isElement());
    functions.add(elementName());
    functions.add(xmlChildText());
    functions.add(elementText());
    functions.add(collectText());
    functions.add(isElementString());
    functions.add(xmlChildList());
    functions.add(xmlChildStructList());
    return functions;
  }

  static List<ExFunction> restXmlEncodeHelpers() {
    return List.of(
        encodeXml(),
        buildXmlElement(),
        buildXmlChild(),
        xmlNamespaceAttrs(),
        ElixirCodecHelperIr.encodeQueryValueXmlQuery(),
        ElixirCodecHelperIr.toBinary(ElixirCodecHelperIr.ToBinaryVariant.XML_QUERY));
  }

  static ExFunction xmlChildList() {
    return ExFunction.defpFunction(
        "xml_child_list",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("parent"),
                    ExNilPattern.nil(),
                    ExVarPattern.var("item_name")),
                childListPipeline(ExVar.var("parent"))),
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
                    ExCaseBranch.branch(
                        ExVarPattern.var("list_element"),
                        childListPipeline(ExVar.var("list_element")))))));
  }

  static ExFunction xmlNamespace(Optional<String> namespaceUri) {
    if (namespaceUri.isPresent()) {
      return ExFunction.defpFunction(
          "xml_namespace",
          List.of(
              ExClause.inlineClause(
                  List.of(),
                  ExMap.map(
                      ExMapEntry.entry(
                          ExAtom.atom("uri"), ExString.string(namespaceUri.get()))))));
    }
    return ExFunction.defpFunction(
        "xml_namespace", List.of(ExClause.inlineClause(List.of(), ExMap.map())));
  }

  private static ExPipeline childListPipeline(ExExpr root) {
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
    return ExPipeline.pipeChain(
        root,
        ExCallLocal.callLocal("element_content"),
        ExCall.call("Enum", "filter", filterFn),
        ExCall.call("Enum", "map", mapFn),
        ExCall.call("Enum", "reject", rejectFn));
  }

  static ExFunction elementText() {
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

    return ExFunction.defpFunction(
        "element_text",
        List.of(
            ExClause.blockClause(
                List.of(xmlElementContent),
                ExCall.call(
                    "Enum",
                    "flat_map",
                    ExVar.var("content"),
                    ExOp.prefix("&", ExCallLocal.callLocal("collect_text", ExVar.var("_"))))),
            ExClause.inlineClause(List.of(W), ExList.list())));
  }

  private static ExFunction collectText() {
    return ExFunction.defpFunction(
        "collect_text",
        List.of(
            ExClause.inlineClause(
                List.of(
                    ExTuplePattern.tuple(
                        ExAtomPattern.atom("xmlText"),
                        W,
                        W,
                        W,
                        ExVarPattern.var("text"),
                        W)),
                ExList.list(ExVar.var("text"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("text")),
                List.of(ExGuard.guard("is_list", ExVar.var("text"))),
                ExList.list(ExVar.var("text"))),
            ExClause.inlineClause(List.of(W), ExList.list())));
  }

  static ExFunction isElementString() {
    return ExFunction.defpFunction(
        "is_element_string",
        List.of(
            ExClause.inlineClause(
                List.of(
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
                        W)),
                ExAtom.atom("true")),
            ExClause.inlineClause(List.of(W), ExAtom.atom("false"))));
  }

  private static ExFunction encodeXml() {
    return ExFunction.defpFunction(
        "encode_xml",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("root_map"), ExVarPattern.var("xml_ns")),
                ExMatch.match(
                    ExConsPattern.consPattern(
                        ExTuplePattern.tuple(
                            ExVarPattern.var("root_name"), ExVarPattern.var("content")),
                        ExNilPattern.nil()),
                    ExCall.call("Map", "to_list", ExVar.var("root_map"))),
                ExMatch.match(
                    ExVarPattern.var("element"),
                    ExCallLocal.callLocal(
                        "build_xml_element",
                        ExVar.var("root_name"),
                        ExVar.var("content"),
                        ExVar.var("xml_ns"))),
                ExPipeline.pipeChain(
                    ExCall.call(
                        ":xmerl",
                        "export_simple",
                        ExList.list(ExVar.var("element")),
                        ExAtom.atom("xmerl_xmlns"),
                        ExList.list(),
                        ExList.list(
                            ExTuple.tuple(ExAtom.atom("prolog"), ExAtom.atom("false")))),
                    ExCallLocal.callLocal(":erlang.iolist_to_binary")))));
  }

  private static ExFunction buildXmlElement() {
    ExFor childFor =
        ExFor.forExpr(
            ExCallLocal.callLocal("build_xml_child", ExVar.var("k"), ExVar.var("v")),
            ExTuplePattern.tuple(ExVarPattern.var("k"), ExVarPattern.var("v")),
            ExCall.call("Map", "to_list", ExVar.var("content")),
            ExForFilter.filter(ExOp.op("!=", ExVar.var("v"), ExAtom.atom("nil"))));

    return ExFunction.defpFunction(
        "build_xml_element",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("name"),
                    ExVarPattern.var("content"),
                    ExVarPattern.var("xml_ns")),
                List.of(ExGuard.guard("is_map", ExVar.var("content"))),
                ExMatch.match(
                    ExVarPattern.var("attrs"),
                    ExCallLocal.callLocal("xml_namespace_attrs", ExVar.var("xml_ns"))),
                ExMatch.match(ExVarPattern.var("children"), childFor),
                ExTuple.tuple(ExVar.var("name"), ExVar.var("attrs"), ExVar.var("children"))),
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("name"),
                    ExVarPattern.var("content"),
                    ExVarPattern.var("xml_ns")),
                ExTuple.tuple(
                    ExVar.var("name"),
                    ExCallLocal.callLocal("xml_namespace_attrs", ExVar.var("xml_ns")),
                    ExList.list(
                        ExTuple.tuple(
                            ExAtom.atom("text"),
                            ExCallLocal.callLocal("to_binary", ExVar.var("content"))))))));
  }

  private static ExFunction buildXmlChild() {
    ExFor mapChildFor =
        ExFor.forExpr(
            ExCallLocal.callLocal(
                "build_xml_element", ExVar.var("k"), ExVar.var("v"), ExMap.map()),
            ExTuplePattern.tuple(ExVarPattern.var("k"), ExVarPattern.var("v")),
            ExCall.call("Map", "to_list", ExVar.var("value")),
            ExForFilter.filter(ExOp.op("!=", ExVar.var("v"), ExAtom.atom("nil"))));

    ExFor listChildFor =
        ExFor.forExpr(
            ExCallLocal.callLocal(
                "build_xml_element", ExAtom.atom("member"), ExVar.var("v"), ExMap.map()),
            ExVarPattern.var("v"),
            ExVar.var("values"),
            ExForFilter.filter(ExOp.op("!=", ExVar.var("v"), ExAtom.atom("nil"))));

    return ExFunction.defpFunction(
        "build_xml_child",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("name"), ExVarPattern.var("value")),
                List.of(ExGuard.guard("is_map", ExVar.var("value"))),
                ExMatch.match(ExVarPattern.var("children"), mapChildFor),
                ExTuple.tuple(ExVar.var("name"), ExList.list(), ExVar.var("children"))),
            ExClause.blockClause(
                List.of(ExVarPattern.var("name"), ExVarPattern.var("values")),
                List.of(ExGuard.guard("is_list", ExVar.var("values"))),
                ExMatch.match(ExVarPattern.var("children"), listChildFor),
                ExTuple.tuple(ExVar.var("name"), ExList.list(), ExVar.var("children"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("name"), ExVarPattern.var("value")),
                ExTuple.tuple(
                    ExVar.var("name"),
                    ExList.list(),
                    ExList.list(
                        ExTuple.tuple(
                            ExAtom.atom("text"),
                            ExCallLocal.callLocal("to_binary", ExVar.var("value"))))))));
  }

  private static ExFunction xmlNamespaceAttrs() {
    return ExFunction.defpFunction(
        "xml_namespace_attrs",
        List.of(
            ExClause.inlineClause(
                List.of(
                    ExMapPattern.map(
                        ExMapFieldPattern.field(ExAtom.atom("uri"), ExVarPattern.var("uri")))),
                ExList.list(ExTuple.tuple(ExAtom.atom("xmlns"), ExVar.var("uri")))),
            ExClause.inlineClause(List.of(W), ExList.list())));
  }

  private static ExFunction parseXmlRoot() {
    return ExFunction.defpFunction(
        "parse_xml_root",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("body"), ExVarPattern.var("root_name")),
                ExCapturedBlock.capturedBlock(
                    """
                    try do
                      {xml, _} = :xmerl_scan.string(String.to_charlist(body))
                      cond do
                        xml_element_named(xml, root_name) -> {:ok, xml}
                        true ->
                          case find_element(root_name, element_content(xml)) do
                            nil -> {:error, {:missing_root, root_name}}
                            root -> {:ok, root}
                          end
                      end
                    rescue
                      reason -> {:error, {:xml_parse_error, reason}}
                    end"""))));
  }

  private static ExFunction decodePayload() {
    return ExFunction.defpFunction(
        "decode_payload",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("body"), ExVarPattern.var("root_name")),
                ExCase.caseExpr(
                    ExCallLocal.callLocal("parse_xml_root", ExVar.var("body"), ExVar.var("root_name")),
                    ExCaseBranch.branch(
                        ExTuplePattern.tuple(ExAtomPattern.atom("ok"), W), ExNil.nil()),
                    ExCaseBranch.branch(
                        ExTuplePattern.tuple(ExAtomPattern.atom("error"), W), ExNil.nil())))));
  }

  private static ExFunction xmlElementNamed() {
    return ExFunction.defpFunction(
        "xml_element_named",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("element"), ExVarPattern.var("name")),
                ExOp.op(
                    "and",
                    ExCallLocal.callLocal("is_element", ExVar.var("element")),
                    ExOp.op(
                        "==",
                        ExCallLocal.callLocal("element_name", ExVar.var("element")),
                        ExVar.var("name"))))));
  }

  private static ExFunction elementContent() {
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
            ExVarPattern.var("content"),
            W,
            W,
            W);
    ExTuplePattern sixTuple =
        ExTuplePattern.tuple(W, W, ExVarPattern.var("content"), W, W, W);
    ExConsPattern headList = ExConsPattern.consPattern(ExVarPattern.var("h"), W);

    return ExFunction.defpFunction(
        "element_content",
        List.of(
            ExClause.inlineClause(List.of(xmlElement), ExVar.var("content")),
            ExClause.inlineClause(
                List.of(sixTuple),
                List.of(ExGuard.guard("is_list", ExVar.var("content"))),
                ExVar.var("content")),
            ExClause.inlineClause(
                List.of(headList),
                ExCallLocal.callLocal("element_content", ExVar.var("h"))),
            ExClause.inlineClause(List.of(W), ExList.list())));
  }

  private static ExFunction findElement() {
    ExAnonymousFn finder =
        ExAnonymousFn.fn(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("item")),
                ExIf.ifExpr(
                    ExOp.op(
                        "and",
                        ExCallLocal.callLocal("is_element", ExVar.var("item")),
                        ExOp.op(
                            "==",
                            ExCallLocal.callLocal("element_name", ExVar.var("item")),
                            ExVar.var("name"))),
                    ExVar.var("item"),
                    ExNil.nil())));
    return ExFunction.defpFunction(
        "find_element",
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("name"), ExVarPattern.var("content")),
                ExCall.call("Enum", "find_value", ExVar.var("content"), finder))));
  }

  private static ExFunction isElement() {
    ExTuplePattern xmlElement =
        ExTuplePattern.tuple(
            ExAtomPattern.atom("xmlElement"), W, W, W, W, W, W, W, W, W, W, W);
    ExTuplePattern sixTuple = ExTuplePattern.tuple(W, W, ExVarPattern.var("content"), W, W, W);
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

  private static ExFunction elementName() {
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
                                ExConsPattern.consPattern(ExVarPattern.var("text"), W),
                                ExCall.call("List", "to_string", ExVar.var("text")))))))));
  }

  private static ExFunction xmlChildStructList() {
    return ExFunction.defpFunction(
        "xml_child_struct_list",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("parent"),
                    ExNilPattern.nil(),
                    ExVarPattern.var("item_name"),
                    ExVarPattern.var("decode_fun")),
                ExCapturedBlock.capturedBlock(
                    """
                    parent
                    |> element_content()
                    |> Enum.filter(fn item -> is_element(item) and element_name(item) == item_name end)
                    |> Enum.map(decode_fun)""")),
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("parent"),
                    ExVarPattern.var("list_name"),
                    ExVarPattern.var("item_name"),
                    ExVarPattern.var("decode_fun")),
                ExCapturedBlock.capturedBlock(
                    """
                    case find_element(list_name, element_content(parent)) do
                      nil -> nil
                      list_element ->
                        list_element
                        |> element_content()
                        |> Enum.filter(fn item -> is_element(item) and element_name(item) == item_name end)
                        |> Enum.map(decode_fun)
                    end"""))));
  }
}
