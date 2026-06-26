package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamXmlDecoder;
import io.smithy.beam.ir.erlang.ErlApply;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryExpr;
import io.smithy.beam.ir.erlang.ErlBinaryTemplate;
import io.smithy.beam.ir.erlang.ErlBinaryText;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlCatchClause;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlComprehensionFilter;
import io.smithy.beam.ir.erlang.ErlComprehensionGenerator;
import io.smithy.beam.ir.erlang.ErlComprehensionQual;
import io.smithy.beam.ir.erlang.ErlConsPattern;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlInteger;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlListComprehension;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlNilPattern;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlTry;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;

import java.util.ArrayList;
import java.util.List;

final class ErlangAwsQueryHelperIr {
    private ErlangAwsQueryHelperIr() {}

    private static final ErlVarPattern W = ErlVarPattern.varPattern("_");

    static List<ErlFunction> queryHelperFunctions(boolean ec2Query) {
        return List.of(flattenMember(ec2Query), flattenStructure(), enc());
    }

    static List<ErlFunction> xmlHelperFunctions(boolean ec2Query) {
        List<ErlFunction> functions = new ArrayList<>();
        functions.add(unwrapQueryResult());
        functions.add(normalizeXmlElement());
        functions.add(queryResultElement());
        functions.addAll(awsQueryXmlElementHelpers());
        functions.add(awsQueryXmlChildStructList());
        functions.add(awsQueryXmlChildList());
        functions.add(decodeQueryError(ec2Query));
        return functions;
    }

