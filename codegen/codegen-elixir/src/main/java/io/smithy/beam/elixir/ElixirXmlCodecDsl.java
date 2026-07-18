package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AnonFun;
import io.beam.dsl.elixir.AnonFunClause;
import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.AtomPattern;
import io.beam.dsl.elixir.BlockExpr;
import io.beam.dsl.elixir.CaptureExpr;
import io.beam.dsl.elixir.CaseExpr;
import io.beam.dsl.elixir.CatchClause;
import io.beam.dsl.elixir.Clause;
import io.beam.dsl.elixir.ComparisonGuard;
import io.beam.dsl.elixir.ConsListPattern;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.IfExpr;
import io.beam.dsl.elixir.InfixExpr;
import io.beam.dsl.elixir.IsTypeGuard;
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
import io.beam.dsl.elixir.StringExpr;
import io.beam.dsl.elixir.TryExpr;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.beam.dsl.elixir.WildcardPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ElixirXmlCodecDsl {
  private ElixirXmlCodecDsl() {}

  private static final Pattern W = WildcardPattern.of();

  static List<Function> restXmlDecodeHelpers() {
    List<Function> functions = new ArrayList<>();
    functions.addAll(parseXmlRoot());
    functions.addAll(decodePayload());
    functions.addAll(xmlElementNamed());
    functions.addAll(elementContent());
    functions.addAll(findElement());
    functions.addAll(isElement());
    functions.addAll(elementName());
    functions.addAll(xmlChildText());
    functions.addAll(elementText());
    functions.addAll(collectText());
    functions.addAll(isElementString());
    functions.addAll(xmlChildList());
    functions.addAll(xmlChildStructList());
    return functions;
  }

  static List<Function> restXmlEncodeHelpers() {
    List<Function> functions = new ArrayList<>();
    functions.addAll(encodeXml());
    functions.addAll(buildXmlElement());
    functions.addAll(buildXmlChild());
    functions.addAll(xmlNamespaceAttrs());
    return functions;
  }

  static List<Function> xmlChildList() {
    String name = "xml_child_list";
    return List.of(
        defp(
            name,
            List.of(VariablePattern.of("parent"), NilPattern.of(), VariablePattern.of("item_name")),
            childListPipeline(Variable.of("parent")),
            false),
        defp(
            name,
            List.of(
                VariablePattern.of("parent"),
                VariablePattern.of("list_name"),
                VariablePattern.of("item_name")),
            CaseExpr.of(
                LocalCallExpr.of(
                    "find_element",
                    List.of(
                        Variable.of("list_name"),
                        LocalCallExpr.of("element_content", List.of(Variable.of("parent"))))),
                List.of(
                    Clause.of(NilPattern.of(), NilExpr.of()),
                    Clause.of(
                        VariablePattern.of("list_element"),
                        childListPipeline(Variable.of("list_element"))))),
            false));
  }

  static List<Function> xmlNamespace(Optional<String> namespaceUri) {
    if (namespaceUri.isPresent()) {
      return List.of(
          defp(
              "xml_namespace",
              List.of(),
              MapExpr.of(List.of(MapEntry.atomKey("uri", StringExpr.of(namespaceUri.get())))),
              true));
    }
    return List.of(defp("xml_namespace", List.of(), MapExpr.of(List.of()), true));
  }

  static List<Function> elementText() {
    TuplePattern xmlElementContent = xmlElementContentPattern("content");
    return List.of(
        defp(
            "element_text",
            List.of(xmlElementContent),
            RemoteCallExpr.of(
                "Enum",
                "flat_map",
                List.of(Variable.of("content"), CaptureExpr.of("collect_text", 1))),
            false),
        defp("element_text", List.of(W), ListExpr.of(List.of()), true));
  }

  static List<Function> collectText() {
    String name = "collect_text";
    return List.of(
        defp(
            name,
            List.of(
                TuplePattern.of(
                    List.of(AtomPattern.of("xmlText"), W, W, W, VariablePattern.of("text"), W))),
            ListExpr.of(List.of(Variable.of("text"))),
            true),
        defp(
            name,
            List.of(VariablePattern.of("text")),
            IsTypeGuard.of("is_list", "text"),
            ListExpr.of(List.of(Variable.of("text"))),
            true),
        defp(name, List.of(W), ListExpr.of(List.of()), true));
  }

  static List<Function> isElementString() {
    String name = "is_element_string";
    return List.of(
        defp(name, List.of(xmlElementWildPattern()), AtomExpr.of("true"), true),
        defp(name, List.of(W), AtomExpr.of("false"), true));
  }

  private static List<Function> encodeXml() {
    return List.of(
        defp(
            "encode_xml",
            List.of(VariablePattern.of("root_map"), VariablePattern.of("xml_ns")),
            encodeXmlBody(),
            false));
  }

  private static Expression encodeXmlBody() {
    return BlockExpr.of(
        List.of(
            MatchExpr.bind(
                ConsListPattern.of(
                    TuplePattern.of(
                        List.of(VariablePattern.of("root_name"), VariablePattern.of("content"))),
                    NilPattern.of()),
                RemoteCallExpr.of("Map", "to_list", List.of(Variable.of("root_map"))),
                MatchExpr.bind(
                    "element",
                    LocalCallExpr.of(
                        "build_xml_element",
                        List.of(
                            Variable.of("root_name"),
                            Variable.of("content"),
                            Variable.of("xml_ns"))),
                    PipeExpr.of(
                        RemoteCallExpr.of(
                            ":xmerl",
                            "export_simple",
                            List.of(
                                ListExpr.of(List.of(Variable.of("element"))),
                                AtomExpr.of("xmerl_xmlns"),
                                ListExpr.of(List.of()),
                                ListExpr.of(
                                    List.of(
                                        TupleExpr.of(
                                            List.of(
                                                AtomExpr.of("prolog"), AtomExpr.of("false"))))))),
                        List.of(
                            PipeStep.of(
                                RemoteCallExpr.of(":erlang", "iolist_to_binary", List.of()),
                                List.of())))))));
  }

  private static List<Function> buildXmlElement() {
    Expression mapChildren =
        RemoteCallExpr.of(
            "Enum",
            "flat_map",
            List.of(
                RemoteCallExpr.of("Map", "to_list", List.of(Variable.of("content"))),
                AnonFun.of(
                    List.of(
                        AnonFunClause.of(
                            List.of(
                                TuplePattern.of(
                                    List.of(VariablePattern.of("k"), VariablePattern.of("v")))),
                            ComparisonGuard.of(Variable.of("v"), "!=", AtomExpr.of("nil")),
                            LocalCallExpr.of(
                                "build_xml_child",
                                List.of(Variable.of("k"), Variable.of("v"))))))));

    String name = "build_xml_element";
    return List.of(
        defp(
            name,
            List.of(
                VariablePattern.of("name"),
                VariablePattern.of("content"),
                VariablePattern.of("xml_ns")),
            IsTypeGuard.of("is_map", "content"),
            BlockExpr.of(
                List.of(
                    MatchExpr.bind(
                        "attrs",
                        LocalCallExpr.of("xml_namespace_attrs", List.of(Variable.of("xml_ns"))),
                        MatchExpr.bind(
                            "children",
                            mapChildren,
                            TupleExpr.of(
                                List.of(
                                    Variable.of("name"),
                                    Variable.of("attrs"),
                                    Variable.of("children"))))))),
            false),
        defp(
            name,
            List.of(
                VariablePattern.of("name"),
                VariablePattern.of("content"),
                VariablePattern.of("xml_ns")),
            TupleExpr.of(
                List.of(
                    Variable.of("name"),
                    LocalCallExpr.of("xml_namespace_attrs", List.of(Variable.of("xml_ns"))),
                    ListExpr.of(
                        List.of(
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("text"),
                                    LocalCallExpr.of(
                                        "to_binary", List.of(Variable.of("content"))))))))),
            false));
  }

  private static List<Function> buildXmlChild() {
    Expression mapChildren =
        RemoteCallExpr.of(
            "Enum",
            "flat_map",
            List.of(
                RemoteCallExpr.of("Map", "to_list", List.of(Variable.of("value"))),
                AnonFun.of(
                    List.of(
                        AnonFunClause.of(
                            List.of(
                                TuplePattern.of(
                                    List.of(VariablePattern.of("k"), VariablePattern.of("v")))),
                            ComparisonGuard.of(Variable.of("v"), "!=", AtomExpr.of("nil")),
                            LocalCallExpr.of(
                                "build_xml_element",
                                List.of(
                                    Variable.of("k"),
                                    Variable.of("v"),
                                    MapExpr.of(List.of()))))))));

    Expression listChildren =
        RemoteCallExpr.of(
            "Enum",
            "flat_map",
            List.of(
                Variable.of("values"),
                AnonFun.of(
                    List.of(
                        AnonFunClause.of(
                            List.of(VariablePattern.of("v")),
                            ComparisonGuard.of(Variable.of("v"), "!=", AtomExpr.of("nil")),
                            LocalCallExpr.of(
                                "build_xml_element",
                                List.of(
                                    AtomExpr.of("member"),
                                    Variable.of("v"),
                                    MapExpr.of(List.of()))))))));

    String name = "build_xml_child";
    return List.of(
        defp(
            name,
            List.of(VariablePattern.of("name"), VariablePattern.of("value")),
            IsTypeGuard.of("is_map", "value"),
            BlockExpr.of(
                List.of(
                    MatchExpr.bind(
                        "children",
                        mapChildren,
                        TupleExpr.of(
                            List.of(
                                Variable.of("name"),
                                ListExpr.of(List.of()),
                                Variable.of("children")))))),
            false),
        defp(
            name,
            List.of(VariablePattern.of("name"), VariablePattern.of("values")),
            IsTypeGuard.of("is_list", "values"),
            BlockExpr.of(
                List.of(
                    MatchExpr.bind(
                        "children",
                        listChildren,
                        TupleExpr.of(
                            List.of(
                                Variable.of("name"),
                                ListExpr.of(List.of()),
                                Variable.of("children")))))),
            false),
        defp(
            name,
            List.of(VariablePattern.of("name"), VariablePattern.of("value")),
            TupleExpr.of(
                List.of(
                    Variable.of("name"),
                    ListExpr.of(List.of()),
                    ListExpr.of(
                        List.of(
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("text"),
                                    LocalCallExpr.of(
                                        "to_binary", List.of(Variable.of("value"))))))))),
            true));
  }

  private static List<Function> xmlNamespaceAttrs() {
    String name = "xml_namespace_attrs";
    return List.of(
        defp(
            name,
            List.of(
                MapPattern.of(
                    List.of(MapPatternEntry.of(AtomExpr.of("uri"), VariablePattern.of("uri"))))),
            ListExpr.of(List.of(TupleExpr.of(List.of(AtomExpr.of("xmlns"), Variable.of("uri"))))),
            true),
        defp(name, List.of(W), ListExpr.of(List.of()), true));
  }

  private static List<Function> parseXmlRoot() {
    return List.of(
        defp(
            "parse_xml_root",
            List.of(VariablePattern.of("body"), VariablePattern.of("root_name")),
            parseXmlRootBody(),
            false));
  }

  private static Expression parseXmlRootBody() {
    Expression rootSelection =
        IfExpr.of(
            LocalCallExpr.of(
                "xml_element_named", List.of(Variable.of("xml"), Variable.of("root_name"))),
            TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("xml"))),
            CaseExpr.of(
                LocalCallExpr.of(
                    "find_element",
                    List.of(
                        Variable.of("root_name"),
                        LocalCallExpr.of("element_content", List.of(Variable.of("xml"))))),
                List.of(
                    Clause.of(
                        NilPattern.of(),
                        TupleExpr.of(
                            List.of(
                                AtomExpr.of("error"),
                                TupleExpr.of(
                                    List.of(
                                        AtomExpr.of("missing_root"), Variable.of("root_name")))))),
                    Clause.of(
                        VariablePattern.of("root"),
                        TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("root")))))),
            false);

    Expression tryBody =
        BlockExpr.of(
            List.of(
                MatchExpr.bind(
                    TuplePattern.of(List.of(VariablePattern.of("xml"), WildcardPattern.of())),
                    RemoteCallExpr.of(
                        ":xmerl_scan",
                        "string",
                        List.of(
                            RemoteCallExpr.of(
                                "String", "to_charlist", List.of(Variable.of("body"))))),
                    rootSelection)));

    return TryExpr.of(
        tryBody,
        List.of(
            CatchClause.of(
                WildcardPattern.of(),
                VariablePattern.of("reason"),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("error"),
                        TupleExpr.of(
                            List.of(AtomExpr.of("xml_parse_error"), Variable.of("reason"))))))));
  }

  private static List<Function> decodePayload() {
    return List.of(
        defp(
            "decode_payload",
            List.of(VariablePattern.of("body"), VariablePattern.of("root_name")),
            CaseExpr.of(
                LocalCallExpr.of(
                    "parse_xml_root", List.of(Variable.of("body"), Variable.of("root_name"))),
                List.of(
                    Clause.of(TuplePattern.of(List.of(AtomPattern.of("ok"), W)), NilExpr.of()),
                    Clause.of(TuplePattern.of(List.of(AtomPattern.of("error"), W)), NilExpr.of()))),
            false));
  }

  private static List<Function> xmlElementNamed() {
    return List.of(
        defp(
            "xml_element_named",
            List.of(VariablePattern.of("element"), VariablePattern.of("name")),
            InfixExpr.of(
                LocalCallExpr.of("is_element", List.of(Variable.of("element"))),
                "and",
                InfixExpr.of(
                    LocalCallExpr.of("element_name", List.of(Variable.of("element"))),
                    "==",
                    Variable.of("name"))),
            true));
  }

  private static List<Function> elementContent() {
    TuplePattern xmlElement = xmlElementContentPattern("content");
    TuplePattern sixTuple = TuplePattern.of(List.of(W, W, VariablePattern.of("content"), W, W, W));
    String name = "element_content";
    return List.of(
        defp(name, List.of(xmlElement), Variable.of("content"), true),
        defp(
            name,
            List.of(sixTuple),
            IsTypeGuard.of("is_list", "content"),
            Variable.of("content"),
            true),
        defp(
            name,
            List.of(ConsListPattern.of(VariablePattern.of("h"), W)),
            LocalCallExpr.of("element_content", List.of(Variable.of("h"))),
            true),
        defp(name, List.of(W), ListExpr.of(List.of()), true));
  }

  private static List<Function> findElement() {
    return List.of(
        defp(
            "find_element",
            List.of(VariablePattern.of("name"), VariablePattern.of("content")),
            RemoteCallExpr.of(
                "Enum",
                "find_value",
                List.of(
                    Variable.of("content"),
                    AnonFun.of(
                        List.of(
                            AnonFunClause.of(
                                List.of(VariablePattern.of("item")),
                                IfExpr.of(
                                    InfixExpr.of(
                                        LocalCallExpr.of(
                                            "is_element", List.of(Variable.of("item"))),
                                        "and",
                                        InfixExpr.of(
                                            LocalCallExpr.of(
                                                "element_name", List.of(Variable.of("item"))),
                                            "==",
                                            Variable.of("name"))),
                                    Variable.of("item"),
                                    NilExpr.of(),
                                    false)))))),
            false));
  }

  private static List<Function> isElement() {
    TuplePattern xmlElement = xmlElementWildPattern();
    TuplePattern sixTuple = TuplePattern.of(List.of(W, W, VariablePattern.of("content"), W, W, W));
    String name = "is_element";
    return List.of(
        defp(name, List.of(xmlElement), AtomExpr.of("true"), true),
        defp(
            name,
            List.of(sixTuple),
            IsTypeGuard.of("is_list", "content"),
            AtomExpr.of("true"),
            true),
        defp(name, List.of(W), AtomExpr.of("false"), true));
  }

  private static List<Function> elementName() {
    TuplePattern xmlElementName =
        TuplePattern.of(
            List.of(
                AtomPattern.of("xmlElement"),
                VariablePattern.of("name"),
                W,
                W,
                W,
                W,
                W,
                W,
                W,
                W,
                W,
                W));
    TuplePattern sixTupleName = TuplePattern.of(List.of(VariablePattern.of("name"), W, W, W, W, W));
    String name = "element_name";
    return List.of(
        defp(
            name,
            List.of(xmlElementName),
            IsTypeGuard.of("is_atom", "name"),
            RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("name"))),
            true),
        defp(
            name,
            List.of(xmlElementName),
            IsTypeGuard.of("is_list", "name"),
            RemoteCallExpr.of("List", "to_string", List.of(Variable.of("name"))),
            true),
        defp(
            name,
            List.of(xmlElementName),
            IsTypeGuard.of("is_binary", "name"),
            Variable.of("name"),
            true),
        defp(
            name,
            List.of(sixTupleName),
            IsTypeGuard.of("is_atom", "name"),
            RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("name"))),
            true),
        defp(
            name,
            List.of(sixTupleName),
            IsTypeGuard.of("is_list", "name"),
            RemoteCallExpr.of("List", "to_string", List.of(Variable.of("name"))),
            true),
        defp(
            name,
            List.of(sixTupleName),
            IsTypeGuard.of("is_binary", "name"),
            Variable.of("name"),
            true));
  }

  private static List<Function> xmlChildText() {
    return List.of(
        defp(
            "xml_child_text",
            List.of(VariablePattern.of("parent"), VariablePattern.of("name")),
            CaseExpr.of(
                LocalCallExpr.of(
                    "find_element",
                    List.of(
                        Variable.of("name"),
                        LocalCallExpr.of("element_content", List.of(Variable.of("parent"))))),
                List.of(
                    Clause.of(NilPattern.of(), NilExpr.of()),
                    Clause.of(
                        VariablePattern.of("element"),
                        CaseExpr.of(
                            LocalCallExpr.of("element_text", List.of(Variable.of("element"))),
                            List.of(
                                Clause.of(ListPattern.of(List.of()), NilExpr.of()),
                                Clause.of(
                                    ConsListPattern.of(VariablePattern.of("text"), W),
                                    RemoteCallExpr.of(
                                        "List", "to_string", List.of(Variable.of("text"))))))))),
            false));
  }

  private static List<Function> xmlChildStructList() {
    Expression filterMapPipeline =
        childListPipelineWithDecodeFun(Variable.of("parent"), Variable.of("decode_fun"));

    Expression listNameBody =
        CaseExpr.of(
            LocalCallExpr.of(
                "find_element",
                List.of(
                    Variable.of("list_name"),
                    LocalCallExpr.of("element_content", List.of(Variable.of("parent"))))),
            List.of(
                Clause.of(NilPattern.of(), NilExpr.of()),
                Clause.of(
                    VariablePattern.of("list_element"),
                    childListPipelineWithDecodeFun(
                        Variable.of("list_element"), Variable.of("decode_fun")))));

    String name = "xml_child_struct_list";
    return List.of(
        defp(
            name,
            List.of(
                VariablePattern.of("parent"),
                NilPattern.of(),
                VariablePattern.of("item_name"),
                VariablePattern.of("decode_fun")),
            filterMapPipeline,
            false),
        defp(
            name,
            List.of(
                VariablePattern.of("parent"),
                VariablePattern.of("list_name"),
                VariablePattern.of("item_name"),
                VariablePattern.of("decode_fun")),
            listNameBody,
            false));
  }

  private static Expression childListPipeline(Expression root) {
    return PipeExpr.of(
        root,
        List.of(
            PipeStep.of(LocalCallExpr.of("element_content", List.of()), List.of()),
            PipeStep.of(elementFilterCall(), List.of()),
            PipeStep.of(elementTextMapCall(), List.of()),
            PipeStep.of(rejectNilCall(), List.of())));
  }

  private static Expression elementFilterCall() {
    return RemoteCallExpr.of(
        "Enum",
        "filter",
        List.of(
            AnonFun.of(
                List.of(
                    AnonFunClause.of(
                        List.of(VariablePattern.of("item")),
                        InfixExpr.of(
                            LocalCallExpr.of("is_element", List.of(Variable.of("item"))),
                            "and",
                            InfixExpr.of(
                                LocalCallExpr.of("element_name", List.of(Variable.of("item"))),
                                "==",
                                Variable.of("item_name"))))))));
  }

  private static Expression elementTextMapCall() {
    return RemoteCallExpr.of(
        "Enum",
        "map",
        List.of(
            AnonFun.of(
                List.of(
                    AnonFunClause.of(
                        List.of(VariablePattern.of("item")), elementTextCaseExpr())))));
  }

  private static Expression elementTextCaseExpr() {
    return CaseExpr.of(
        LocalCallExpr.of("element_text", List.of(Variable.of("item"))),
        List.of(
            Clause.of(ListPattern.of(List.of()), NilExpr.of()),
            Clause.of(
                ConsListPattern.of(VariablePattern.of("text"), W),
                RemoteCallExpr.of("List", "to_string", List.of(Variable.of("text"))))));
  }

  private static Expression rejectNilCall() {
    return RemoteCallExpr.of(
        "Enum",
        "reject",
        List.of(
            AnonFun.of(
                List.of(
                    AnonFunClause.of(
                        List.of(VariablePattern.of("x")),
                        LocalCallExpr.of("is_nil", List.of(Variable.of("x"))))))));
  }

  private static Expression childListPipelineWithDecodeFun(Expression root, Expression decodeFun) {
    return PipeExpr.of(
        root,
        List.of(
            PipeStep.of(LocalCallExpr.of("element_content", List.of()), List.of()),
            PipeStep.of(elementFilterCall(), List.of()),
            PipeStep.of(
                RemoteCallExpr.of("Enum", "map", List.of(Variable.of("decode_fun"))), List.of())));
  }

  private static TuplePattern xmlElementContentPattern(String contentVar) {
    return TuplePattern.of(
        List.of(
            AtomPattern.of("xmlElement"),
            W,
            W,
            W,
            W,
            W,
            W,
            W,
            VariablePattern.of(contentVar),
            W,
            W,
            W));
  }

  private static TuplePattern xmlElementWildPattern() {
    return TuplePattern.of(List.of(AtomPattern.of("xmlElement"), W, W, W, W, W, W, W, W, W, W, W));
  }

  private static Function defp(
      String name, List<Pattern> params, Expression body, boolean oneLiner) {
    return Function.of(name, true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
  }

  private static Function defp(
      String name, List<Pattern> params, IsTypeGuard guard, Expression body, boolean oneLiner) {
    return Function.of(
        name, true, List.of(FunctionHead.of(params, guard)), body, null, null, oneLiner);
  }
}
