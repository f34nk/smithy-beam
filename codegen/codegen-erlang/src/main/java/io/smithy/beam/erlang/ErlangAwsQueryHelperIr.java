package io.smithy.beam.erlang;

import io.beam.ir.erlang.ApplyExpr;
import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.BinarySegmentExpr;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.CatchPattern;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Expression;
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
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.NotExpr;
import io.beam.ir.erlang.Pattern;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.TryExpr;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.beam.ir.erlang.WildcardPattern;
import io.smithy.beam.core.BeamXmlDecoder;
import java.util.ArrayList;
import java.util.List;

final class ErlangAwsQueryHelperIr {
  private ErlangAwsQueryHelperIr() {}

  private static final WildcardPattern W = WildcardPattern.of();

  static List<Function> queryHelperFunctions(boolean ec2Query) {
    return List.of(flattenMember(ec2Query), enc());
  }

  static List<Function> xmlHelperFunctions(boolean ec2Query) {
    List<Function> functions = new ArrayList<>();
    functions.add(unwrapQueryResult());
    functions.add(normalizeXmlElement());
    functions.add(queryResultElement());
    functions.addAll(awsQueryXmlElementHelpers());
    functions.add(awsQueryXmlChildStructList());
    functions.add(awsQueryXmlChildList());
    functions.add(decodeQueryError(ec2Query));
    return functions;
  }

  private static List<Function> awsQueryXmlElementHelpers() {
    return List.of(
        ErlangXmlCodecIr.elementContentFunction(),
        awsQueryFindElement(),
        ErlangXmlCodecIr.isElementFunction(),
        awsQueryElementName(),
        ErlangXmlCodecIr.xmlChildTextFunction(),
        awsQueryElementText(),
        awsQueryXmlTextValues(),
        ErlangXmlCodecIr.isElementStringFunction());
  }

