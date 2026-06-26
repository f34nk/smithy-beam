package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryExpr;
import io.smithy.beam.ir.erlang.ErlBinaryPattern;
import io.smithy.beam.ir.erlang.ErlBinaryTemplate;
import io.smithy.beam.ir.erlang.ErlBinaryText;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlListComprehension;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlMapFieldPattern;
import io.smithy.beam.ir.erlang.ErlMapPattern;
import io.smithy.beam.ir.erlang.ErlMapUpdate;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlMatchPattern;
import io.smithy.beam.ir.erlang.ErlNilPattern;
import io.smithy.beam.ir.erlang.ErlRecord;
import io.smithy.beam.ir.erlang.ErlRecordField;
import io.smithy.beam.ir.erlang.ErlRecordFieldPattern;
import io.smithy.beam.ir.erlang.ErlRecordPattern;
import io.smithy.beam.ir.erlang.ErlRemoteCall;
import io.smithy.beam.ir.erlang.ErlString;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;

import java.util.ArrayList;
import java.util.List;

final class ErlangHttpDispatchIr {
    private ErlangHttpDispatchIr() {}

    static ErlFunction dispatchArity2() {
        return ErlFunction.function(
                "dispatch",
                2,
                List.of(ErlClause.clause(
                        List.of(
                                ErlVarPattern.varPattern("Config"),
                                ErlVarPattern.varPattern("Request")),
                        ErlExprBlock.block(
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("HttpClient"),
                                        ErlCall.call(
                                                "maps",
                                                "get",
                                                ErlAtom.atom("http_client"),
                                                ErlVar.var("Config"),
                                                ErlAtom.atom("httpc"))),
                                ErlCallLocal.callLocal(
                                        "dispatch",
                                        ErlVar.var("HttpClient"),
                                        ErlVar.var("Config"),
                                        ErlVar.var("Request"))))));
    }

    static ErlFunction dispatchArity3() {
        return ErlFunction.function(
                "dispatch",
                3,
                List.of(ErlClause.clause(
                        List.of(
                                ErlVarPattern.varPattern("HttpClient"),
                                ErlVarPattern.varPattern("Config"),
                                ErlVarPattern.varPattern("Request")),
                        ErlCallLocal.callLocal(
                                "dispatch_signed",
                                ErlVar.var("HttpClient"),
                                ErlVar.var("Config"),
                                ErlVar.var("Request")))));
    }

    static ErlFunction dispatchSigned(
            boolean sigv4,
            boolean endpointRules,
            String configVar,
            String helpersMod,
            String endpointsMod,
            String credentialsMod) {
        List<ErlExpr> body = new ArrayList<>();
        if (sigv4) {
            body.add(sigv4ConfigMatch(credentialsMod));
        }
        body.add(resolveBaseUrlMatch(configVar, helpersMod, endpointsMod, endpointRules));
        body.add(queryStringMatch());
        body.add(ErlMatch.match(
                ErlTuplePattern.tuplePattern(
                        ErlVarPattern.varPattern("Scheme"),
                        ErlVarPattern.varPattern("DefaultAuthority")),
                ErlCallLocal.callLocal("split_base_url", ErlVar.var("BaseUrl"))));
        body.add(authorityMatch());
        body.add(ErlMatch.match(
                ErlVarPattern.varPattern("ReqUrl"),
                ErlBinaryTemplate.binaryTemplate(
                        ErlBinaryExpr.expr(ErlVar.var("Scheme"), true),
                        ErlBinaryExpr.expr(ErlVar.var("Authority"), true),
                        ErlBinaryExpr.expr(ErlVar.var("Path"), true),
                        ErlBinaryExpr.expr(ErlVar.var("QueryStr"), true))));
        body.add(httpcHeadersMatch());
        body.add(requestTupleMatch());
        body.add(httpClientRequestCase());

        return ErlFunction.function(
                "dispatch_signed",
                3,
                List.of(ErlClause.clause(
                        List.of(
                                ErlVarPattern.varPattern("HttpClient"),
                                ErlVarPattern.varPattern("Config"),
                                httpRequestPattern()),
                        ErlExprBlock.block(body.toArray(ErlExpr[]::new)))));
    }

    static ErlFunction splitBaseUrl() {
        ErlBinaryTemplate schemePrefix = ErlBinaryTemplate.binaryTemplate(
                ErlBinaryExpr.expr(ErlCallLocal.callLocal("list_to_binary", ErlVar.var("Scheme")), "binary"),
                ErlBinaryText.text("://"));
        ErlBinaryTemplate authority = ErlBinaryTemplate.binaryTemplate(
                ErlBinaryExpr.expr(ErlCallLocal.callLocal("list_to_binary", ErlVar.var("Host")), "binary"),
                ErlBinaryExpr.expr(ErlVar.var("PortSuffix"), true));
        ErlBinaryTemplate portSuffix = ErlBinaryTemplate.binaryTemplate(
                ErlBinaryText.text(":"),
                ErlBinaryExpr.expr(ErlCallLocal.callLocal("integer_to_binary", ErlVar.var("Port")), "binary"));

        ErlCase portSuffixCase = ErlCase.caseExpr(
                ErlCall.call("maps", "get", ErlAtom.atom("port"), ErlVar.var("Parts"), ErlAtom.atom("undefined")),
                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlBinary.binary("")),
                ErlClause.clause(List.of(ErlVarPattern.varPattern("Port")), portSuffix));

        ErlCase parseCase = ErlCase.caseExpr(
                ErlCall.call("uri_string", "parse", ErlCallLocal.callLocal("binary_to_list", ErlVar.var("BaseUrl"))),
                ErlClause.clause(
                        List.of(ErlMatchPattern.matchPattern(
                                ErlMapPattern.mapPattern(
                                        ErlMapFieldPattern.fieldPattern("scheme", ErlVarPattern.varPattern("Scheme")),
                                        ErlMapFieldPattern.fieldPattern("host", ErlVarPattern.varPattern("Host"))),
                                ErlVarPattern.varPattern("Parts"))),
                        ErlExprBlock.block(
                                ErlMatch.match(ErlVarPattern.varPattern("PortSuffix"), portSuffixCase),
                                ErlTuple.tuple(schemePrefix, authority))),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("_")),
                        ErlTuple.tuple(ErlBinary.binary(""), ErlVar.var("BaseUrl"))));

        return ErlFunction.function(
                "split_base_url",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlBinaryPattern.binaryPattern("")),
                                ErlTuple.tuple(ErlBinary.binary(""), ErlBinary.binary(""))),
                        ErlClause.clause(List.of(ErlVarPattern.varPattern("BaseUrl")), parseCase)));
    }

    static ErlFunction mime() {
        return ErlFunction.function(
                "mime",
                1,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Headers")),
                        ErlCase.caseExpr(
                                ErlCall.call(
                                        "proplists",
                                        "get_value",
                                        ErlBinary.binary("Content-Type"),
                                        ErlVar.var("Headers")),
                                ErlClause.clause(
                                        List.of(ErlAtomPattern.atomPattern("undefined")),
                                        ErlString.string("application/octet-stream")),
                                ErlClause.clause(
                                        List.of(ErlVarPattern.varPattern("CT")),
                                        ErlCallLocal.callLocal("binary_to_list", ErlVar.var("CT")))))));
    }

    static void writeFunction(ErlangWriter writer, ErlFunction fn) {
        writer.write("$L", fn.asString());
        writer.write("");
    }

    private static ErlRecordPattern httpRequestPattern() {
        return ErlRecordPattern.recordPattern(
                "http_request",
                ErlRecordFieldPattern.fieldPattern("method", ErlVarPattern.varPattern("Method")),
                ErlRecordFieldPattern.fieldPattern("path", ErlVarPattern.varPattern("Path")),
                ErlRecordFieldPattern.fieldPattern("query", ErlVarPattern.varPattern("Query")),
                ErlRecordFieldPattern.fieldPattern("headers", ErlVarPattern.varPattern("Headers")),
                ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")),
                ErlRecordFieldPattern.fieldPattern("host", ErlVarPattern.varPattern("Host")));
    }

    private static ErlMatch sigv4ConfigMatch(String credentialsMod) {
        return ErlMatch.match(
                ErlVarPattern.varPattern("Config1"),
                ErlCase.caseExpr(
                        ErlCall.call("maps", "get", ErlAtom.atom("credentials"), ErlVar.var("Config"), ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlCase.caseExpr(
                                        ErlCall.call(credentialsMod, "resolve", ErlVar.var("Config")),
                                        ErlClause.clause(
                                                List.of(ErlTuplePattern.tuplePattern(
                                                        ErlAtomPattern.atomPattern("ok"),
                                                        ErlVarPattern.varPattern("Creds"))),
                                                ErlMapUpdate.mapUpdate(
                                                        ErlVar.var("Config"),
                                                        ErlMapEntry.entry(ErlAtom.atom("credentials"), ErlVar.var("Creds")))),
                                        ErlClause.clause(
                                                List.of(ErlVarPattern.varPattern("_")),
                                                ErlVar.var("Config")))),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("_")),
                                ErlVar.var("Config"))));
    }

    private static ErlMatch resolveBaseUrlMatch(
            String configVar,
            String helpersMod,
            String endpointsMod,
            boolean endpointRules) {
        ErlClause endpointPrefixFallback;
        if (endpointRules) {
            endpointPrefixFallback = ErlClause.clause(
                    List.of(ErlVarPattern.varPattern("_")),
                    ErlCase.caseExpr(
                            ErlCall.call(endpointsMod, "resolve", ErlVar.var(configVar), ErlMap.map()),
                            ErlClause.clause(
                                    List.of(ErlTuplePattern.tuplePattern(
                                            ErlAtomPattern.atomPattern("ok"),
                                            ErlMapPattern.mapPattern(
                                                    ErlMapFieldPattern.fieldPattern("url", ErlVarPattern.varPattern("ResolvedUrl"))))),
                                    ErlVar.var("ResolvedUrl")),
                            ErlClause.clause(
                                    List.of(ErlVarPattern.varPattern("_")),
                                    ErlCall.call(helpersMod, "resolve_base_url", ErlVar.var(configVar)))));
        } else {
            endpointPrefixFallback = ErlClause.clause(
                    List.of(ErlVarPattern.varPattern("_")),
                    ErlCall.call(helpersMod, "resolve_base_url", ErlVar.var(configVar)));
        }

        return ErlMatch.match(
                ErlVarPattern.varPattern("BaseUrl"),
                ErlCase.caseExpr(
                        ErlCall.call("maps", "get", ErlAtom.atom("base_url"), ErlVar.var(configVar), ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlCase.caseExpr(
                                        ErlCall.call("maps", "get", ErlAtom.atom("endpoint_prefix"), ErlVar.var(configVar), ErlAtom.atom("undefined")),
                                        ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlBinary.binary("")),
                                        endpointPrefixFallback)),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("GivenUrl")),
                                ErlVar.var("GivenUrl"))));
    }

    private static ErlMatch queryStringMatch() {
        return ErlMatch.match(
                ErlVarPattern.varPattern("QueryStr"),
                ErlCase.caseExpr(
                        ErlCall.call("maps", "to_list", ErlVar.var("Query")),
                        ErlClause.clause(List.of(ErlNilPattern.nilPattern()), ErlBinary.binary("")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("Pairs")),
                                ErlExprBlock.block(
                                        ErlMatch.match(
                                                ErlVarPattern.varPattern("Encoded"),
                                                ErlCall.call(
                                                        "uri_string",
                                                        "compose_query",
                                                        ErlListComprehension.comprehension(
                                                                ErlTuple.tuple(ErlVar.var("K"), ErlVar.var("V")),
                                                                ErlTuplePattern.tuplePattern(
                                                                        ErlVarPattern.varPattern("K"),
                                                                        ErlVarPattern.varPattern("V")),
                                                                ErlVar.var("Pairs")))),
                                        ErlBinaryTemplate.binaryTemplate(
                                                ErlBinaryText.text("?"),
                                                ErlBinaryExpr.expr(ErlVar.var("Encoded"), true))))));
    }

    private static ErlMatch authorityMatch() {
        return ErlMatch.match(
                ErlVarPattern.varPattern("Authority"),
                ErlCase.caseExpr(
                        ErlVar.var("Host"),
                        ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlVar.var("DefaultAuthority")),
                        ErlClause.clause(List.of(ErlVarPattern.varPattern("_")), ErlVar.var("Host"))));
    }

    private static ErlMatch httpcHeadersMatch() {
        return ErlMatch.match(
                ErlVarPattern.varPattern("HttpcHeaders"),
                ErlListComprehension.comprehension(
                        ErlTuple.tuple(
                                ErlCallLocal.callLocal("binary_to_list", ErlVar.var("K")),
                                ErlCallLocal.callLocal("binary_to_list", ErlVar.var("V"))),
                        ErlTuplePattern.tuplePattern(
                                ErlVarPattern.varPattern("K"),
                                ErlVarPattern.varPattern("V")),
                        ErlVar.var("Headers")));
    }

    private static ErlMatch requestTupleMatch() {
        return ErlMatch.match(
                ErlVarPattern.varPattern("Req"),
                ErlCase.caseExpr(
                        ErlVar.var("Body"),
                        ErlClause.clause(
                                List.of(ErlBinaryPattern.binaryPattern("")),
                                ErlTuple.tuple(
                                        ErlCallLocal.callLocal("binary_to_list", ErlVar.var("ReqUrl")),
                                        ErlVar.var("HttpcHeaders"))),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("_")),
                                ErlTuple.tuple(
                                        ErlCallLocal.callLocal("binary_to_list", ErlVar.var("ReqUrl")),
                                        ErlVar.var("HttpcHeaders"),
                                        ErlCallLocal.callLocal("mime", ErlVar.var("Headers")),
                                        ErlVar.var("Body")))));
    }

    private static ErlCase httpClientRequestCase() {
        ErlMatch binHeadersMatch = ErlMatch.match(
                ErlVarPattern.varPattern("BinHeaders"),
                ErlListComprehension.comprehension(
                        ErlTuple.tuple(
                                ErlCallLocal.callLocal("list_to_binary", ErlVar.var("K")),
                                ErlCallLocal.callLocal("list_to_binary", ErlVar.var("V"))),
                        ErlTuplePattern.tuplePattern(
                                ErlVarPattern.varPattern("K"),
                                ErlVarPattern.varPattern("V")),
                        ErlVar.var("RespHeaders")));

        return ErlCase.caseExpr(
                ErlRemoteCall.call(
                        ErlVar.var("HttpClient"),
                        "request",
                        ErlCallLocal.callLocal("binary_to_atom",
                                ErlCall.call("string", "lowercase", ErlVar.var("Method")),
                                ErlAtom.atom("utf8")),
                        ErlVar.var("Req"),
                        ErlList.list(),
                        ErlList.list(ErlTuple.tuple(ErlAtom.atom("body_format"), ErlAtom.atom("binary")))),
                ErlClause.clause(
                        List.of(ErlTuplePattern.tuplePattern(
                                ErlAtomPattern.atomPattern("ok"),
                                ErlTuplePattern.tuplePattern(
                                        ErlTuplePattern.tuplePattern(
                                                ErlVarPattern.varPattern("_"),
                                                ErlVarPattern.varPattern("Status"),
                                                ErlVarPattern.varPattern("_")),
                                        ErlVarPattern.varPattern("RespHeaders"),
                                        ErlVarPattern.varPattern("RespBody")))),
                        ErlExprBlock.block(
                                binHeadersMatch,
                                ErlTuple.tuple(
                                        ErlAtom.atom("ok"),
                                        ErlRecord.record(
                                                "http_response",
                                                ErlRecordField.field("status", ErlVar.var("Status")),
                                                ErlRecordField.field("headers", ErlVar.var("BinHeaders")),
                                                ErlRecordField.field("body", ErlVar.var("RespBody")))))),
                ErlClause.clause(
                        List.of(ErlTuplePattern.tuplePattern(
                                ErlAtomPattern.atomPattern("error"),
                                ErlVarPattern.varPattern("Reason"))),
                        ErlTuple.tuple(ErlAtom.atom("error"), ErlVar.var("Reason"))));
    }
}
