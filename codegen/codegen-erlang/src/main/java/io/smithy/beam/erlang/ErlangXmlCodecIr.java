package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlApply;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCapturedBlock;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlCatchClause;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComprehensionFilter;
import io.smithy.beam.ir.erlang.ErlComprehensionGenerator;
import io.smithy.beam.ir.erlang.ErlComprehensionQual;
import io.smithy.beam.ir.erlang.ErlConsPattern;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFun;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlInteger;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlListComprehension;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlNilPattern;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlPattern;
import io.smithy.beam.ir.erlang.ErlTry;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ErlangXmlCodecIr {
    private ErlangXmlCodecIr() {}

    private static final ErlVarPattern W = ErlVarPattern.varPattern("_");

    public static List<ErlFunction> restXmlHelpers() {
        List<ErlFunction> functions = new ArrayList<>();
        functions.addAll(restXmlDecodeHelpers());
        functions.addAll(restXmlEncodeHelpers());
        return functions;
    }

    public static List<ErlFunction> restXmlDecodeHelpers() {
        return List.of(
                parseXmlRoot(),
                xmlElementNamed(),
                elementContent(),
                findElement(),
                isElement(),
                elementName(),
                xmlChildText(),
                elementText(),
                xmlTextValues(),
                isElementString(),
                xmlAttribute(),
                xmlChildList(),
                xmlChildStructList(),
                decodeRestXmlError());
    }

    public static List<ErlFunction> restXmlEncodeHelpers() {
        return List.of(
                encodeXml(),
                buildXmlElement(),
                buildXmlChild(),
                xmlNamespaceAttrs(),
                ErlangCodecHelperIr.encodeQueryValueXmlQuery(),
                ErlangCodecHelperIr.toBinary(ErlangCodecHelperIr.ToBinaryVariant.XML_QUERY));
    }

    static List<ErlFunction> awsQueryServerEncodeHelpers() {
        return List.of(
                encodeXml(),
                buildXmlElement(),
                buildXmlChild(),
                xmlNamespaceAttrs(),
                ErlangCodecHelperIr.toBinary(ErlangCodecHelperIr.ToBinaryVariant.XML_QUERY));
    }

    static ErlFunction elementContentFunction() {
        return elementContent();
    }

    static ErlFunction isElementFunction() {
        return isElement();
    }

    static ErlFunction xmlChildTextFunction() {
        return xmlChildText();
    }

    static ErlFunction isElementStringFunction() {
        return isElementString();
    }

    public static ErlFunction decodeSparseMap() {
        ErlFun sparseMapFun = ErlFun.fun(
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("_K"), ErlAtomPattern.atomPattern("null")),
                        ErlAtom.atom("undefined")),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("_K"), ErlVarPattern.varPattern("V")),
                        ErlVar.var("V")));

        return ErlFunction.function(
                "decode_sparse_map",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("Map")),
                                List.of(ErlGuard.guard("is_map", ErlVar.var("Map"))),
                                ErlCall.call("maps", "map", sparseMapFun, ErlVar.var("Map")))));
    }

    private static ErlFunction parseXmlRoot() {
        ErlCase rootLookup = ErlCase.caseExpr(
                ErlCallLocal.callLocal(
                        "find_element",
                        ErlVar.var("RootName"),
                        ErlCallLocal.callLocal("element_content", ErlVar.var("Xml"))),
                ErlClause.clause(
                        List.of(ErlAtomPattern.atomPattern("undefined")),
                        ErlTuple.tuple(
                                ErlAtom.atom("error"),
                                ErlTuple.tuple(ErlAtom.atom("missing_root"), ErlVar.var("RootName")))),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Root")),
                        ErlTuple.tuple(ErlAtom.atom("ok"), ErlVar.var("Root"))));

        ErlTry body = ErlTry.tryExpr(
                List.of(
                        ErlMatch.match(
                                ErlTuplePattern.tuplePattern(
                                        ErlVarPattern.varPattern("Xml"), ErlVarPattern.varPattern("_")),
                                ErlCall.call(
                                        "xmerl_scan",
                                        "string",
                                        ErlCallLocal.callLocal("binary_to_list", ErlVar.var("Body")))),
                        ErlCase.caseExpr(
                                ErlCallLocal.callLocal(
                                        "xml_element_named", ErlVar.var("Xml"), ErlVar.var("RootName")),
                                ErlClause.clause(
                                        List.of(ErlAtomPattern.atomPattern("true")),
                                        ErlTuple.tuple(ErlAtom.atom("ok"), ErlVar.var("Xml"))),
                                ErlClause.clause(
                                        List.of(ErlAtomPattern.atomPattern("false")),
                                        rootLookup))),
                List.of(ErlCatchClause.catchClause(
                        W,
                        ErlVarPattern.varPattern("Reason"),
                        ErlTuple.tuple(
                                ErlAtom.atom("error"),
                                ErlTuple.tuple(ErlAtom.atom("xml_parse_error"), ErlVar.var("Reason"))))));

        return ErlFunction.function(
                "parse_xml_root",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Body"), ErlVarPattern.varPattern("RootName")),
                        body)));
    }

    private static ErlFunction xmlElementNamed() {
        return ErlFunction.function(
                "xml_element_named",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Element"), ErlVarPattern.varPattern("Name")),
                        ErlOp.op(
                                "andalso",
                                ErlCallLocal.callLocal("is_element", ErlVar.var("Element")),
                                ErlOp.op(
                                        "=:=",
                                        ErlCallLocal.callLocal("element_name", ErlVar.var("Element")),
                                        ErlVar.var("Name"))))));
    }

    private static ErlFunction elementContent() {
        return ErlFunction.function(
                "element_content",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(xmlElementTuple(W, 8, "Content", W)),
                                ErlVar.var("Content")),
                        ErlClause.clause(
                                List.of(sixTupleWithContent()),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("Content"))),
                                ErlVar.var("Content")),
                        ErlClause.clause(
                                List.of(ErlConsPattern.consPattern(ErlVarPattern.varPattern("H"), W)),
                                ErlCallLocal.callLocal("element_content", ErlVar.var("H"))),
                        ErlClause.clause(List.of(W), ErlList.list())));
    }

    private static ErlFunction findElement() {
        ErlListComprehension matches = ErlListComprehension.comprehensionWithFilters(
                ErlVar.var("C"),
                ErlVarPattern.varPattern("C"),
                ErlVar.var("Content"),
                List.of(
                        ErlCallLocal.callLocal("is_element", ErlVar.var("C")),
                        ErlOp.op(
                                "=:=",
                                ErlCallLocal.callLocal("element_name", ErlVar.var("C")),
                                ErlVar.var("Name"))));

        return ErlFunction.function(
                "find_element",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Name"), ErlVarPattern.varPattern("Content")),
                        ErlCase.caseExpr(
                                matches,
                                ErlClause.clause(
                                        List.of(ErlConsPattern.consPattern(ErlVarPattern.varPattern("Element"), W)),
                                        ErlVar.var("Element")),
                                ErlClause.clause(List.of(ErlNilPattern.nilPattern()), ErlAtom.atom("undefined"))))));
    }

    private static ErlFunction isElement() {
        return ErlFunction.function(
                "is_element",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(xmlElementTuple(W, -1, null, W)),
                                ErlAtom.atom("true")),
                        ErlClause.clause(
                                List.of(sixTupleWithContent()),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("Content"))),
                                ErlAtom.atom("true")),
                        ErlClause.clause(List.of(W), ErlAtom.atom("false"))));
    }

    private static ErlFunction elementName() {
        return ErlFunction.function(
                "element_name",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(xmlElementTuple(ErlVarPattern.varPattern("Name"), -1, null, W)),
                                List.of(ErlGuard.guard("is_atom", ErlVar.var("Name"))),
                                ErlCallLocal.callLocal(
                                        "list_to_binary", ErlCallLocal.callLocal("atom_to_list", ErlVar.var("Name")))),
                        ErlClause.clause(
                                List.of(xmlElementTuple(ErlVarPattern.varPattern("Name"), -1, null, W)),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("Name"))),
                                ErlCallLocal.callLocal("list_to_binary", ErlVar.var("Name"))),
                        ErlClause.clause(
                                List.of(xmlElementTuple(ErlVarPattern.varPattern("Name"), -1, null, W)),
                                List.of(ErlGuard.guard("is_binary", ErlVar.var("Name"))),
                                ErlVar.var("Name")),
                        ErlClause.clause(
                                List.of(sixTupleName()),
                                List.of(ErlGuard.guard("is_atom", ErlVar.var("Name"))),
                                ErlCallLocal.callLocal(
                                        "list_to_binary", ErlCallLocal.callLocal("atom_to_list", ErlVar.var("Name")))),
                        ErlClause.clause(
                                List.of(sixTupleName()),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("Name"))),
                                ErlCallLocal.callLocal("list_to_binary", ErlVar.var("Name"))),
                        ErlClause.clause(
                                List.of(sixTupleName()),
                                List.of(ErlGuard.guard("is_binary", ErlVar.var("Name"))),
                                ErlVar.var("Name"))));
    }

    private static ErlFunction xmlChildText() {
        return ErlFunction.function(
                "xml_child_text",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Parent"), ErlVarPattern.varPattern("Name")),
                        ErlCase.caseExpr(
                                ErlCallLocal.callLocal(
                                        "find_element",
                                        ErlVar.var("Name"),
                                        ErlCallLocal.callLocal("element_content", ErlVar.var("Parent"))),
                                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
                                ErlClause.clause(
                                        List.of(ErlVarPattern.varPattern("Element")),
                                        ErlCase.caseExpr(
                                                ErlCallLocal.callLocal("element_text", ErlVar.var("Element")),
                                                ErlClause.clause(List.of(ErlNilPattern.nilPattern()), ErlAtom.atom("undefined")),
                                                ErlClause.clause(
                                                        List.of(ErlVarPattern.varPattern("Text")),
                                                        ErlCallLocal.callLocal("list_to_binary", ErlVar.var("Text")))))))));
    }

    private static ErlFunction elementText() {
        return ErlFunction.function(
                "element_text",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(xmlElementTuple(W, 8, "Content", W)),
                                ErlCallLocal.callLocal("xml_text_values", ErlVar.var("Content"))),
                        ErlClause.clause(
                                List.of(sixTupleWithContent()),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("Content"))),
                                ErlListComprehension.comprehensionWithFilters(
                                        ErlVar.var("T"),
                                        ErlVarPattern.varPattern("T"),
                                        ErlVar.var("Content"),
                                        List.of(
                                                ErlCapturedBlock.capturedBlock("is_list(T)"),
                                                ErlCapturedBlock.capturedBlock("not is_element_string(T)")))),
                        ErlClause.clause(List.of(W), ErlList.list())));
    }

    private static ErlFunction xmlTextValues() {
        ErlCase textCase = ErlCase.caseExpr(
                ErlVar.var("C"),
                ErlClause.clause(
                        List.of(xmlTextPattern()),
                        List.of(ErlGuard.guard("is_list", ErlVar.var("V"))),
                        ErlVar.var("V")),
                ErlClause.clause(
                        List.of(xmlTextPattern()),
                        List.of(ErlGuard.guard("is_binary", ErlVar.var("V"))),
                        ErlCallLocal.callLocal("binary_to_list", ErlVar.var("V"))),
                ErlClause.clause(List.of(W), ErlList.list()));

        return ErlFunction.function(
                "xml_text_values",
                1,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Content")),
                        ErlCall.call(
                                "lists",
                                "flatten",
                                ErlListComprehension.comprehension(
                                        textCase,
                                        ErlVarPattern.varPattern("C"),
                                        ErlVar.var("Content"))))));
    }

    private static ErlFunction isElementString() {
        return ErlFunction.function(
                "is_element_string",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("T")),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("T"))),
                                ErlCase.caseExpr(
                                        ErlVar.var("T"),
                                        ErlClause.clause(
                                                List.of(xmlElementTuple(W, -1, null, W)),
                                                ErlAtom.atom("true")),
                                        ErlClause.clause(
                                                List.of(ErlTuplePattern.tuplePattern(W, W, W, W, W, W)),
                                                ErlAtom.atom("true")),
                                        ErlClause.clause(List.of(W), ErlAtom.atom("false")))),
                        ErlClause.clause(List.of(W), ErlAtom.atom("false"))));
    }

    private static ErlFunction xmlAttribute() {
        return ErlFunction.function(
                "xml_attribute",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Element"), ErlVarPattern.varPattern("AttrName")),
                        ErlCase.caseExpr(
                                ErlCall.call(
                                        "proplists",
                                        "get_value",
                                        ErlVar.var("AttrName"),
                                        ErlCallLocal.callLocal("element", ErlVar.var("Element"), ErlInteger.integer(2)),
                                        ErlAtom.atom("undefined")),
                                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
                                ErlClause.clause(
                                        List.of(ErlVarPattern.varPattern("Value")),
                                        ErlCallLocal.callLocal("list_to_binary", ErlVar.var("Value")))))));
    }

    private static ErlFunction xmlChildList() {
        List<ErlComprehensionQual> itemQualifiers = List.of(
                new ErlComprehensionGenerator(
                        ErlVarPattern.varPattern("Item"),
                        ErlCallLocal.callLocal("element_content", ErlVar.var("Parent"))),
                new ErlComprehensionFilter(ErlCallLocal.callLocal("is_element", ErlVar.var("Item"))),
                new ErlComprehensionFilter(
                        ErlOp.op(
                                "=:=",
                                ErlCallLocal.callLocal("element_name", ErlVar.var("Item")),
                                ErlVar.var("ItemName"))),
                new ErlComprehensionGenerator(
                        ErlVarPattern.varPattern("ItemText"),
                        ErlList.list(
                                ErlCallLocal.callLocal(
                                        "list_to_binary",
                                        ErlCallLocal.callLocal("element_text", ErlVar.var("Item"))))),
                new ErlComprehensionFilter(
                        ErlOp.op("=/=", ErlVar.var("ItemText"), ErlBinary.binary(""))));

        ErlListComprehension directList =
                ErlListComprehension.comprehensionQualifiers(ErlVar.var("ItemText"), itemQualifiers);

        ErlListComprehension nestedList = ErlListComprehension.comprehensionQualifiers(
                ErlVar.var("ItemText"), List.of(
                        new ErlComprehensionGenerator(
                                ErlVarPattern.varPattern("Item"),
                                ErlCallLocal.callLocal("element_content", ErlVar.var("ListElement"))),
                        new ErlComprehensionFilter(ErlCallLocal.callLocal("is_element", ErlVar.var("Item"))),
                        new ErlComprehensionFilter(
                                ErlOp.op(
                                        "=:=",
                                        ErlCallLocal.callLocal("element_name", ErlVar.var("Item")),
                                        ErlVar.var("ItemName"))),
                        new ErlComprehensionGenerator(
                                ErlVarPattern.varPattern("ItemText"),
                                ErlList.list(
                                        ErlCallLocal.callLocal(
                                                "list_to_binary",
                                                ErlCallLocal.callLocal("element_text", ErlVar.var("Item"))))),
                        new ErlComprehensionFilter(
                                ErlOp.op("=/=", ErlVar.var("ItemText"), ErlBinary.binary("")))));

        return ErlFunction.function(
                "xml_child_list",
                3,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Parent"),
                                        ErlAtomPattern.atomPattern("undefined"),
                                        ErlVarPattern.varPattern("ItemName")),
                                directList),
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Parent"),
                                        ErlVarPattern.varPattern("ListName"),
                                        ErlVarPattern.varPattern("ItemName")),
                                ErlCase.caseExpr(
                                        ErlCallLocal.callLocal(
                                                "find_element",
                                                ErlVar.var("ListName"),
                                                ErlCallLocal.callLocal("element_content", ErlVar.var("Parent"))),
                                        ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
                                        ErlClause.clause(
                                                List.of(ErlVarPattern.varPattern("ListElement")),
                                                nestedList)))));
    }

    private static ErlFunction xmlChildStructList() {
        ErlApply decodeItem = ErlApply.apply(ErlVar.var("DecodeFun"), ErlVar.var("Item"));

        return ErlFunction.function(
                "xml_child_struct_list",
                4,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Parent"),
                                        ErlAtomPattern.atomPattern("undefined"),
                                        ErlVarPattern.varPattern("ItemName"),
                                        ErlVarPattern.varPattern("DecodeFun")),
                                ErlListComprehension.comprehensionWithFilters(
                                        decodeItem,
                                        ErlVarPattern.varPattern("Item"),
                                        ErlCallLocal.callLocal("element_content", ErlVar.var("Parent")),
                                        List.of(
                                                ErlCallLocal.callLocal("is_element", ErlVar.var("Item")),
                                                ErlOp.op(
                                                        "=:=",
                                                        ErlCallLocal.callLocal("element_name", ErlVar.var("Item")),
                                                        ErlVar.var("ItemName"))))),
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Parent"),
                                        ErlVarPattern.varPattern("ListName"),
                                        ErlVarPattern.varPattern("ItemName"),
                                        ErlVarPattern.varPattern("DecodeFun")),
                                ErlCase.caseExpr(
                                        ErlCallLocal.callLocal(
                                                "find_element",
                                                ErlVar.var("ListName"),
                                                ErlCallLocal.callLocal("element_content", ErlVar.var("Parent"))),
                                        ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
                                        ErlClause.clause(
                                                List.of(ErlVarPattern.varPattern("ListElement")),
                                                ErlListComprehension.comprehensionWithFilters(
                                                        decodeItem,
                                                        ErlVarPattern.varPattern("Item"),
                                                        ErlCallLocal.callLocal("element_content", ErlVar.var("ListElement")),
                                                        List.of(
                                                                ErlCallLocal.callLocal("is_element", ErlVar.var("Item")),
                                                                ErlOp.op(
                                                                        "=:=",
                                                                        ErlCallLocal.callLocal("element_name", ErlVar.var("Item")),
                                                                        ErlVar.var("ItemName")))))))));
    }

    private static ErlFunction decodeRestXmlError() {
        ErlCase errorLookup = ErlCase.caseExpr(
                ErlCallLocal.callLocal(
                        "find_element",
                        ErlBinary.binary("Error"),
                        ErlCallLocal.callLocal("element_content", ErlVar.var("ErrorResponse"))),
                ErlClause.clause(
                        List.of(ErlAtomPattern.atomPattern("undefined")),
                        ErlTuple.tuple(
                                ErlAtom.atom("error"),
                                ErlTuple.tuple(ErlAtom.atom("unknown_error"), ErlVar.var("Status"), ErlVar.var("Body")))),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Error")),
                        ErlTuple.tuple(
                                ErlAtom.atom("error"),
                                ErlTuple.tuple(
                                        ErlCallLocal.callLocal(
                                                "xml_child_text", ErlVar.var("Error"), ErlBinary.binary("Code")),
                                        ErlCallLocal.callLocal(
                                                "xml_child_text", ErlVar.var("Error"), ErlBinary.binary("Message"))))));

        return ErlFunction.function(
                "decode_rest_xml_error",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Status"), ErlVarPattern.varPattern("Body")),
                        ErlCase.caseExpr(
                                ErlCallLocal.callLocal(
                                        "parse_xml_root", ErlVar.var("Body"), ErlBinary.binary("ErrorResponse")),
                                ErlClause.clause(
                                        List.of(ErlTuplePattern.tuplePattern(
                                                ErlAtomPattern.atomPattern("ok"),
                                                ErlVarPattern.varPattern("ErrorResponse"))),
                                        errorLookup),
                                ErlClause.clause(
                                        List.of(ErlTuplePattern.tuplePattern(ErlAtomPattern.atomPattern("error"), W)),
                                        ErlTuple.tuple(
                                                ErlAtom.atom("error"),
                                                ErlTuple.tuple(
                                                        ErlAtom.atom("unknown_error"),
                                                        ErlVar.var("Status"),
                                                        ErlVar.var("Body"))))))));
    }

    private static ErlFunction encodeXml() {
        return ErlFunction.function(
                "encode_xml",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("RootMap"), ErlVarPattern.varPattern("XmlNs")),
                        ErlExprBlock.block(
                                ErlMatch.match(
                                        ErlConsPattern.consPattern(
                                                ErlTuplePattern.tuplePattern(
                                                        ErlVarPattern.varPattern("RootName"),
                                                        ErlVarPattern.varPattern("Content")),
                                                ErlNilPattern.nilPattern()),
                                        ErlCall.call("maps", "to_list", ErlVar.var("RootMap"))),
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("Element"),
                                        ErlCallLocal.callLocal(
                                                "build_xml_element",
                                                ErlVar.var("RootName"),
                                                ErlVar.var("Content"),
                                                ErlVar.var("XmlNs"))),
                                ErlCallLocal.callLocal(
                                        "iolist_to_binary",
                                        ErlCall.call(
                                                "xmerl",
                                                "export_simple",
                                                ErlList.list(ErlVar.var("Element")),
                                                ErlAtom.atom("xmerl_xmlns"),
                                                ErlList.list(),
                                                ErlList.list(
                                                        ErlTuple.tuple(
                                                                ErlAtom.atom("prolog"), ErlAtom.atom("false")))))))));
    }

    private static ErlFunction buildXmlElement() {
        return ErlFunction.function(
                "build_xml_element",
                3,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Name"),
                                        ErlVarPattern.varPattern("Content"),
                                        ErlVarPattern.varPattern("XmlNs")),
                                List.of(ErlGuard.guard("is_map", ErlVar.var("Content"))),
                                ErlExprBlock.block(
                                        ErlMatch.match(
                                                ErlVarPattern.varPattern("Attrs"),
                                                ErlCallLocal.callLocal("xml_namespace_attrs", ErlVar.var("XmlNs"))),
                                        ErlMatch.match(
                                                ErlVarPattern.varPattern("Children"),
                                                ErlListComprehension.comprehension(
                                                        ErlCallLocal.callLocal(
                                                                "build_xml_child", ErlVar.var("K"), ErlVar.var("V")),
                                                        ErlTuplePattern.tuplePattern(
                                                                ErlVarPattern.varPattern("K"),
                                                                ErlVarPattern.varPattern("V")),
                                                        ErlCall.call("maps", "to_list", ErlVar.var("Content")),
                                                        ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined")))),
                                        ErlTuple.tuple(ErlVar.var("Name"), ErlVar.var("Attrs"), ErlVar.var("Children")))),
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Name"),
                                        ErlVarPattern.varPattern("Content"),
                                        ErlVarPattern.varPattern("XmlNs")),
                                ErlTuple.tuple(
                                        ErlVar.var("Name"),
                                        ErlCallLocal.callLocal("xml_namespace_attrs", ErlVar.var("XmlNs")),
                                        ErlList.list(
                                                ErlTuple.tuple(
                                                        ErlAtom.atom("text"),
                                                        ErlCallLocal.callLocal("to_binary", ErlVar.var("Content"))))))));
    }

    private static ErlFunction buildXmlChild() {
        return ErlFunction.function(
                "build_xml_child",
                2,
                List.of(
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("Name"), ErlVarPattern.varPattern("Value")),
                                List.of(ErlGuard.guard("is_map", ErlVar.var("Value"))),
                                ErlTuple.tuple(
                                        ErlVar.var("Name"),
                                        ErlList.list(),
                                        ErlListComprehension.comprehension(
                                                ErlCallLocal.callLocal(
                                                        "build_xml_element",
                                                        ErlVar.var("K"),
                                                        ErlVar.var("V"),
                                                        ErlMap.map()),
                                                ErlTuplePattern.tuplePattern(
                                                        ErlVarPattern.varPattern("K"),
                                                        ErlVarPattern.varPattern("V")),
                                                ErlCall.call("maps", "to_list", ErlVar.var("Value")),
                                                ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined"))))),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("Name"), ErlVarPattern.varPattern("Values")),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("Values"))),
                                ErlTuple.tuple(
                                        ErlVar.var("Name"),
                                        ErlList.list(),
                                        ErlListComprehension.comprehension(
                                                ErlCallLocal.callLocal(
                                                        "build_xml_element",
                                                        ErlBinary.binary("member"),
                                                        ErlVar.var("V"),
                                                        ErlMap.map()),
                                                ErlVarPattern.varPattern("V"),
                                                ErlVar.var("Values"),
                                                ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined"))))),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("Name"), ErlVarPattern.varPattern("Value")),
                                ErlTuple.tuple(
                                        ErlVar.var("Name"),
                                        ErlList.list(),
                                        ErlList.list(
                                                ErlTuple.tuple(
                                                        ErlAtom.atom("text"),
                                                        ErlCallLocal.callLocal("to_binary", ErlVar.var("Value"))))))));
    }

    static ErlFunction xmlNamespace(Optional<String> namespaceUri) {
        if (namespaceUri.isPresent()) {
            return ErlFunction.function(
                    "xml_namespace",
                    0,
                    List.of(ErlClause.clause(
                            List.of(),
                            ErlMap.map(ErlMapEntry.entry(ErlAtom.atom("uri"), ErlBinary.binary(namespaceUri.get()))))));
        }
        return ErlFunction.function(
                "xml_namespace",
                0,
                List.of(ErlClause.clause(List.of(), ErlMap.map())));
    }

    private static ErlFunction xmlNamespaceAttrs() {
        return ErlFunction.function(
                "xml_namespace_attrs",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("#{uri := Uri}")),
                                ErlList.list(ErlTuple.tuple(ErlAtom.atom("xmlns"), ErlVar.var("Uri")))),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("#{uri := Uri, prefix := Prefix}")),
                                ErlList.list(
                                        ErlTuple.tuple(
                                                ErlOp.op(
                                                        "++",
                                                        ErlAtom.atom("xmlns:"),
                                                        ErlCallLocal.callLocal("binary_to_list", ErlVar.var("Prefix"))),
                                                ErlVar.var("Uri")))),
                        ErlClause.clause(List.of(W), ErlList.list())));
    }

    private static ErlTuplePattern xmlElementTuple(
            ErlPattern namePattern, int contentIndex, String contentName, ErlPattern trailing) {
        ErlPattern[] elements = new ErlPattern[12];
        elements[0] = ErlAtomPattern.atomPattern("xmlElement");
        for (int i = 1; i < 12; i++) {
            if (i == 1 && namePattern != W) {
                elements[i] = namePattern;
            } else if (i == contentIndex && contentName != null) {
                elements[i] = ErlVarPattern.varPattern(contentName);
            } else if (i == 11) {
                elements[i] = trailing;
            } else {
                elements[i] = W;
            }
        }
        return ErlTuplePattern.tuplePattern(elements);
    }

    private static ErlTuplePattern sixTupleWithContent() {
        return ErlTuplePattern.tuplePattern(W, W, ErlVarPattern.varPattern("Content"), W, W, W);
    }

    private static ErlTuplePattern sixTupleName() {
        return ErlTuplePattern.tuplePattern(ErlVarPattern.varPattern("Name"), W, W, W, W, W);
    }

    private static ErlTuplePattern xmlTextPattern() {
        return ErlTuplePattern.tuplePattern(
                ErlAtomPattern.atomPattern("xmlText"), W, W, W, ErlVarPattern.varPattern("V"), W);
    }
}