  private static Function awsQueryFindElement() {
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
                            ListPattern.cons(VariablePattern.of("Element"), WildcardPattern.of()),
                            Variable.of("Element")),
                        Clause.of(ListPattern.of(List.of()), AtomExpr.of("undefined")))))));
  }

  private static Function awsQueryElementName() {
    return Function.of(
        "element_name",
        List.of(
            FunctionClause.of(
                List.of(xmlElementNamePattern()),
                IsTypeGuard.of("atom", Variable.of("Name")),
                LocalCallExpr.of(
                    "list_to_binary",
                    List.of(LocalCallExpr.of("atom_to_list", List.of(Variable.of("Name")))))),
            FunctionClause.of(
                List.of(xmlElementNamePattern()),
                IsTypeGuard.of("list", Variable.of("Name")),
                LocalCallExpr.of("list_to_binary", List.of(Variable.of("Name")))),
            FunctionClause.of(
                List.of(xmlElementNamePattern()),
                IsTypeGuard.of("binary", Variable.of("Name")),
                Variable.of("Name")),
            FunctionClause.of(
                List.of(sixTupleNamePattern()),
                IsTypeGuard.of("atom", Variable.of("Name")),
                LocalCallExpr.of(
                    "list_to_binary",
                    List.of(LocalCallExpr.of("atom_to_list", List.of(Variable.of("Name")))))),
            FunctionClause.of(
                List.of(sixTupleNamePattern()),
                IsTypeGuard.of("list", Variable.of("Name")),
                LocalCallExpr.of("list_to_binary", List.of(Variable.of("Name")))),
            FunctionClause.of(
                List.of(sixTupleNamePattern()),
                IsTypeGuard.of("binary", Variable.of("Name")),
                Variable.of("Name"))));
  }

  private static Function awsQueryElementText() {
    return Function.of(
        "element_text",
        List.of(
            FunctionClause.of(
                List.of(xmlElementContentPattern()),
                LocalCallExpr.of("xml_text_values", List.of(Variable.of("Content")))),
            FunctionClause.of(
                List.of(sixTupleContentPattern()),
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

  private static Function awsQueryXmlTextValues() {
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
                            textCase,
                            List.of(
                                ListComprehensionGenerator.of(
                                    VariablePattern.of("C"), Variable.of("Content")))))))));
  }

  private static TuplePattern xmlTextPattern() {
    return TuplePattern.of(List.of(AtomPattern.of("xmlText"), W, W, W, VariablePattern.of("V"), W));
  }

  private static Function awsQueryXmlChildStructList() {
    Expression decodeItem = ApplyExpr.of(Variable.of("DecodeFun"), List.of(Variable.of("Item")));

    return Function.of(
        "xml_child_struct_list",
        List.of(
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

  private static Function awsQueryXmlChildList() {
    List<io.beam.ir.erlang.ListComprehensionQualifier> itemQualifiers =
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
                                LocalCallExpr.of("element_text", List.of(Variable.of("Item")))))))),
            ListComprehensionFilter.of(
                InfixExpr.of(Variable.of("ItemText"), "=/=", BinaryExpr.of(""))));

    return Function.of(
        "xml_child_list",
        List.of(
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
                        Clause.of(
                            VariablePattern.of("ListElement"),
                            ListComprehensionExpr.of(Variable.of("ItemText"), itemQualifiers)))))));
  }

  private static TuplePattern xmlElementNamePattern() {
    return xmlElementTuple(VariablePattern.of("Name"), -1, null, W);
  }

  private static TuplePattern sixTupleNamePattern() {
    return TuplePattern.of(List.of(VariablePattern.of("Name"), W, W, W, W, W));
  }

  private static TuplePattern xmlElementContentPattern() {
    return xmlElementTuple(W, 8, "Content", W);
  }

  private static TuplePattern sixTupleContentPattern() {
    return TuplePattern.of(List.of(W, W, VariablePattern.of("Content"), W, W, W));
  }

  static List<Function> serverQueryDecodeHelperFunctions(boolean ec2Query) {
    return List.of(
        parseQueryParams(),
        formValue(),
        ec2Query ? formListValuesEc2() : formListValuesAws(),
        indexedFormValues(),
        formIndex());
  }

  static List<Function> serverXmlEncodeHelperFunctions(boolean ec2Query) {
    List<Function> functions = new ArrayList<>();
    if (!ec2Query) {
      functions.add(wrapAwsQueryResponse());
    }
    functions.addAll(ErlangXmlCodecIr.awsQueryServerEncodeHelpers());
    return functions;
  }

  private static Function flattenMember(boolean ec2Query) {
    String listSuffix = ec2Query ? "." : ".member.";
    Expression listBody =
        RemoteCallExpr.of("lists", "append", List.of(flattenMemberListComprehension(listSuffix)));
    Expression mapBody =
        RemoteCallExpr.of("lists", "append", List.of(flattenMemberMapComprehension()));

    return Function.of(
        "flatten_member",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("_Key"), AtomPattern.of("undefined")),
                ListExpr.of(List.of())),
            FunctionClause.of(
                List.of(VariablePattern.of("Key"), VariablePattern.of("Value")),
                IsTypeGuard.of("list", Variable.of("Value")),
                listBody),
            FunctionClause.of(
                List.of(VariablePattern.of("Key"), VariablePattern.of("Value")),
                IsTypeGuard.of("map", Variable.of("Value")),
                mapBody),
            FunctionClause.of(
                List.of(VariablePattern.of("Key"), VariablePattern.of("Value")),
                IsTypeGuard.of("tuple", Variable.of("Value")),
                LocalCallExpr.of(
                    "flatten_structure", List.of(Variable.of("Key"), Variable.of("Value")))),
            FunctionClause.of(
                List.of(VariablePattern.of("Key"), VariablePattern.of("Value")),
                ListExpr.of(
                    List.of(TupleExpr.of(List.of(Variable.of("Key"), Variable.of("Value"))))))));
  }

  private static ListComprehensionExpr flattenMemberListComprehension(String listSuffix) {
    return ListComprehensionExpr.of(
        LocalCallExpr.of(
            "flatten_member", List.of(flattenMemberIndexedKey(listSuffix), Variable.of("V"))),
        List.of(
            ListComprehensionGenerator.of(
                TuplePattern.of(List.of(VariablePattern.of("I"), VariablePattern.of("V"))),
                RemoteCallExpr.of("lists", "enumerate", List.of(Variable.of("Value")))),
            ListComprehensionFilter.of(
                InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("undefined")))));
  }

  private static ListComprehensionExpr flattenMemberMapComprehension() {
    return ListComprehensionExpr.of(
        InfixExpr.of(
            LocalCallExpr.of(
                "flatten_member", List.of(flattenMemberEntryKey(".key"), Variable.of("K"))),
            "++",
            LocalCallExpr.of(
                "flatten_member", List.of(flattenMemberEntryKey(".value"), Variable.of("V")))),
        List.of(
            ListComprehensionGenerator.of(
                TuplePattern.of(
                    List.of(
                        VariablePattern.of("I"),
                        TuplePattern.of(
                            List.of(VariablePattern.of("K"), VariablePattern.of("V"))))),
                RemoteCallExpr.of(
                    "lists",
                    "enumerate",
                    List.of(RemoteCallExpr.of("maps", "to_list", List.of(Variable.of("Value")))))),
            ListComprehensionFilter.of(
                InfixExpr.of(Variable.of("K"), "=/=", AtomExpr.of("undefined"))),
            ListComprehensionFilter.of(
                InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("undefined")))));
  }

  private static Expression flattenMemberIndexedKey(String listSuffix) {
    return BinaryExpr.of(
        List.of(
            BinarySegmentExpr.of(Variable.of("Key"), "binary"),
            BinarySegmentExpr.literal(listSuffix),
            BinarySegmentExpr.of(
                LocalCallExpr.of("integer_to_binary", List.of(Variable.of("I"))), "binary")));
  }

  private static Expression flattenMemberEntryKey(String suffix) {
    return BinaryExpr.of(
        List.of(
            BinarySegmentExpr.of(Variable.of("Key"), "binary"),
            BinarySegmentExpr.literal(".entry."),
            BinarySegmentExpr.of(
                LocalCallExpr.of("integer_to_binary", List.of(Variable.of("I"))), "binary"),
            BinarySegmentExpr.literal(suffix)));
  }

  private static Function enc() {
    return Function.of(
        "enc",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("boolean", Variable.of("V")),
                LocalCallExpr.of("atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("integer", Variable.of("V")),
                LocalCallExpr.of("integer_to_binary", List.of(Variable.of("V")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("float", Variable.of("V")),
                LocalCallExpr.of("float_to_binary", List.of(Variable.of("V")))),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("binary", Variable.of("V")),
                Variable.of("V")),
            FunctionClause.of(
                List.of(VariablePattern.of("V")),
                IsTypeGuard.of("atom", Variable.of("V")),
                LocalCallExpr.of(
                    "atom_to_binary", List.of(Variable.of("V"), AtomExpr.of("utf8"))))));
  }

  private static Function unwrapQueryResult() {
    Expression resultLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "query_result_element", List.of(Variable.of("Root"), Variable.of("ResultName"))),
            List.of(
                Clause.of(
                    AtomPattern.of("undefined"),
                    TupleExpr.of(
                        List.of(
                            AtomExpr.of("error"),
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("missing_result"), Variable.of("ResultName")))))),
                Clause.of(
                    VariablePattern.of("Result"),
                    TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("Result"))))));

    Expression body =
        TryExpr.of(
            BlockExpr.commaSeparated(
                List.of(
                    MatchExpr.of(
                        TuplePattern.of(List.of(VariablePattern.of("Xml"), WildcardPattern.of())),
                        RemoteCallExpr.of(
                            "xmerl_scan",
                            "string",
                            List.of(
                                LocalCallExpr.of("binary_to_list", List.of(Variable.of("Body"))))),
                        MatchExpr.bindValue(
                            "Root",
                            LocalCallExpr.of(
                                "normalize_xml_element", List.of(Variable.of("Xml"))))),
                    resultLookup),
                false),
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
        "unwrap_query_result",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Body"), VariablePattern.of("ResultName")), body)));
  }

  private static Function normalizeXmlElement() {
    return Function.of(
        "normalize_xml_element",
        List.of(
            FunctionClause.of(
                List.of(ListPattern.cons(VariablePattern.of("H"), WildcardPattern.of())),
                LocalCallExpr.of("normalize_xml_element", List.of(Variable.of("H")))),
            FunctionClause.of(List.of(VariablePattern.of("Element")), Variable.of("Element"))));
  }

  private static Function queryResultElement() {
    return Function.of(
        "query_result_element",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Element"), VariablePattern.of("ResultName")),
                CaseExpr.of(
                    InfixExpr.of(
                        LocalCallExpr.of("is_element", List.of(Variable.of("Element"))),
                        "andalso",
                        InfixExpr.of(
                            LocalCallExpr.of("element_name", List.of(Variable.of("Element"))),
                            "=:=",
                            Variable.of("ResultName"))),
                    List.of(
                        Clause.of(AtomPattern.of("true"), Variable.of("Element")),
                        Clause.of(
                            AtomPattern.of("false"),
                            LocalCallExpr.of(
                                "find_element",
                                List.of(
                                    Variable.of("ResultName"),
                                    LocalCallExpr.of(
                                        "element_content", List.of(Variable.of("Element")))))))))));
  }

  private static Function decodeQueryError(boolean ec2Query) {
    if (ec2Query) {
      return decodeEc2QueryError();
    }
    return decodeAwsQueryError();
  }

  private static Function decodeAwsQueryError() {
    return Function.of(
        "decode_query_error",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Status"), VariablePattern.of("Body")),
                decodeQueryErrorBody(
                    BeamXmlDecoder.ERROR_RESPONSE_ELEMENT,
                    BeamXmlDecoder.ERROR_ELEMENT,
                    BeamXmlDecoder.ERROR_CODE_ELEMENT,
                    BeamXmlDecoder.ERROR_MESSAGE_ELEMENT))));
  }

  private static Function decodeEc2QueryError() {
    return Function.of(
        "decode_query_error",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Status"), VariablePattern.of("Body")),
                decodeQueryErrorBodyEc2())));
  }

  private static Expression decodeQueryErrorBody(
      String responseElement, String errorElement, String codeElement, String messageElement) {
    Expression errorLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "find_element",
                List.of(
                    BinaryExpr.of(errorElement),
                    LocalCallExpr.of("element_content", List.of(Variable.of("ErrorResponse"))))),
            List.of(
                Clause.of(AtomPattern.of("undefined"), unknownQueryError()),
                Clause.of(
                    VariablePattern.of("Error"),
                    queryErrorTuple(
                        LocalCallExpr.of(
                            "xml_child_text",
                            List.of(Variable.of("Error"), BinaryExpr.of(codeElement))),
                        LocalCallExpr.of(
                            "xml_child_text",
                            List.of(Variable.of("Error"), BinaryExpr.of(messageElement)))))));

    Expression responseLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "query_result_element",
                List.of(Variable.of("Root"), BinaryExpr.of(responseElement))),
            List.of(
                Clause.of(AtomPattern.of("undefined"), unknownQueryError()),
                Clause.of(VariablePattern.of("ErrorResponse"), errorLookup)));

    return queryErrorTryBody(responseLookup);
  }

  private static Expression decodeQueryErrorBodyEc2() {
    Expression errorLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "find_element",
                List.of(
                    BinaryExpr.of(BeamXmlDecoder.ERROR_ELEMENT),
                    LocalCallExpr.of("element_content", List.of(Variable.of("Error"))))),
            List.of(
                Clause.of(AtomPattern.of("undefined"), unknownQueryError()),
                Clause.of(
                    VariablePattern.of("Error"),
                    queryErrorTuple(
                        LocalCallExpr.of(
                            "xml_child_text",
                            List.of(
                                Variable.of("Error"),
                                BinaryExpr.of(BeamXmlDecoder.ERROR_CODE_ELEMENT))),
                        LocalCallExpr.of(
                            "xml_child_text",
                            List.of(
                                Variable.of("Error"),
                                BinaryExpr.of(BeamXmlDecoder.ERROR_MESSAGE_ELEMENT)))))));

    Expression errorsLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "find_element",
                List.of(
                    BinaryExpr.of(BeamXmlDecoder.EC2_ERRORS_ELEMENT),
                    LocalCallExpr.of("element_content", List.of(Variable.of("Response"))))),
            List.of(
                Clause.of(AtomPattern.of("undefined"), unknownQueryError()),
                Clause.of(
                    VariablePattern.of("Errors"),
                    CaseExpr.of(
                        LocalCallExpr.of(
                            "find_element",
                            List.of(
                                BinaryExpr.of(BeamXmlDecoder.ERROR_ELEMENT),
                                LocalCallExpr.of(
                                    "element_content", List.of(Variable.of("Errors"))))),
                        List.of(
                            Clause.of(AtomPattern.of("undefined"), unknownQueryError()),
                            Clause.of(VariablePattern.of("Error"), errorLookup))))));

    Expression responseLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "query_result_element",
                List.of(Variable.of("Root"), BinaryExpr.of(BeamXmlDecoder.EC2_RESPONSE_ELEMENT))),
            List.of(
                Clause.of(AtomPattern.of("undefined"), unknownQueryError()),
                Clause.of(VariablePattern.of("Response"), errorsLookup)));

    return queryErrorTryBody(responseLookup);
  }

  private static Expression queryErrorTuple(Expression codeExpr, Expression messageExpr) {
    return TupleExpr.of(
        List.of(AtomExpr.of("error"), TupleExpr.of(List.of(codeExpr, messageExpr))));
  }

  private static Expression queryErrorTryBody(Expression resultLookup) {
    return TryExpr.of(
        BlockExpr.commaSeparated(
            List.of(
                MatchExpr.of(
                    TuplePattern.of(List.of(VariablePattern.of("Xml"), WildcardPattern.of())),
                    RemoteCallExpr.of(
                        "xmerl_scan",
                        "string",
                        List.of(LocalCallExpr.of("binary_to_list", List.of(Variable.of("Body"))))),
                    MatchExpr.bindValue(
                        "Root",
                        LocalCallExpr.of("normalize_xml_element", List.of(Variable.of("Xml"))))),
                resultLookup),
            false),
        List.of(Clause.of(CatchPattern.anyAny(), unknownQueryError())));
  }

  private static Expression unknownQueryError() {
    return TupleExpr.of(
        List.of(
            AtomExpr.of("error"),
            TupleExpr.of(
                List.of(
                    AtomExpr.of("unknown_error"), Variable.of("Status"), Variable.of("Body")))));
  }

  private static Function parseQueryParams() {
    return Function.of(
        "parse_query_params",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Body")),
                RemoteCallExpr.of(
                    "maps",
                    "from_list",
                    List.of(
                        RemoteCallExpr.of(
                            "uri_string", "dissect_query", List.of(Variable.of("Body"))))))));
  }

  private static Function formValue() {
    return Function.of(
        "form_value",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Params"), VariablePattern.of("Key")),
                RemoteCallExpr.of(
                    "maps",
                    "get",
                    List.of(
                        Variable.of("Key"), Variable.of("Params"), AtomExpr.of("undefined"))))));
  }

  private static Function formListValuesAws() {
    return Function.of(
        "form_list_values_aws",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Params"), VariablePattern.of("Key")),
                BlockExpr.commaSeparated(
                    List.of(
                        MatchExpr.bindValue("Prefix", formListPrefix(".member.")),
                        LocalCallExpr.of(
                            "indexed_form_values",
                            List.of(Variable.of("Params"), Variable.of("Prefix")))),
                    false))));
  }

  private static Function formListValuesEc2() {
    return Function.of(
        "form_list_values_ec2",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Params"), VariablePattern.of("Key")),
                BlockExpr.commaSeparated(
                    List.of(
                        MatchExpr.bindValue("Prefix", formListPrefix(".")),
                        LocalCallExpr.of(
                            "indexed_form_values",
                            List.of(Variable.of("Params"), Variable.of("Prefix")))),
                    false))));
  }

  private static Expression formListPrefix(String suffix) {
    return BinaryExpr.of(
        List.of(
            BinarySegmentExpr.of(Variable.of("Key"), "binary"), BinarySegmentExpr.literal(suffix)));
  }

  private static Function indexedFormValues() {
    ListComprehensionExpr entries =
        ListComprehensionExpr.of(
            TupleExpr.of(
                List.of(
                    LocalCallExpr.of(
                        "form_index", List.of(Variable.of("K"), Variable.of("Prefix"))),
                    RemoteCallExpr.of(
                        "maps", "get", List.of(Variable.of("K"), Variable.of("Params"))))),
            List.of(
                ListComprehensionGenerator.of(
                    VariablePattern.of("K"),
                    RemoteCallExpr.of("maps", "keys", List.of(Variable.of("Params")))),
                ListComprehensionFilter.of(
                    InfixExpr.of(
                        RemoteCallExpr.of(
                            "binary", "match", List.of(Variable.of("K"), Variable.of("Prefix"))),
                        "=:=",
                        TupleExpr.of(
                            List.of(
                                IntegerExpr.of(0),
                                LocalCallExpr.of("byte_size", List.of(Variable.of("Prefix")))))))));

    ListComprehensionExpr sortedValues =
        ListComprehensionExpr.of(
            Variable.of("V"),
            List.of(
                ListComprehensionGenerator.of(
                    TuplePattern.of(List.of(WildcardPattern.of(), VariablePattern.of("V"))),
                    Variable.of("Sorted"))));

    return Function.of(
        "indexed_form_values",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Params"), VariablePattern.of("Prefix")),
                BlockExpr.commaSeparated(
                    List.of(
                        MatchExpr.bindValue("Entries", entries),
                        CaseExpr.of(
                            RemoteCallExpr.of("lists", "sort", List.of(Variable.of("Entries"))),
                            List.of(
                                Clause.of(ListPattern.of(List.of()), AtomExpr.of("undefined")),
                                Clause.of(VariablePattern.of("Sorted"), sortedValues)))),
                    false))));
  }

  private static Function formIndex() {
    return Function.of(
        "form_index",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Key"), VariablePattern.of("Prefix")),
                BlockExpr.commaSeparated(
                    List.of(
                        MatchExpr.bindValue(
                            "Rest",
                            RemoteCallExpr.of(
                                "binary",
                                "part",
                                List.of(
                                    Variable.of("Key"),
                                    LocalCallExpr.of("byte_size", List.of(Variable.of("Prefix"))),
                                    InfixExpr.of(
                                        LocalCallExpr.of("byte_size", List.of(Variable.of("Key"))),
                                        "-",
                                        LocalCallExpr.of(
                                            "byte_size", List.of(Variable.of("Prefix"))))))),
                        LocalCallExpr.of("binary_to_integer", List.of(Variable.of("Rest")))),
                    false))));
  }

  private static Function wrapAwsQueryResponse() {
    Expression responseMap =
        MapExpr.of(
            List.of(
                MapEntry.of(
                    Variable.of("ResponseName"),
                    MapExpr.of(
                        List.of(
                            MapEntry.of(
                                Variable.of("ResultName"), Variable.of("ResultContent")))))));

    return Function.of(
        "wrap_aws_query_response",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("ResultName"),
                    VariablePattern.of("ResultContent"),
                    VariablePattern.of("ResponseName"),
                    VariablePattern.of("XmlNs")),
                LocalCallExpr.of("encode_xml", List.of(responseMap, Variable.of("XmlNs"))))));
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
}
