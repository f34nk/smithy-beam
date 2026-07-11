package io.smithy.beam.erlang;

import io.beam.ir.erlang.ApplyExpr;
import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.CatchPattern;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.Fun;
import io.beam.ir.erlang.FunClause;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.InfixExpr;
import io.beam.ir.erlang.IntegerExpr;
import io.beam.ir.erlang.IsTypeGuard;
import io.beam.ir.erlang.ListComprehensionExpr;
import io.beam.ir.erlang.ListComprehensionFilter;
import io.beam.ir.erlang.ListComprehensionGenerator;
import io.beam.ir.erlang.ListExpr;
import io.beam.ir.erlang.ListPattern;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.MapEntry;
import io.beam.ir.erlang.MapExpr;
import io.beam.ir.erlang.MapPattern;
import io.beam.ir.erlang.MapPatternEntry;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.NotExpr;
import io.beam.ir.erlang.Pattern;
import io.beam.ir.erlang.QuotedAtomExpr;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.TryExpr;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.beam.ir.erlang.WildcardPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ErlangXmlCodecIr {
  private ErlangXmlCodecIr() {}

  private static final WildcardPattern W = WildcardPattern.of();

  public static List<Function> restXmlHelpers() {
    List<Function> functions = new ArrayList<>();
    functions.addAll(restXmlDecodeHelpers());
    functions.addAll(restXmlEncodeHelpers());
    return functions;
  }

  public static List<Function> restXmlDecodeHelpers() {
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

  public static List<Function> restXmlEncodeHelpers() {
    return List.of(
        encodeXml(),
        buildXmlElement(),
        buildXmlChild(),
        xmlNamespaceAttrs(),
        ErlangCodecHelperIr.encodeQueryValueXmlQuery(),
        ErlangCodecHelperIr.toBinary(ErlangCodecHelperIr.ToBinaryVariant.XML_QUERY));
  }

  static List<Function> awsQueryServerEncodeHelpers() {
    return List.of(
        encodeXml(),
        buildXmlElement(),
        buildXmlChild(),
        xmlNamespaceAttrs(),
        ErlangCodecHelperIr.toBinary(ErlangCodecHelperIr.ToBinaryVariant.XML_QUERY));
  }

  static Function elementContentFunction() {
    return elementContent();
  }

  static Function isElementFunction() {
    return isElement();
  }

  static Function xmlChildTextFunction() {
    return xmlChildText();
  }

  static Function isElementStringFunction() {
    return isElementString();
  }

  public static Function decodeSparseMap() {
    return Function.of(
        "decode_sparse_map",
        List.of(
            FunctionClause.of(List.of(AtomPattern.of("undefined")), AtomExpr.of("undefined")),
            FunctionClause.of(
                List.of(VariablePattern.of("Map")),
                IsTypeGuard.of("map", Variable.of("Map")),
                RemoteCallExpr.of(
                    "maps",
                    "map",
                    List.of(
                        Fun.of(
                            List.of(
                                FunClause.of(
                                    List.of(WildcardPattern.of("K"), AtomPattern.of("null")),
                                    AtomExpr.of("undefined")),
                                FunClause.of(
                                    List.of(WildcardPattern.of("K"), VariablePattern.of("V")),
                                    Variable.of("V")))),
                        Variable.of("Map"))))));
  }

  private static Function parseXmlRoot() {
    Expression rootLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "find_element",
                List.of(
                    Variable.of("RootName"),
                    LocalCallExpr.of("element_content", List.of(Variable.of("Xml"))))),
            List.of(
                Clause.of(
                    AtomPattern.of("undefined"),
                    TupleExpr.of(
                        List.of(
                            AtomExpr.of("error"),
                            TupleExpr.of(
                                List.of(AtomExpr.of("missing_root"), Variable.of("RootName")))))),
                Clause.of(
                    VariablePattern.of("Root"),
                    TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Root"))))));

    Expression body =
        TryExpr.of(
            MatchExpr.of(
                TuplePattern.of(List.of(VariablePattern.of("Xml"), WildcardPattern.of())),
                RemoteCallExpr.of(
                    "xmerl_scan",
                    "string",
                    List.of(LocalCallExpr.of("binary_to_list", List.of(Variable.of("Body"))))),
                CaseExpr.of(
                    LocalCallExpr.of(
                        "xml_element_named", List.of(Variable.of("Xml"), Variable.of("RootName"))),
                    List.of(
                        Clause.of(
                            AtomPattern.of("true"),
                            TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Xml")))),
                        Clause.of(AtomPattern.of("false"), rootLookup)))),
            List.of(
                Clause.of(
                    CatchPattern.anyReason("Reason"),
                    TupleExpr.of(
                        List.of(
                            AtomExpr.of("error"),
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("xml_parse_error"), Variable.of("Reason"))))))));

    return Function.of(
        "parse_xml_root",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Body"), VariablePattern.of("RootName")), body)));
  }

  private static Function xmlElementNamed() {
    return Function.of(
        "xml_element_named",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Element"), VariablePattern.of("Name")),
                InfixExpr.of(
                    LocalCallExpr.of("is_element", List.of(Variable.of("Element"))),
                    "andalso",
                    InfixExpr.of(
                        LocalCallExpr.of("element_name", List.of(Variable.of("Element"))),
                        "=:=",
                        Variable.of("Name"))))));
  }

  private static Function elementContent() {
    return Function.of(
        "element_content",
        List.of(
            FunctionClause.of(List.of(xmlElementTuple(W, 8, "Content", W)), Variable.of("Content")),
            FunctionClause.of(
                List.of(sixTupleWithContent()),
                IsTypeGuard.of("list", Variable.of("Content")),
                Variable.of("Content")),
            FunctionClause.of(
                List.of(ListPattern.cons(VariablePattern.of("H"), W)),
                LocalCallExpr.of("element_content", List.of(Variable.of("H")))),
            FunctionClause.of(List.of(W), ListExpr.of(List.of()))));
  }

  private static Function findElement() {
    Expression matches =
        ListComprehensionExpr.of(
            Variable.of("C"),
            List.of(
                ListComprehensionGenerator.of(VariablePattern.of("C"), Variable.of("Content")),
                ListComprehensionFilter.of(
                    LocalCallExpr.of("is_element", List.of(Variable.of("C")))),
                ListComprehensionFilter.of(
                    InfixExpr.of(
                        LocalCallExpr.of("element_name", List.of(Variable.of("C"))),
                        "=:=",
                        Variable.of("Name")))));

    return Function.of(
        "find_element",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Name"), VariablePattern.of("Content")),
                CaseExpr.of(
                    matches,
                    List.of(
                        Clause.of(
                            ListPattern.cons(VariablePattern.of("Element"), W),
                            Variable.of("Element")),
                        Clause.of(ListPattern.of(List.of()), AtomExpr.of("undefined")))))));
  }

  private static Function isElement() {
    return Function.of(
        "is_element",
        List.of(
            FunctionClause.of(List.of(xmlElementTuple(W, -1, null, W)), AtomExpr.of("true")),
            FunctionClause.of(
                List.of(sixTupleWithContent()),
                IsTypeGuard.of("list", Variable.of("Content")),
                AtomExpr.of("true")),
            FunctionClause.of(List.of(W), AtomExpr.of("false"))));
  }

  private static Function elementName() {
    return Function.of(
        "element_name",
        List.of(
            FunctionClause.of(
                List.of(xmlElementTuple(VariablePattern.of("Name"), -1, null, W)),
                IsTypeGuard.of("atom", Variable.of("Name")),
                LocalCallExpr.of(
                    "list_to_binary",
                    List.of(LocalCallExpr.of("atom_to_list", List.of(Variable.of("Name")))))),
            FunctionClause.of(
                List.of(xmlElementTuple(VariablePattern.of("Name"), -1, null, W)),
                IsTypeGuard.of("list", Variable.of("Name")),
                LocalCallExpr.of("list_to_binary", List.of(Variable.of("Name")))),
            FunctionClause.of(
                List.of(xmlElementTuple(VariablePattern.of("Name"), -1, null, W)),
                IsTypeGuard.of("binary", Variable.of("Name")),
                Variable.of("Name")),
            FunctionClause.of(
                List.of(sixTupleName()),
                IsTypeGuard.of("atom", Variable.of("Name")),
                LocalCallExpr.of(
                    "list_to_binary",
                    List.of(LocalCallExpr.of("atom_to_list", List.of(Variable.of("Name")))))),
            FunctionClause.of(
                List.of(sixTupleName()),
                IsTypeGuard.of("list", Variable.of("Name")),
                LocalCallExpr.of("list_to_binary", List.of(Variable.of("Name")))),
            FunctionClause.of(
                List.of(sixTupleName()),
                IsTypeGuard.of("binary", Variable.of("Name")),
                Variable.of("Name"))));
  }

  private static Function xmlChildText() {
    Expression elementTextCase =
        CaseExpr.of(
            LocalCallExpr.of("element_text", List.of(Variable.of("Element"))),
            List.of(
                Clause.of(ListPattern.of(List.of()), AtomExpr.of("undefined")),
                Clause.of(
                    VariablePattern.of("Text"),
                    LocalCallExpr.of("list_to_binary", List.of(Variable.of("Text"))))));

    Expression findElementCase =
        CaseExpr.of(
            LocalCallExpr.of(
                "find_element",
                List.of(
                    Variable.of("Name"),
                    LocalCallExpr.of("element_content", List.of(Variable.of("Parent"))))),
            List.of(
                Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                Clause.of(VariablePattern.of("Element"), elementTextCase)));

    return Function.of(
        "xml_child_text",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Parent"), VariablePattern.of("Name")),
                findElementCase)));
  }

  private static Function elementText() {
    return Function.of(
        "element_text",
        List.of(
            FunctionClause.of(
                List.of(xmlElementTuple(W, 8, "Content", W)),
                LocalCallExpr.of("xml_text_values", List.of(Variable.of("Content")))),
            FunctionClause.of(
                List.of(sixTupleWithContent()),
                IsTypeGuard.of("list", Variable.of("Content")),
                ListComprehensionExpr.of(
                    Variable.of("T"),
                    List.of(
                        ListComprehensionGenerator.of(
                            VariablePattern.of("T"), Variable.of("Content")),
                        ListComprehensionFilter.of(
                            LocalCallExpr.of("is_list", List.of(Variable.of("T")))),
                        ListComprehensionFilter.of(
                            NotExpr.of(
                                LocalCallExpr.of(
                                    "is_element_string", List.of(Variable.of("T")))))))),
            FunctionClause.of(List.of(W), ListExpr.of(List.of()))));
  }

  private static Function xmlTextValues() {
    Expression textCase =
        CaseExpr.of(
            Variable.of("C"),
            List.of(
                Clause.of(
                    xmlTextPattern(), IsTypeGuard.of("list", Variable.of("V")), Variable.of("V")),
                Clause.of(
                    xmlTextPattern(),
                    IsTypeGuard.of("binary", Variable.of("V")),
                    LocalCallExpr.of("binary_to_list", List.of(Variable.of("V")))),
                Clause.of(W, ListExpr.of(List.of()))));

    return Function.of(
        "xml_text_values",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Content")),
                RemoteCallExpr.of(
                    "lists",
                    "flatten",
                    List.of(
                        ListComprehensionExpr.of(
                            textCase, VariablePattern.of("C"), Variable.of("Content")))))));
  }

  private static Function isElementString() {
    return Function.of(
        "is_element_string",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("T")),
                IsTypeGuard.of("list", Variable.of("T")),
                CaseExpr.of(
                    Variable.of("T"),
                    List.of(
                        Clause.of(xmlElementTuple(W, -1, null, W), AtomExpr.of("true")),
                        Clause.of(TuplePattern.of(List.of(W, W, W, W, W, W)), AtomExpr.of("true")),
                        Clause.of(W, AtomExpr.of("false"))))),
            FunctionClause.of(List.of(W), AtomExpr.of("false"))));
  }

  private static Function xmlAttribute() {
    return Function.of(
        "xml_attribute",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Element"), VariablePattern.of("AttrName")),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "proplists",
                        "get_value",
                        List.of(
                            Variable.of("AttrName"),
                            LocalCallExpr.of(
                                "element", List.of(Variable.of("Element"), IntegerExpr.of(2))),
                            AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                        Clause.of(
                            VariablePattern.of("Value"),
                            LocalCallExpr.of("list_to_binary", List.of(Variable.of("Value")))))))));
  }

  private static Function xmlChildList() {
    List<io.beam.ir.erlang.ListComprehensionQualifier> itemQualifiers =
        List.of(
            ListComprehensionGenerator.of(
                VariablePattern.of("Item"),
                LocalCallExpr.of("element_content", List.of(Variable.of("Parent")))),
            ListComprehensionFilter.of(
                LocalCallExpr.of("is_element", List.of(Variable.of("Item")))),
            ListComprehensionFilter.of(
                InfixExpr.of(
                    LocalCallExpr.of("element_name", List.of(Variable.of("Item"))),
                    "=:=",
                    Variable.of("ItemName"))),
            ListComprehensionGenerator.of(
                VariablePattern.of("ItemText"),
                ListExpr.of(
                    List.of(
                        LocalCallExpr.of(
                            "list_to_binary",
                            List.of(
                                LocalCallExpr.of("element_text", List.of(Variable.of("Item")))))))),
            ListComprehensionFilter.of(
                InfixExpr.of(Variable.of("ItemText"), "=/=", BinaryExpr.of(""))));

    Expression directList = ListComprehensionExpr.of(Variable.of("ItemText"), itemQualifiers);

    Expression nestedList =
        ListComprehensionExpr.of(
            Variable.of("ItemText"),
            List.of(
                ListComprehensionGenerator.of(
                    VariablePattern.of("Item"),
                    LocalCallExpr.of("element_content", List.of(Variable.of("ListElement")))),
                ListComprehensionFilter.of(
                    LocalCallExpr.of("is_element", List.of(Variable.of("Item")))),
                ListComprehensionFilter.of(
                    InfixExpr.of(
                        LocalCallExpr.of("element_name", List.of(Variable.of("Item"))),
                        "=:=",
                        Variable.of("ItemName"))),
                ListComprehensionGenerator.of(
                    VariablePattern.of("ItemText"),
                    ListExpr.of(
                        List.of(
                            LocalCallExpr.of(
                                "list_to_binary",
                                List.of(
                                    LocalCallExpr.of(
                                        "element_text", List.of(Variable.of("Item")))))))),
                ListComprehensionFilter.of(
                    InfixExpr.of(Variable.of("ItemText"), "=/=", BinaryExpr.of("")))));

    return Function.of(
        "xml_child_list",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Parent"),
                    AtomPattern.of("undefined"),
                    VariablePattern.of("ItemName")),
                directList),
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Parent"),
                    VariablePattern.of("ListName"),
                    VariablePattern.of("ItemName")),
                CaseExpr.of(
                    LocalCallExpr.of(
                        "find_element",
                        List.of(
                            Variable.of("ListName"),
                            LocalCallExpr.of("element_content", List.of(Variable.of("Parent"))))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                        Clause.of(VariablePattern.of("ListElement"), nestedList))))));
  }

  private static Function xmlChildStructList() {
    Expression decodeItem = ApplyExpr.of(Variable.of("DecodeFun"), List.of(Variable.of("Item")));

    return Function.of(
        "xml_child_struct_list",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Parent"),
                    AtomPattern.of("undefined"),
                    VariablePattern.of("ItemName"),
                    VariablePattern.of("DecodeFun")),
                ListComprehensionExpr.of(
                    decodeItem,
                    List.of(
                        ListComprehensionGenerator.of(
                            VariablePattern.of("Item"),
                            LocalCallExpr.of("element_content", List.of(Variable.of("Parent")))),
                        ListComprehensionFilter.of(
                            LocalCallExpr.of("is_element", List.of(Variable.of("Item")))),
                        ListComprehensionFilter.of(
                            InfixExpr.of(
                                LocalCallExpr.of("element_name", List.of(Variable.of("Item"))),
                                "=:=",
                                Variable.of("ItemName")))))),
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Parent"),
                    VariablePattern.of("ListName"),
                    VariablePattern.of("ItemName"),
                    VariablePattern.of("DecodeFun")),
                CaseExpr.of(
                    LocalCallExpr.of(
                        "find_element",
                        List.of(
                            Variable.of("ListName"),
                            LocalCallExpr.of("element_content", List.of(Variable.of("Parent"))))),
                    List.of(
                        Clause.of(AtomPattern.of("undefined"), AtomExpr.of("undefined")),
                        Clause.of(
                            VariablePattern.of("ListElement"),
                            ListComprehensionExpr.of(
                                decodeItem,
                                List.of(
                                    ListComprehensionGenerator.of(
                                        VariablePattern.of("Item"),
                                        LocalCallExpr.of(
                                            "element_content",
                                            List.of(Variable.of("ListElement")))),
                                    ListComprehensionFilter.of(
                                        LocalCallExpr.of(
                                            "is_element", List.of(Variable.of("Item")))),
                                    ListComprehensionFilter.of(
                                        InfixExpr.of(
                                            LocalCallExpr.of(
                                                "element_name", List.of(Variable.of("Item"))),
                                            "=:=",
                                            Variable.of("ItemName")))))))))));
  }

  private static Function decodeRestXmlError() {
    Expression errorLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "find_element",
                List.of(
                    BinaryExpr.of("Error"),
                    LocalCallExpr.of("element_content", List.of(Variable.of("ErrorResponse"))))),
            List.of(
                Clause.of(
                    AtomPattern.of("undefined"),
                    TupleExpr.of(
                        List.of(
                            AtomExpr.of("error"),
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("unknown_error"),
                                    Variable.of("Status"),
                                    Variable.of("Body")))))),
                Clause.of(
                    VariablePattern.of("Error"),
                    TupleExpr.of(
                        List.of(
                            AtomExpr.of("error"),
                            TupleExpr.of(
                                List.of(
                                    LocalCallExpr.of(
                                        "xml_child_text",
                                        List.of(Variable.of("Error"), BinaryExpr.of("Code"))),
                                    LocalCallExpr.of(
                                        "xml_child_text",
                                        List.of(
                                            Variable.of("Error"), BinaryExpr.of("Message"))))))))));

    return Function.of(
        "decode_rest_xml_error",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Status"), VariablePattern.of("Body")),
                CaseExpr.of(
                    LocalCallExpr.of(
                        "parse_xml_root",
                        List.of(Variable.of("Body"), BinaryExpr.of("ErrorResponse"))),
                    List.of(
                        Clause.of(
                            TuplePattern.of(
                                List.of(AtomPattern.of("ok"), VariablePattern.of("ErrorResponse"))),
                            errorLookup),
                        Clause.of(
                            TuplePattern.of(List.of(AtomPattern.of("error"), W)),
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("error"),
                                    TupleExpr.of(
                                        List.of(
                                            AtomExpr.of("unknown_error"),
                                            Variable.of("Status"),
                                            Variable.of("Body")))))))))));
  }

  private static Function encodeXml() {
    Expression exportSimple =
        RemoteCallExpr.of(
            "xmerl",
            "export_simple",
            List.of(
                ListExpr.of(List.of(Variable.of("Element"))),
                AtomExpr.of("xmerl_xmlns"),
                ListExpr.of(List.of()),
                ListExpr.of(
                    List.of(TupleExpr.of(List.of(AtomExpr.of("prolog"), AtomExpr.of("false")))))));

    Expression body =
        MatchExpr.of(
            ListPattern.cons(
                TuplePattern.of(
                    List.of(VariablePattern.of("RootName"), VariablePattern.of("Content"))),
                ListPattern.of(List.of())),
            RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("RootMap"))),
            MatchExpr.bind(
                "Element",
                LocalCallExpr.of(
                    "build_xml_element",
                    List.of(Variable.of("RootName"), Variable.of("Content"), Variable.of("XmlNs"))),
                LocalCallExpr.of("iolist_to_binary", List.of(exportSimple))));

    return Function.of(
        "encode_xml",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("RootMap"), VariablePattern.of("XmlNs")), body)));
  }

  private static Function buildXmlElement() {
    Expression children =
        ListComprehensionExpr.of(
            LocalCallExpr.of("build_xml_child", List.of(Variable.of("K"), Variable.of("V"))),
            List.of(
                ListComprehensionGenerator.of(
                    TuplePattern.of(List.of(VariablePattern.of("K"), VariablePattern.of("V"))),
                    RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("Content")))),
                ListComprehensionFilter.of(
                    InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("undefined")))));

    Expression mapBody =
        MatchExpr.bind(
            "Attrs",
            LocalCallExpr.of("xml_namespace_attrs", List.of(Variable.of("XmlNs"))),
            MatchExpr.bind(
                "Children",
                children,
                TupleExpr.of(
                    List.of(Variable.of("Name"), Variable.of("Attrs"), Variable.of("Children")))));

    Expression scalarBody =
        TupleExpr.of(
            List.of(
                Variable.of("Name"),
                LocalCallExpr.of("xml_namespace_attrs", List.of(Variable.of("XmlNs"))),
                ListExpr.of(
                    List.of(
                        TupleExpr.of(
                            List.of(
                                AtomExpr.of("text"),
                                LocalCallExpr.of(
                                    "to_binary", List.of(Variable.of("Content")))))))));

    return Function.of(
        "build_xml_element",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Name"),
                    VariablePattern.of("Content"),
                    VariablePattern.of("XmlNs")),
                IsTypeGuard.of("map", Variable.of("Content")),
                mapBody),
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Name"),
                    VariablePattern.of("Content"),
                    VariablePattern.of("XmlNs")),
                scalarBody)));
  }

  private static Function buildXmlChild() {
    Expression mapValueBody =
        TupleExpr.of(
            List.of(
                Variable.of("Name"),
                ListExpr.of(List.of()),
                ListComprehensionExpr.of(
                    LocalCallExpr.of(
                        "build_xml_element",
                        List.of(Variable.of("K"), Variable.of("V"), MapExpr.of(List.of()))),
                    List.of(
                        ListComprehensionGenerator.of(
                            TuplePattern.of(
                                List.of(VariablePattern.of("K"), VariablePattern.of("V"))),
                            RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("Value")))),
                        ListComprehensionFilter.of(
                            InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("undefined")))))));

    Expression listValueBody =
        TupleExpr.of(
            List.of(
                Variable.of("Name"),
                ListExpr.of(List.of()),
                ListComprehensionExpr.of(
                    LocalCallExpr.of(
                        "build_xml_element",
                        List.of(BinaryExpr.of("member"), Variable.of("V"), MapExpr.of(List.of()))),
                    List.of(
                        ListComprehensionGenerator.of(
                            VariablePattern.of("V"), Variable.of("Values")),
                        ListComprehensionFilter.of(
                            InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("undefined")))))));

    Expression scalarValueBody =
        TupleExpr.of(
            List.of(
                Variable.of("Name"),
                ListExpr.of(List.of()),
                ListExpr.of(
                    List.of(
                        TupleExpr.of(
                            List.of(
                                AtomExpr.of("text"),
                                LocalCallExpr.of("to_binary", List.of(Variable.of("Value")))))))));

    return Function.of(
        "build_xml_child",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Name"), VariablePattern.of("Value")),
                IsTypeGuard.of("map", Variable.of("Value")),
                mapValueBody),
            FunctionClause.of(
                List.of(VariablePattern.of("Name"), VariablePattern.of("Values")),
                IsTypeGuard.of("list", Variable.of("Values")),
                listValueBody),
            FunctionClause.of(
                List.of(VariablePattern.of("Name"), VariablePattern.of("Value")),
                scalarValueBody)));
  }

  static Function xmlNamespace(Optional<String> namespaceUri) {
    if (namespaceUri.isPresent()) {
      return Function.of(
          "xml_namespace",
          List.of(
              FunctionClause.of(
                  List.of(),
                  MapExpr.of(
                      List.of(
                          MapEntry.of(AtomExpr.of("uri"), BinaryExpr.of(namespaceUri.get())))))));
    }
    return Function.of(
        "xml_namespace", List.of(FunctionClause.of(List.of(), MapExpr.of(List.of()))));
  }

  private static Function xmlNamespaceAttrs() {
    return Function.of(
        "xml_namespace_attrs",
        List.of(
            FunctionClause.of(
                List.of(
                    MapPattern.of(
                        List.of(
                            MapPatternEntry.of(
                                AtomExpr.of("uri"), VariablePattern.of("Uri"), true)))),
                ListExpr.of(
                    List.of(TupleExpr.of(List.of(AtomExpr.of("xmlns"), Variable.of("Uri")))))),
            FunctionClause.of(
                List.of(
                    MapPattern.of(
                        List.of(
                            MapPatternEntry.of(AtomExpr.of("uri"), VariablePattern.of("Uri"), true),
                            MapPatternEntry.of(
                                AtomExpr.of("prefix"), VariablePattern.of("Prefix"), true)))),
                ListExpr.of(
                    List.of(
                        TupleExpr.of(
                            List.of(
                                InfixExpr.of(
                                    QuotedAtomExpr.of("xmlns:"),
                                    "++",
                                    LocalCallExpr.of(
                                        "binary_to_list", List.of(Variable.of("Prefix")))),
                                Variable.of("Uri")))))),
            FunctionClause.of(List.of(W), ListExpr.of(List.of()))));
  }

  private static TuplePattern xmlElementTuple(
      Pattern namePattern, int contentIndex, String contentName, Pattern trailing) {
    List<Pattern> elements = new ArrayList<>();
    elements.add(AtomPattern.of("xmlElement"));
    for (int i = 1; i < 12; i++) {
      if (i == 1 && namePattern != W) {
        elements.add(namePattern);
      } else if (i == contentIndex && contentName != null) {
        elements.add(VariablePattern.of(contentName));
      } else if (i == 11) {
        elements.add(trailing);
      } else {
        elements.add(W);
      }
    }
    return TuplePattern.of(elements);
  }

  private static TuplePattern sixTupleWithContent() {
    return TuplePattern.of(List.of(W, W, VariablePattern.of("Content"), W, W, W));
  }

  private static TuplePattern sixTupleName() {
    return TuplePattern.of(List.of(VariablePattern.of("Name"), W, W, W, W, W));
  }

  private static TuplePattern xmlTextPattern() {
    return TuplePattern.of(List.of(AtomPattern.of("xmlText"), W, W, W, VariablePattern.of("V"), W));
  }
}
