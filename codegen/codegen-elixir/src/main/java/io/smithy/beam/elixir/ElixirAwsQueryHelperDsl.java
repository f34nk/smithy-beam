package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AndGuard;
import io.beam.dsl.elixir.AnonFun;
import io.beam.dsl.elixir.AnonFunClause;
import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.AtomPattern;
import io.beam.dsl.elixir.BlockExpr;
import io.beam.dsl.elixir.CaseExpr;
import io.beam.dsl.elixir.Clause;
import io.beam.dsl.elixir.ComparisonGuard;
import io.beam.dsl.elixir.ConsListPattern;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.IfExpr;
import io.beam.dsl.elixir.InfixExpr;
import io.beam.dsl.elixir.IntegerExpr;
import io.beam.dsl.elixir.IsTypeGuard;
import io.beam.dsl.elixir.ListExpr;
import io.beam.dsl.elixir.ListPattern;
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
import io.beam.dsl.elixir.StringExpr;
import io.beam.dsl.elixir.StringPattern;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.beam.dsl.elixir.WildcardPattern;
import io.smithy.beam.core.BeamXmlDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ElixirAwsQueryHelperDsl {
  private ElixirAwsQueryHelperDsl() {}

  private static final Pattern W = WildcardPattern.of();

  static List<Function> queryHelperFunctions(boolean ec2Query) {
    List<Function> functions = new ArrayList<>();
    functions.addAll(flattenMemberFunctions(ec2Query));
    functions.addAll(encFunctions());
    return functions;
  }

  static List<Function> xmlHelperFunctions(boolean ec2Query) {
    List<Function> functions = new ArrayList<>();
    functions.add(unwrapQueryResult());
    functions.addAll(normalizeXmlElement());
    functions.add(queryResultElement());
    functions.addAll(awsQueryXmlElementHelpers());
    functions.add(awsQueryXmlChildStructList());
    functions.add(awsQueryXmlChildList());
    functions.addAll(decodeXmlTextHelpers());
    functions.add(decodeQueryError(ec2Query));
    return functions;
  }

  private static List<Function> decodeXmlTextHelpers() {
    return List.of(
        defp("decode_xml_boolean", List.of(NilPattern.of()), NilExpr.of(), true),
        defp("decode_xml_boolean", List.of(VariablePattern.of("true")), AtomExpr.of("true"), true),
        defp(
            "decode_xml_boolean", List.of(VariablePattern.of("false")), AtomExpr.of("false"), true),
        defp(
            "decode_xml_boolean",
            List.of(VariablePattern.of("text")),
            IsTypeGuard.of("is_binary", "text"),
            CaseExpr.of(
                Variable.of("text"),
                List.of(
                    Clause.of(StringPattern.of("true"), AtomExpr.of("true")),
                    Clause.of(StringPattern.of("false"), AtomExpr.of("false")))),
            false),
        defp("decode_xml_integer", List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            "decode_xml_integer",
            List.of(VariablePattern.of("text")),
            IsTypeGuard.of("is_binary", "text"),
            RemoteCallExpr.of("String", "to_integer", List.of(Variable.of("text"))),
            true),
        defp("decode_xml_float", List.of(NilPattern.of()), NilExpr.of(), true),
        defp(
            "decode_xml_float",
            List.of(VariablePattern.of("text")),
            IsTypeGuard.of("is_binary", "text"),
            RemoteCallExpr.of("String", "to_float", List.of(Variable.of("text"))),
            true));
  }

  static List<Function> serverDecodeHelpers(boolean ec2Query) {
    List<Function> functions = new ArrayList<>();
    functions.add(parseQueryParams());
    functions.add(formValue());
    functions.add(ec2Query ? formListValuesEc2() : formListValuesAws());
    functions.add(indexedFormValues());
    return functions;
  }

  static List<Function> serverXmlEncodeHelpers(
      Optional<String> serviceNamespace, boolean ec2Query) {
    List<Function> functions = new ArrayList<>();
    if (!ec2Query) {
      functions.add(wrapAwsQueryResponse());
    }
    functions.addAll(ElixirXmlCodecDsl.xmlNamespace(serviceNamespace));
    functions.addAll(ElixirXmlCodecDsl.restXmlEncodeHelpers());
    return functions;
  }

  private static List<Function> awsQueryXmlElementHelpers() {
    List<Function> helpers = new ArrayList<>();
    helpers.addAll(ElixirXmlCodecDsl.collectText());
    helpers.addAll(ElixirXmlCodecDsl.elementText());
    helpers.addAll(ElixirXmlCodecDsl.isElementString());
    helpers.addAll(elementContent());
    helpers.add(awsQueryFindElement());
    helpers.addAll(isElement());
    helpers.addAll(elementName());
    helpers.add(xmlChildText());
    return helpers;
  }

  private static List<Function> flattenMemberFunctions(boolean ec2Query) {
    String listSuffix = ec2Query ? "." : ".member.";
    Expression listBody =
        RemoteCallExpr.of("List", "flatten", List.of(flattenMemberListFlatMap(listSuffix)));
    Expression mapBody = RemoteCallExpr.of("List", "flatten", List.of(flattenMemberMapFlatMap()));

    return List.of(
        defp(
            "flatten_member",
            List.of(VariablePattern.of("_key"), NilPattern.of()),
            ListExpr.of(List.of()),
            true),
        defp(
            "flatten_member",
            List.of(VariablePattern.of("key"), VariablePattern.of("value")),
            IsTypeGuard.of("is_list", "value"),
            listBody,
            false),
        defp(
            "flatten_member",
            List.of(VariablePattern.of("key"), VariablePattern.of("value")),
            IsTypeGuard.of("is_struct", "value"),
            LocalCallExpr.of(
                "flatten_structure", List.of(Variable.of("key"), Variable.of("value"))),
            false),
        defp(
            "flatten_member",
            List.of(VariablePattern.of("key"), VariablePattern.of("value")),
            IsTypeGuard.of("is_map", "value"),
            mapBody,
            false),
        defp(
            "flatten_member",
            List.of(VariablePattern.of("key"), VariablePattern.of("value")),
            ListExpr.of(List.of(TupleExpr.of(List.of(Variable.of("key"), Variable.of("value"))))),
            true));
  }

  private static Expression flattenMemberListFlatMap(String listSuffix) {
    return RemoteCallExpr.of(
        "Enum",
        "flat_map",
        List.of(
            RemoteCallExpr.of(
                "Enum", "with_index", List.of(Variable.of("value"), IntegerExpr.of(1))),
            AnonFun.of(
                List.of(
                    AnonFunClause.of(
                        List.of(
                            TuplePattern.of(
                                List.of(VariablePattern.of("v"), VariablePattern.of("i")))),
                        ComparisonGuard.of(Variable.of("v"), "!=", AtomExpr.of("nil")),
                        LocalCallExpr.of(
                            "flatten_member",
                            List.of(flattenMemberIndexedKey(listSuffix), Variable.of("v"))))))));
  }

  private static Expression flattenMemberMapFlatMap() {
    return RemoteCallExpr.of(
        "Enum",
        "flat_map",
        List.of(
            RemoteCallExpr.of(
                "Enum",
                "with_index",
                List.of(
                    RemoteCallExpr.of("Map", "to_list", List.of(Variable.of("value"))),
                    IntegerExpr.of(1))),
            AnonFun.of(
                List.of(
                    AnonFunClause.of(
                        List.of(
                            TuplePattern.of(
                                List.of(
                                    TuplePattern.of(
                                        List.of(VariablePattern.of("k"), VariablePattern.of("v"))),
                                    VariablePattern.of("i")))),
                        AndGuard.of(
                            List.of(
                                ComparisonGuard.of(Variable.of("k"), "!=", AtomExpr.of("nil")),
                                ComparisonGuard.of(Variable.of("v"), "!=", AtomExpr.of("nil")))),
                        InfixExpr.of(
                            LocalCallExpr.of(
                                "flatten_member",
                                List.of(flattenMemberEntryKey(".key"), Variable.of("k"))),
                            "++",
                            LocalCallExpr.of(
                                "flatten_member",
                                List.of(flattenMemberEntryKey(".value"), Variable.of("v")))))))));
  }

  private static Expression flattenMemberIndexedKey(String listSuffix) {
    return InfixExpr.of(
        InfixExpr.of(Variable.of("key"), "<>", StringExpr.of(listSuffix)),
        "<>",
        RemoteCallExpr.of("Integer", "to_string", List.of(Variable.of("i"))));
  }

  private static Expression flattenMemberEntryKey(String suffix) {
    return InfixExpr.of(
        InfixExpr.of(
            InfixExpr.of(Variable.of("key"), "<>", StringExpr.of(".entry.")),
            "<>",
            RemoteCallExpr.of("Integer", "to_string", List.of(Variable.of("i")))),
        "<>",
        StringExpr.of(suffix));
  }

  private static List<Function> encFunctions() {
    return List.of(
        defp(
            "enc",
            List.of(VariablePattern.of("value")),
            IsTypeGuard.of("is_boolean", "value"),
            RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("value"))),
            true),
        defp(
            "enc",
            List.of(VariablePattern.of("value")),
            IsTypeGuard.of("is_integer", "value"),
            RemoteCallExpr.of("Integer", "to_string", List.of(Variable.of("value"))),
            true),
        defp(
            "enc",
            List.of(VariablePattern.of("value")),
            IsTypeGuard.of("is_float", "value"),
            RemoteCallExpr.of("Float", "to_string", List.of(Variable.of("value"))),
            true),
        defp(
            "enc",
            List.of(VariablePattern.of("value")),
            IsTypeGuard.of("is_binary", "value"),
            Variable.of("value"),
            true),
        defp(
            "enc",
            List.of(VariablePattern.of("value")),
            IsTypeGuard.of("is_atom", "value"),
            RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("value"))),
            true));
  }

  private static Function parseQueryParams() {
    return defp(
        "parse_query_params",
        List.of(VariablePattern.of("body")),
        PipeExpr.of(
            Variable.of("body"),
            List.of(
                PipeStep.of(RemoteCallExpr.of("URI", "decode_query", List.of()), List.of()),
                PipeStep.of(RemoteCallExpr.of("Map", "new", List.of()), List.of()))),
        false);
  }

  private static Function formValue() {
    return defp(
        "form_value",
        List.of(VariablePattern.of("params"), VariablePattern.of("key")),
        RemoteCallExpr.of("Map", "get", List.of(Variable.of("params"), Variable.of("key"))),
        true);
  }

  private static Function formListValuesAws() {
    return defp(
        "form_list_values_aws",
        List.of(VariablePattern.of("params"), VariablePattern.of("key")),
        BlockExpr.of(
            List.of(
                MatchExpr.bind(
                    "prefix", InfixExpr.of(Variable.of("key"), "<>", StringExpr.of(".member."))),
                LocalCallExpr.of(
                    "indexed_form_values", List.of(Variable.of("params"), Variable.of("prefix"))))),
        false);
  }

  private static Function formListValuesEc2() {
    return defp(
        "form_list_values_ec2",
        List.of(VariablePattern.of("params"), VariablePattern.of("key")),
        BlockExpr.of(
            List.of(
                MatchExpr.bind(
                    "prefix", InfixExpr.of(Variable.of("key"), "<>", StringExpr.of("."))),
                LocalCallExpr.of(
                    "indexed_form_values", List.of(Variable.of("params"), Variable.of("prefix"))))),
        false);
  }

  private static Function indexedFormValues() {
    AnonFun startsWithFn =
        AnonFun.of(
            List.of(
                AnonFunClause.of(
                    List.of(TuplePattern.of(List.of(VariablePattern.of("k"), W))),
                    RemoteCallExpr.of(
                        "String",
                        "starts_with?",
                        List.of(Variable.of("k"), Variable.of("prefix"))))));

    AnonFun sortFn =
        AnonFun.of(
            List.of(
                AnonFunClause.of(
                    List.of(TuplePattern.of(List.of(VariablePattern.of("k"), W))),
                    RemoteCallExpr.of(
                        "String",
                        "to_integer",
                        List.of(
                            RemoteCallExpr.of(
                                "String",
                                "replace_prefix",
                                List.of(
                                    Variable.of("k"),
                                    Variable.of("prefix"),
                                    StringExpr.of(""))))))));

    AnonFun mapFn =
        AnonFun.of(
            List.of(
                AnonFunClause.of(
                    List.of(TuplePattern.of(List.of(W, VariablePattern.of("v")))),
                    Variable.of("v"))));

    return defp(
        "indexed_form_values",
        List.of(VariablePattern.of("params"), VariablePattern.of("prefix")),
        BlockExpr.of(
            List.of(
                MatchExpr.bind(
                    "values",
                    PipeExpr.of(
                        Variable.of("params"),
                        List.of(
                            PipeStep.of(
                                RemoteCallExpr.of("Enum", "filter", List.of(startsWithFn)),
                                List.of()),
                            PipeStep.of(
                                RemoteCallExpr.of("Enum", "sort_by", List.of(sortFn)), List.of()),
                            PipeStep.of(
                                RemoteCallExpr.of("Enum", "map", List.of(mapFn)), List.of())))),
                CaseExpr.of(
                    Variable.of("values"),
                    List.of(
                        Clause.of(ListPattern.of(List.of()), NilExpr.of()),
                        Clause.of(VariablePattern.of("values"), Variable.of("values")))))),
        false);
  }

  private static Function wrapAwsQueryResponse() {
    return defp(
        "wrap_aws_query_response",
        List.of(
            VariablePattern.of("result_name"),
            VariablePattern.of("result_content"),
            VariablePattern.of("response_name"),
            VariablePattern.of("xml_ns")),
        LocalCallExpr.of(
            "encode_xml",
            List.of(
                MapExpr.of(
                    List.of(
                        MapEntry.pair(
                            Variable.of("response_name"),
                            MapExpr.of(
                                List.of(
                                    MapEntry.pair(
                                        Variable.of("result_name"),
                                        Variable.of("result_content"))))))),
                Variable.of("xml_ns"))),
        true);
  }

  private static Function unwrapQueryResult() {
    Expression resultLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "query_result_element", List.of(Variable.of("root"), Variable.of("result_name"))),
            List.of(
                Clause.of(NilPattern.of(), errorMissingResult()),
                Clause.of(
                    VariablePattern.of("result"),
                    TupleExpr.of(List.of(AtomExpr.of("ok"), Variable.of("result"))))));

    Expression scanCase =
        CaseExpr.of(
            RemoteCallExpr.of(
                ":xmerl_scan",
                "string",
                List.of(
                    RemoteCallExpr.of(":erlang", "binary_to_list", List.of(Variable.of("body"))))),
            List.of(
                Clause.of(
                    TuplePattern.of(List.of(VariablePattern.of("xml"), W)),
                    BlockExpr.of(
                        List.of(
                            MatchExpr.bind(
                                "root",
                                LocalCallExpr.of(
                                    "normalize_xml_element", List.of(Variable.of("xml")))),
                            resultLookup))),
                Clause.of(
                    W,
                    TupleExpr.of(List.of(AtomExpr.of("error"), AtomExpr.of("xml_parse_error"))))));

    return defp(
        "unwrap_query_result",
        List.of(VariablePattern.of("body"), VariablePattern.of("result_name")),
        scanCase,
        false);
  }

  private static TupleExpr errorMissingResult() {
    return TupleExpr.of(
        List.of(
            AtomExpr.of("error"),
            TupleExpr.of(List.of(AtomExpr.of("missing_result"), Variable.of("result_name")))));
  }

  private static List<Function> normalizeXmlElement() {
    return List.of(
        defp(
            "normalize_xml_element",
            List.of(ConsListPattern.of(VariablePattern.of("h"), W)),
            LocalCallExpr.of("normalize_xml_element", List.of(Variable.of("h"))),
            false),
        defp(
            "normalize_xml_element",
            List.of(VariablePattern.of("element")),
            Variable.of("element"),
            true));
  }

  private static Function queryResultElement() {
    return defp(
        "query_result_element",
        List.of(VariablePattern.of("element"), VariablePattern.of("result_name")),
        IfExpr.of(
            InfixExpr.of(
                LocalCallExpr.of("is_element", List.of(Variable.of("element"))),
                "and",
                InfixExpr.of(
                    LocalCallExpr.of("element_name", List.of(Variable.of("element"))),
                    "==",
                    Variable.of("result_name"))),
            Variable.of("element"),
            LocalCallExpr.of(
                "find_element",
                List.of(
                    Variable.of("result_name"),
                    LocalCallExpr.of("element_content", List.of(Variable.of("element"))))),
            false),
        false);
  }

  private static Function awsQueryFindElement() {
    AnonFun findFn =
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
                            Variable.of("name"))))));

    return defp(
        "find_element",
        List.of(VariablePattern.of("name"), VariablePattern.of("content")),
        RemoteCallExpr.of("Enum", "find", List.of(Variable.of("content"), findFn)),
        false);
  }

  private static Function decodeQueryError(boolean ec2Query) {
    if (ec2Query) {
      return decodeEc2QueryError();
    }
    return decodeAwsQueryError();
  }

  private static Function decodeAwsQueryError() {
    return defp(
        "decode_query_error",
        List.of(VariablePattern.of("status"), VariablePattern.of("body")),
        decodeQueryErrorBody(
            BeamXmlDecoder.ERROR_RESPONSE_ELEMENT,
            BeamXmlDecoder.ERROR_ELEMENT,
            BeamXmlDecoder.ERROR_CODE_ELEMENT,
            BeamXmlDecoder.ERROR_MESSAGE_ELEMENT),
        false);
  }

  private static Function decodeEc2QueryError() {
    return defp(
        "decode_query_error",
        List.of(VariablePattern.of("status"), VariablePattern.of("body")),
        decodeQueryErrorBodyEc2(),
        false);
  }

  private static Expression decodeQueryErrorBody(
      String responseElement, String errorElement, String codeElement, String messageElement) {
    Expression errorLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "find_element",
                List.of(
                    StringExpr.of(errorElement),
                    LocalCallExpr.of("element_content", List.of(Variable.of("error_response"))))),
            List.of(
                Clause.of(W, unknownQueryError()),
                Clause.of(
                    VariablePattern.of("error"),
                    TupleExpr.of(
                        List.of(
                            AtomExpr.of("error"),
                            TupleExpr.of(
                                List.of(
                                    LocalCallExpr.of(
                                        "xml_child_text",
                                        List.of(Variable.of("error"), StringExpr.of(codeElement))),
                                    LocalCallExpr.of(
                                        "xml_child_text",
                                        List.of(
                                            Variable.of("error"),
                                            StringExpr.of(messageElement))))))))));

    Expression responseLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "query_result_element",
                List.of(Variable.of("xml"), StringExpr.of(responseElement))),
            List.of(
                Clause.of(W, unknownQueryError()),
                Clause.of(VariablePattern.of("error_response"), errorLookup)));

    return scanAndLookup(responseLookup);
  }

  private static Expression decodeQueryErrorBodyEc2() {
    Expression errorLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "find_element",
                List.of(
                    StringExpr.of(BeamXmlDecoder.ERROR_ELEMENT),
                    LocalCallExpr.of("element_content", List.of(Variable.of("error"))))),
            List.of(
                Clause.of(W, unknownQueryError()),
                Clause.of(
                    VariablePattern.of("error"),
                    TupleExpr.of(
                        List.of(
                            AtomExpr.of("error"),
                            TupleExpr.of(
                                List.of(
                                    LocalCallExpr.of(
                                        "xml_child_text",
                                        List.of(
                                            Variable.of("error"),
                                            StringExpr.of(BeamXmlDecoder.ERROR_CODE_ELEMENT))),
                                    LocalCallExpr.of(
                                        "xml_child_text",
                                        List.of(
                                            Variable.of("error"),
                                            StringExpr.of(
                                                BeamXmlDecoder.ERROR_MESSAGE_ELEMENT))))))))));

    Expression errorsLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "find_element",
                List.of(
                    StringExpr.of(BeamXmlDecoder.EC2_ERRORS_ELEMENT),
                    LocalCallExpr.of("element_content", List.of(Variable.of("response"))))),
            List.of(
                Clause.of(W, unknownQueryError()),
                Clause.of(
                    VariablePattern.of("errors"),
                    CaseExpr.of(
                        LocalCallExpr.of(
                            "find_element",
                            List.of(
                                StringExpr.of(BeamXmlDecoder.ERROR_ELEMENT),
                                LocalCallExpr.of(
                                    "element_content", List.of(Variable.of("errors"))))),
                        List.of(
                            Clause.of(W, unknownQueryError()),
                            Clause.of(VariablePattern.of("error"), errorLookup))))));

    Expression responseLookup =
        CaseExpr.of(
            LocalCallExpr.of(
                "query_result_element",
                List.of(Variable.of("root"), StringExpr.of(BeamXmlDecoder.EC2_RESPONSE_ELEMENT))),
            List.of(
                Clause.of(W, unknownQueryError()),
                Clause.of(VariablePattern.of("response"), errorsLookup)));

    return scanAndLookup(responseLookup);
  }

  private static Expression scanAndLookup(Expression lookup) {
    return CaseExpr.of(
        RemoteCallExpr.of(
            ":xmerl_scan",
            "string",
            List.of(RemoteCallExpr.of(":erlang", "binary_to_list", List.of(Variable.of("body"))))),
        List.of(
            Clause.of(
                TuplePattern.of(List.of(VariablePattern.of("xml"), W)),
                BlockExpr.of(
                    List.of(
                        MatchExpr.bind(
                            "root",
                            LocalCallExpr.of("normalize_xml_element", List.of(Variable.of("xml")))),
                        lookup))),
            Clause.of(W, unknownQueryError())));
  }

  private static TupleExpr unknownQueryError() {
    return TupleExpr.of(
        List.of(
            AtomExpr.of("error"),
            TupleExpr.of(
                List.of(
                    AtomExpr.of("unknown_error"), Variable.of("status"), Variable.of("body")))));
  }

  private static List<Function> elementContent() {
    TuplePattern xmlElementContent = xmlElementContentPattern("content");
    TuplePattern sixTupleContent =
        TuplePattern.of(List.of(W, W, VariablePattern.of("content"), W, W, W));
    return List.of(
        defp("element_content", List.of(xmlElementContent), Variable.of("content"), true),
        defp(
            "element_content",
            List.of(sixTupleContent),
            IsTypeGuard.of("is_list", "content"),
            Variable.of("content"),
            true),
        defp(
            "element_content",
            List.of(ConsListPattern.of(VariablePattern.of("h"), W)),
            LocalCallExpr.of("element_content", List.of(Variable.of("h"))),
            false),
        defp("element_content", List.of(W), ListExpr.of(List.of()), true));
  }

  private static List<Function> isElement() {
    TuplePattern xmlElement = xmlElementWildPattern();
    TuplePattern sixTuple = TuplePattern.of(List.of(W, W, VariablePattern.of("content"), W, W, W));
    return List.of(
        defp("is_element", List.of(xmlElement), AtomExpr.of("true"), true),
        defp(
            "is_element",
            List.of(sixTuple),
            IsTypeGuard.of("is_list", "content"),
            AtomExpr.of("true"),
            true),
        defp("is_element", List.of(W), AtomExpr.of("false"), true));
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
    return List.of(
        defp(
            "element_name",
            List.of(xmlElementName),
            IsTypeGuard.of("is_atom", "name"),
            RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("name"))),
            true),
        defp(
            "element_name",
            List.of(xmlElementName),
            IsTypeGuard.of("is_list", "name"),
            RemoteCallExpr.of("List", "to_string", List.of(Variable.of("name"))),
            true),
        defp(
            "element_name",
            List.of(xmlElementName),
            IsTypeGuard.of("is_binary", "name"),
            Variable.of("name"),
            true),
        defp(
            "element_name",
            List.of(sixTupleName),
            IsTypeGuard.of("is_atom", "name"),
            RemoteCallExpr.of("Atom", "to_string", List.of(Variable.of("name"))),
            true),
        defp(
            "element_name",
            List.of(sixTupleName),
            IsTypeGuard.of("is_list", "name"),
            RemoteCallExpr.of("List", "to_string", List.of(Variable.of("name"))),
            true),
        defp(
            "element_name",
            List.of(sixTupleName),
            IsTypeGuard.of("is_binary", "name"),
            Variable.of("name"),
            true));
  }

  private static Function xmlChildText() {
    return defp(
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
        false);
  }

  private static Function awsQueryXmlChildStructList() {
    AnonFun filterFn =
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
                            Variable.of("item_name"))))));

    Expression decodePipeline =
        PipeExpr.of(
            LocalCallExpr.of("element_content", List.of(Variable.of("list_element"))),
            List.of(
                PipeStep.of(RemoteCallExpr.of("Enum", "filter", List.of(filterFn)), List.of()),
                PipeStep.of(
                    RemoteCallExpr.of("Enum", "map", List.of(Variable.of("decode_fun"))),
                    List.of())));

    return defp(
        "xml_child_struct_list",
        List.of(
            VariablePattern.of("parent"),
            VariablePattern.of("list_name"),
            VariablePattern.of("item_name"),
            VariablePattern.of("decode_fun")),
        CaseExpr.of(
            LocalCallExpr.of(
                "find_element",
                List.of(
                    Variable.of("list_name"),
                    LocalCallExpr.of("element_content", List.of(Variable.of("parent"))))),
            List.of(
                Clause.of(NilPattern.of(), NilExpr.of()),
                Clause.of(VariablePattern.of("list_element"), decodePipeline))),
        false);
  }

  private static Function awsQueryXmlChildList() {
    AnonFun mapFn =
        AnonFun.of(
            List.of(
                AnonFunClause.of(
                    List.of(VariablePattern.of("item")),
                    CaseExpr.of(
                        LocalCallExpr.of("element_text", List.of(Variable.of("item"))),
                        List.of(
                            Clause.of(ListPattern.of(List.of()), NilExpr.of()),
                            Clause.of(
                                ConsListPattern.of(VariablePattern.of("text"), W),
                                RemoteCallExpr.of(
                                    "List", "to_string", List.of(Variable.of("text")))))))));

    AnonFun rejectFn =
        AnonFun.of(
            List.of(
                AnonFunClause.of(
                    List.of(VariablePattern.of("x")),
                    RemoteCallExpr.of("Kernel", "is_nil", List.of(Variable.of("x"))))));

    Expression childPipeline =
        PipeExpr.of(
            LocalCallExpr.of("element_content", List.of(Variable.of("list_element"))),
            List.of(
                PipeStep.of(
                    RemoteCallExpr.of("Enum", "filter", List.of(filterFnForChildList())),
                    List.of()),
                PipeStep.of(RemoteCallExpr.of("Enum", "map", List.of(mapFn)), List.of()),
                PipeStep.of(RemoteCallExpr.of("Enum", "reject", List.of(rejectFn)), List.of())));

    return defp(
        "xml_child_list",
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
                Clause.of(VariablePattern.of("list_element"), childPipeline))),
        false);
  }

  private static AnonFun filterFnForChildList() {
    return AnonFun.of(
        List.of(
            AnonFunClause.of(
                List.of(VariablePattern.of("item")),
                InfixExpr.of(
                    LocalCallExpr.of("is_element", List.of(Variable.of("item"))),
                    "and",
                    InfixExpr.of(
                        LocalCallExpr.of("element_name", List.of(Variable.of("item"))),
                        "==",
                        Variable.of("item_name"))))));
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