    private static List<ErlFunction> awsQueryXmlElementHelpers() {
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

    private static ErlFunction awsQueryFindElement() {
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

    private static ErlFunction awsQueryElementName() {
        return ErlFunction.function(
                "element_name",
                1,
                List.of(
                        ErlClause.blockClause(
                                List.of(xmlElementNamePattern()),
                                List.of(ErlGuard.guard("is_atom", ErlVar.var("Name"))),
                                ErlCallLocal.callLocal(
                                        "list_to_binary",
                                        ErlCallLocal.callLocal("atom_to_list", ErlVar.var("Name")))),
                        ErlClause.blockClause(
                                List.of(xmlElementNamePattern()),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("Name"))),
                                ErlCallLocal.callLocal("list_to_binary", ErlVar.var("Name"))),
                        ErlClause.clause(
                                List.of(xmlElementNamePattern()),
                                List.of(ErlGuard.guard("is_binary", ErlVar.var("Name"))),
                                ErlVar.var("Name")),
                        ErlClause.clause(
                                List.of(sixTupleNamePattern()),
                                List.of(ErlGuard.guard("is_atom", ErlVar.var("Name"))),
                                ErlCallLocal.callLocal(
                                        "list_to_binary",
                                        ErlCallLocal.callLocal("atom_to_list", ErlVar.var("Name")))),
                        ErlClause.clause(
                                List.of(sixTupleNamePattern()),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("Name"))),
                                ErlCallLocal.callLocal("list_to_binary", ErlVar.var("Name"))),
                        ErlClause.clause(
                                List.of(sixTupleNamePattern()),
                                List.of(ErlGuard.guard("is_binary", ErlVar.var("Name"))),
                                ErlVar.var("Name"))));
    }

    private static ErlFunction awsQueryElementText() {
        return ErlFunction.function(
                "element_text",
                1,
                List.of(
                        ErlClause.blockClause(
                                List.of(xmlElementContentPattern()),
                                ErlCallLocal.callLocal("xml_text_values", ErlVar.var("Content"))),
                        ErlClause.blockClause(
                                List.of(sixTupleContentPattern()),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("Content"))),
                                ErlListComprehension.comprehensionWithFilters(
                                        ErlVar.var("T"),
                                        ErlVarPattern.varPattern("T"),
                                        ErlVar.var("Content"),
                                        List.of(
                                                ErlGuard.guard("is_list", ErlVar.var("T")),
                                                ErlGuard.exprGuard(ErlOp.prefix(
                                                        "not",
                                                        ErlCallLocal.callLocal(
                                                                "is_element_string", ErlVar.var("T"))))))),
                        ErlClause.clause(List.of(W), ErlList.list())));
    }

    private static ErlFunction awsQueryXmlTextValues() {
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

    private static ErlTuplePattern xmlTextPattern() {
        return ErlTuplePattern.tuplePattern(
                ErlAtomPattern.atomPattern("xmlText"), W, W, W, ErlVarPattern.varPattern("V"), W);
    }

    private static ErlFunction awsQueryXmlChildStructList() {
        ErlApply decodeItem = ErlApply.apply(ErlVar.var("DecodeFun"), ErlVar.var("Item"));

        return ErlFunction.function(
                "xml_child_struct_list",
                4,
                List.of(ErlClause.clause(
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

    private static ErlFunction awsQueryXmlChildList() {
        List<ErlComprehensionQual> itemQualifiers = List.of(
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
                        ErlOp.op("=/=", ErlVar.var("ItemText"), ErlBinary.binary(""))));

        return ErlFunction.function(
                "xml_child_list",
                3,
                List.of(ErlClause.clause(
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
                                        ErlListComprehension.comprehensionQualifiers(
                                                ErlVar.var("ItemText"), itemQualifiers))))));
    }

    private static ErlTuplePattern xmlElementNamePattern() {
        return ErlTuplePattern.tuplePattern(
                ErlAtomPattern.atomPattern("xmlElement"),
                ErlVarPattern.varPattern("Name"),
                W, W, W, W, W, W, W, W, W, W);
           
    }

    private static ErlTuplePattern sixTupleNamePattern() {
        return ErlTuplePattern.tuplePattern(
                ErlVarPattern.varPattern("Name"), W, W, W, W, W);
    }

    private static ErlTuplePattern xmlElementContentPattern() {
        return ErlTuplePattern.tuplePattern(
                ErlAtomPattern.atomPattern("xmlElement"),
                W, W, W, W, W, W, W,
                ErlVarPattern.varPattern("Content"),
                W, W, W);
    }

    private static ErlTuplePattern sixTupleContentPattern() {
        return ErlTuplePattern.tuplePattern(W, W, ErlVarPattern.varPattern("Content"), W, W, W);
    }

    static List<ErlFunction> serverQueryDecodeHelperFunctions(boolean ec2Query) {
        return List.of(
                parseQueryParams(),
                formValue(),
                ec2Query ? formListValuesEc2() : formListValuesAws(),
                indexedFormValues(),
                formIndex());
    }

    static List<ErlFunction> serverXmlEncodeHelperFunctions(boolean ec2Query) {
        List<ErlFunction> functions = new ArrayList<>();
        if (!ec2Query) {
            functions.add(wrapAwsQueryResponse());
        }
        functions.addAll(ErlangXmlCodecIr.awsQueryServerEncodeHelpers());
        return functions;
    }

    private static ErlFunction flattenMember(boolean ec2Query) {
        String listSuffix = ec2Query ? "." : ".member.";
        ErlExpr listBody = ErlCall.call("lists", "append", flattenMemberListComprehension(listSuffix));

        ErlExpr mapBody = ErlCall.call("lists", "append", flattenMemberMapComprehension());

        return ErlFunction.function(
                "flatten_member",
                2,
                List.of(
                        ErlClause.blockClause(
                                List.of(ErlVarPattern.varPattern("_Key"), ErlAtomPattern.atomPattern("undefined")),
                                ErlList.list()),
                        ErlClause.blockClause(
                                List.of(ErlVarPattern.varPattern("Key"), ErlVarPattern.varPattern("Value")),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("Value"))),
                                listBody),
                        ErlClause.blockClause(
                                List.of(ErlVarPattern.varPattern("Key"), ErlVarPattern.varPattern("Value")),
                                List.of(ErlGuard.guard("is_map", ErlVar.var("Value"))),
                                mapBody),
                        ErlClause.blockClause(
                                List.of(ErlVarPattern.varPattern("Key"), ErlVarPattern.varPattern("Value")),
                                List.of(ErlGuard.guard("is_tuple", ErlVar.var("Value"))),
                                ErlCallLocal.callLocal("flatten_structure", ErlVar.var("Key"), ErlVar.var("Value"))),
                        ErlClause.blockClause(
                                List.of(ErlVarPattern.varPattern("Key"), ErlVarPattern.varPattern("Value")),
                                ErlList.list(ErlTuple.tuple(ErlVar.var("Key"), ErlVar.var("Value"))))));
    }

    private static ErlListComprehension flattenMemberListComprehension(String listSuffix) {
        return ErlListComprehension.comprehensionQualifiers(
                ErlCallLocal.callLocal(
                        "flatten_member",
                        flattenMemberIndexedKey(listSuffix),
                        ErlVar.var("V")),
                List.of(
                        new ErlComprehensionGenerator(
                                ErlTuplePattern.tuplePattern(
                                        ErlVarPattern.varPattern("I"), ErlVarPattern.varPattern("V")),
                                ErlCall.call("lists", "enumerate", ErlVar.var("Value"))),
                        new ErlComprehensionFilter(
                                ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined")))));
    }

    private static ErlListComprehension flattenMemberMapComprehension() {
        return ErlListComprehension.comprehensionQualifiers(
                ErlOp.op(
                        "++",
                        ErlCallLocal.callLocal(
                                "flatten_member",
                                flattenMemberEntryKey(".key"),
                                ErlVar.var("K")),
                        ErlCallLocal.callLocal(
                                "flatten_member",
                                flattenMemberEntryKey(".value"),
                                ErlVar.var("V"))),
                List.of(
                        new ErlComprehensionGenerator(
                                ErlTuplePattern.tuplePattern(
                                        ErlVarPattern.varPattern("I"),
                                        ErlTuplePattern.tuplePattern(
                                                ErlVarPattern.varPattern("K"), ErlVarPattern.varPattern("V"))),
                                ErlCall.call("lists", "enumerate", ErlCall.call("maps", "to_list", ErlVar.var("Value")))),
                        new ErlComprehensionFilter(
                                ErlOp.op("=/=", ErlVar.var("K"), ErlAtom.atom("undefined"))),
                        new ErlComprehensionFilter(
                                ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined")))));
    }

    private static ErlBinaryTemplate flattenMemberIndexedKey(String listSuffix) {
        return ErlBinaryTemplate.binaryTemplate(
                ErlBinaryExpr.expr(ErlVar.var("Key"), true),
                ErlBinaryText.text(listSuffix),
                ErlBinaryExpr.expr(ErlCallLocal.callLocal("integer_to_binary", ErlVar.var("I")), "binary"));
    }

    private static ErlBinaryTemplate flattenMemberEntryKey(String suffix) {
        return ErlBinaryTemplate.binaryTemplate(
                ErlBinaryExpr.expr(ErlVar.var("Key"), true),
                ErlBinaryText.text(".entry."),
                ErlBinaryExpr.expr(ErlCallLocal.callLocal("integer_to_binary", ErlVar.var("I")), "binary"),
                ErlBinaryText.text(suffix));
    }

    private static ErlFunction flattenStructure() {
        return ErlFunction.function(
                "flatten_structure",
                2,
                List.of(
                        ErlClause.blockClause(
                                List.of(ErlVarPattern.varPattern("_Key"), ErlAtomPattern.atomPattern("undefined")),
                                ErlList.list()),
                        ErlClause.blockClause(
                                List.of(ErlVarPattern.varPattern("_Key"), ErlVarPattern.varPattern("_Value")),
                                ErlList.list())));
    }

    private static ErlFunction enc() {
        return ErlFunction.function(
                "enc",
                1,
                List.of(
                        ErlClause.blockClause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_boolean", ErlVar.var("V"))),
                                ErlCallLocal.callLocal("atom_to_binary", ErlVar.var("V"), ErlAtom.atom("utf8"))),
                        ErlClause.blockClause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_integer", ErlVar.var("V"))),
                                ErlCallLocal.callLocal("integer_to_binary", ErlVar.var("V"))),
                        ErlClause.blockClause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_float", ErlVar.var("V"))),
                                ErlCallLocal.callLocal("float_to_binary", ErlVar.var("V"))),
                        ErlClause.blockClause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_binary", ErlVar.var("V"))),
                                ErlVar.var("V")),
                        ErlClause.blockClause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_atom", ErlVar.var("V"))),
                                ErlCallLocal.callLocal("atom_to_binary", ErlVar.var("V"), ErlAtom.atom("utf8")))));
    }

    private static ErlFunction unwrapQueryResult() {
        ErlCase resultLookup = ErlCase.caseExpr(
                ErlCallLocal.callLocal(
                        "query_result_element", ErlVar.var("Root"), ErlVar.var("ResultName")),
                ErlClause.clause(
                        List.of(ErlAtomPattern.atomPattern("undefined")),
                        ErlTuple.tuple(
                                ErlAtom.atom("error"),
                                ErlTuple.tuple(ErlAtom.atom("missing_result"), ErlVar.var("ResultName")))),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Result")),
                        ErlTuple.tuple(ErlAtom.atom("ok"), ErlVar.var("Result"))));

        ErlTry body = ErlTry.tryExpr(
                List.of(
                        ErlMatch.match(
                                ErlTuplePattern.tuplePattern(
                                        ErlVarPattern.varPattern("Xml"), ErlVarPattern.varPattern("_")),
                                ErlCall.call(
                                        "xmerl_scan",
                                        "string",
                                        ErlCallLocal.callLocal("binary_to_list", ErlVar.var("Body")))),
                        ErlMatch.match(
                                ErlVarPattern.varPattern("Root"),
                                ErlCallLocal.callLocal("normalize_xml_element", ErlVar.var("Xml"))),
                        resultLookup),
                List.of(ErlCatchClause.catchClause(
                        W,
                        ErlVarPattern.varPattern("Reason"),
                        ErlTuple.tuple(
                                ErlAtom.atom("error"),
                                ErlTuple.tuple(ErlAtom.atom("xml_parse_error"), ErlVar.var("Reason"))))));

        return ErlFunction.function(
                "unwrap_query_result",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Body"), ErlVarPattern.varPattern("ResultName")),
                        body)));
    }

    private static ErlFunction normalizeXmlElement() {
        return ErlFunction.function(
                "normalize_xml_element",
                1,
                List.of(
                        ErlClause.blockClause(
                                List.of(ErlConsPattern.consPattern(ErlVarPattern.varPattern("H"), W)),
                                ErlCallLocal.callLocal("normalize_xml_element", ErlVar.var("H"))),
                        ErlClause.blockClause(
                                List.of(ErlVarPattern.varPattern("Element")),
                                ErlVar.var("Element"))));
    }

    private static ErlFunction queryResultElement() {
        return ErlFunction.function(
                "query_result_element",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Element"), ErlVarPattern.varPattern("ResultName")),
                        ErlCase.caseExpr(
                                ErlOp.op(
                                        "andalso",
                                        ErlCallLocal.callLocal("is_element", ErlVar.var("Element")),
                                        ErlOp.op(
                                                "=:=",
                                                ErlCallLocal.callLocal("element_name", ErlVar.var("Element")),
                                                ErlVar.var("ResultName"))),
                                ErlClause.clause(
                                        List.of(ErlAtomPattern.atomPattern("true")),
                                        ErlVar.var("Element")),
                                ErlClause.clause(
                                        List.of(ErlAtomPattern.atomPattern("false")),
                                        ErlCallLocal.callLocal(
                                                "find_element",
                                                ErlVar.var("ResultName"),
                                                ErlCallLocal.callLocal("element_content", ErlVar.var("Element"))))))));
    }

    private static ErlFunction decodeQueryError(boolean ec2Query) {
        if (ec2Query) {
            return decodeEc2QueryError();
        }
        return decodeAwsQueryError();
    }

    private static ErlFunction decodeAwsQueryError() {
        return ErlFunction.function(
                "decode_query_error",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Status"), ErlVarPattern.varPattern("Body")),
                        decodeQueryErrorBody(
                                BeamXmlDecoder.ERROR_RESPONSE_ELEMENT,
                                BeamXmlDecoder.ERROR_ELEMENT,
                                BeamXmlDecoder.ERROR_CODE_ELEMENT,
                                BeamXmlDecoder.ERROR_MESSAGE_ELEMENT))));
    }

    private static ErlFunction decodeEc2QueryError() {
        return ErlFunction.function(
                "decode_query_error",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Status"), ErlVarPattern.varPattern("Body")),
                        decodeQueryErrorBodyEc2())));
    }

    private static ErlTry decodeQueryErrorBody(
            String responseElement, String errorElement, String codeElement, String messageElement) {
        ErlCase errorLookup = ErlCase.caseExpr(
                ErlCallLocal.callLocal(
                        "find_element",
                        ErlBinary.binary(errorElement),
                        ErlCallLocal.callLocal("element_content", ErlVar.var("ErrorResponse"))),
                ErlClause.clause(
                        List.of(ErlAtomPattern.atomPattern("undefined")),
                        unknownQueryError()),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Error")),
                        ErlTuple.tuple(
                                ErlAtom.atom("error"),
                                ErlTuple.tuple(
                                        ErlCallLocal.callLocal(
                                                "xml_child_text", ErlVar.var("Error"), ErlBinary.binary(codeElement)),
                                        ErlCallLocal.callLocal(
                                                "xml_child_text",
                                                ErlVar.var("Error"),
                                                ErlBinary.binary(messageElement))))));

        ErlCase responseLookup = ErlCase.caseExpr(
                ErlCallLocal.callLocal(
                        "query_result_element",
                        ErlVar.var("Root"),
                        ErlBinary.binary(responseElement)),
                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), unknownQueryError()),
                ErlClause.clause(List.of(ErlVarPattern.varPattern("ErrorResponse")), errorLookup));

        return ErlTry.tryExpr(
                List.of(
                        ErlMatch.match(
                                ErlTuplePattern.tuplePattern(
                                        ErlVarPattern.varPattern("Xml"), ErlVarPattern.varPattern("_")),
                                ErlCall.call(
                                        "xmerl_scan",
                                        "string",
                                        ErlCallLocal.callLocal("binary_to_list", ErlVar.var("Body")))),
                        ErlMatch.match(
                                ErlVarPattern.varPattern("Root"),
                                ErlCallLocal.callLocal("normalize_xml_element", ErlVar.var("Xml"))),
                        responseLookup),
                List.of(ErlCatchClause.catchClause(W, W, unknownQueryError())));
    }

    private static ErlTry decodeQueryErrorBodyEc2() {
        ErlCase errorLookup = ErlCase.caseExpr(
                ErlCallLocal.callLocal(
                        "find_element",
                        ErlBinary.binary(BeamXmlDecoder.ERROR_ELEMENT),
                        ErlCallLocal.callLocal("element_content", ErlVar.var("Error"))),
                ErlClause.clause(
                        List.of(ErlAtomPattern.atomPattern("undefined")),
                        unknownQueryError()),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Error")),
                        ErlTuple.tuple(
                                ErlAtom.atom("error"),
                                ErlTuple.tuple(
                                        ErlCallLocal.callLocal(
                                                "xml_child_text",
                                                ErlVar.var("Error"),
                                                ErlBinary.binary(BeamXmlDecoder.ERROR_CODE_ELEMENT)),
                                        ErlCallLocal.callLocal(
                                                "xml_child_text",
                                                ErlVar.var("Error"),
                                                ErlBinary.binary(BeamXmlDecoder.ERROR_MESSAGE_ELEMENT))))));

        ErlCase errorsLookup = ErlCase.caseExpr(
                ErlCallLocal.callLocal(
                        "find_element",
                        ErlBinary.binary(BeamXmlDecoder.EC2_ERRORS_ELEMENT),
                        ErlCallLocal.callLocal("element_content", ErlVar.var("Response"))),
                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), unknownQueryError()),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Errors")),
                        ErlCase.caseExpr(
                                ErlCallLocal.callLocal(
                                        "find_element",
                                        ErlBinary.binary(BeamXmlDecoder.ERROR_ELEMENT),
                                        ErlCallLocal.callLocal("element_content", ErlVar.var("Errors"))),
                                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), unknownQueryError()),
                                ErlClause.clause(List.of(ErlVarPattern.varPattern("Error")), errorLookup))));

        ErlCase responseLookup = ErlCase.caseExpr(
                ErlCallLocal.callLocal(
                        "query_result_element",
                        ErlVar.var("Root"),
                        ErlBinary.binary(BeamXmlDecoder.EC2_RESPONSE_ELEMENT)),
                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), unknownQueryError()),
                ErlClause.clause(List.of(ErlVarPattern.varPattern("Response")), errorsLookup));

        return ErlTry.tryExpr(
                List.of(
                        ErlMatch.match(
                                ErlTuplePattern.tuplePattern(
                                        ErlVarPattern.varPattern("Xml"), ErlVarPattern.varPattern("_")),
                                ErlCall.call(
                                        "xmerl_scan",
                                        "string",
                                        ErlCallLocal.callLocal("binary_to_list", ErlVar.var("Body")))),
                        ErlMatch.match(
                                ErlVarPattern.varPattern("Root"),
                                ErlCallLocal.callLocal("normalize_xml_element", ErlVar.var("Xml"))),
                        responseLookup),
                List.of(ErlCatchClause.catchClause(W, W, unknownQueryError())));
    }

    private static ErlTuple unknownQueryError() {
        return ErlTuple.tuple(
                ErlAtom.atom("error"),
                ErlTuple.tuple(
                        ErlAtom.atom("unknown_error"),
                        ErlVar.var("Status"),
                        ErlVar.var("Body")));
    }

    private static ErlFunction parseQueryParams() {
        return ErlFunction.function(
                "parse_query_params",
                1,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("Body")),
                        ErlCall.call(
                                "maps",
                                "from_list",
                                ErlCall.call("uri_string", "dissect_query", ErlVar.var("Body"))))));
    }

    private static ErlFunction formValue() {
        return ErlFunction.function(
                "form_value",
                2,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("Params"), ErlVarPattern.varPattern("Key")),
                        ErlCall.call(
                                "maps",
                                "get",
                                ErlVar.var("Key"),
                                ErlVar.var("Params"),
                                ErlAtom.atom("undefined")))));
    }

    private static ErlFunction formListValuesAws() {
        return ErlFunction.function(
                "form_list_values_aws",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Params"), ErlVarPattern.varPattern("Key")),
                        ErlExprBlock.block(
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("Prefix"),
                                        formListPrefix(".member.")),
                                ErlCallLocal.callLocal(
                                        "indexed_form_values", ErlVar.var("Params"), ErlVar.var("Prefix"))))));
    }

    private static ErlFunction formListValuesEc2() {
        return ErlFunction.function(
                "form_list_values_ec2",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Params"), ErlVarPattern.varPattern("Key")),
                        ErlExprBlock.block(
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("Prefix"),
                                        formListPrefix(".")),
                                ErlCallLocal.callLocal(
                                        "indexed_form_values", ErlVar.var("Params"), ErlVar.var("Prefix"))))));
    }

    private static ErlBinaryTemplate formListPrefix(String suffix) {
        return ErlBinaryTemplate.binaryTemplate(
                ErlBinaryExpr.expr(ErlVar.var("Key"), true),
                ErlBinaryText.text(suffix));
    }

    private static ErlFunction indexedFormValues() {
        ErlListComprehension entries = ErlListComprehension.comprehensionWithFilters(
                ErlTuple.tuple(
                        ErlCallLocal.callLocal("form_index", ErlVar.var("K"), ErlVar.var("Prefix")),
                        ErlCall.call("maps", "get", ErlVar.var("K"), ErlVar.var("Params"))),
                ErlVarPattern.varPattern("K"),
                ErlCall.call("maps", "keys", ErlVar.var("Params")),
                List.of(ErlOp.op(
                        "=:=",
                        ErlCall.call("binary", "match", ErlVar.var("K"), ErlVar.var("Prefix")),
                        ErlTuple.tuple(ErlInteger.integer(0), ErlCallLocal.callLocal("byte_size", ErlVar.var("Prefix"))))));

        ErlListComprehension sortedValues = ErlListComprehension.comprehension(
                ErlVar.var("V"),
                ErlTuplePattern.tuplePattern(W, ErlVarPattern.varPattern("V")),
                ErlVar.var("Sorted"));

        return ErlFunction.function(
                "indexed_form_values",
                2,
                List.of(ErlClause.blockClause(
                        List.of(ErlVarPattern.varPattern("Params"), ErlVarPattern.varPattern("Prefix")),
                        ErlExprBlock.block(
                                ErlMatch.match(ErlVarPattern.varPattern("Entries"), entries),
                                ErlCase.caseExpr(
                                        ErlCall.call("lists", "sort", ErlVar.var("Entries")),
                                        ErlClause.clause(List.of(ErlNilPattern.nilPattern()), ErlAtom.atom("undefined")),
                                        ErlClause.clause(
                                                List.of(ErlVarPattern.varPattern("Sorted")),
                                                sortedValues))))));
    }

    private static ErlFunction formIndex() {
        return ErlFunction.function(
                "form_index",
                2,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Key"), ErlVarPattern.varPattern("Prefix")),
                        ErlExprBlock.block(
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("Rest"),
                                        ErlCall.call(
                                                "binary",
                                                "part",
                                                ErlVar.var("Key"),
                                                ErlCallLocal.callLocal("byte_size", ErlVar.var("Prefix")),
                                                ErlOp.op(
                                                        "-",
                                                        ErlCallLocal.callLocal("byte_size", ErlVar.var("Key")),
                                                        ErlCallLocal.callLocal("byte_size", ErlVar.var("Prefix"))))),
                                ErlCallLocal.callLocal("binary_to_integer", ErlVar.var("Rest"))))));
    }

    private static ErlFunction wrapAwsQueryResponse() {
        return ErlFunction.function(
                "wrap_aws_query_response",
                4,
                List.of(ErlClause.clause(
                        List.of(
                                ErlVarPattern.varPattern("ResultName"),
                                ErlVarPattern.varPattern("ResultContent"),
                                ErlVarPattern.varPattern("ResponseName"),
                                ErlVarPattern.varPattern("XmlNs")),
                        ErlCallLocal.callLocal(
                                "encode_xml",
                                ErlMap.map(
                                        ErlMapEntry.entry(
                                                ErlVar.var("ResponseName"),
                                                ErlMap.map(
                                                        ErlMapEntry.entry(
                                                                ErlVar.var("ResultName"),
                                                                ErlVar.var("ResultContent"))))),
                                ErlVar.var("XmlNs")))));
    }
}
