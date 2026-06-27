package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryPattern;
import io.smithy.beam.ir.erlang.ErlBinaryExpr;
import io.smithy.beam.ir.erlang.ErlBinaryTemplate;
import io.smithy.beam.ir.erlang.ErlBinaryText;
import io.smithy.beam.ir.erlang.ErlBinPattern;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlCatchClause;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlConsPattern;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFun;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlInteger;
import io.smithy.beam.ir.erlang.ErlIntegerPattern;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlListComprehension;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlMatchPattern;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlString;
import io.smithy.beam.ir.erlang.ErlTry;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;

import java.util.ArrayList;
import java.util.List;

final class ErlangCodecHelperIr {
    private ErlangCodecHelperIr() {}

    enum ToBinaryVariant {
        REST_JSON,
        XML_QUERY
    }

    /** Reusable undefined/null tail clauses for wire-optional decoders. */
    static List<ErlClause> nullUndefinedTailClauses() {
        return List.of(
                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("null")), ErlAtom.atom("undefined")));
    }

    /** IR for maps:get(Key, Map, Default) used by structure decoders. */
    static ErlCall mapsGetDefault(ErlBinary key, ErlVar map, ErlAtom defaultValue) {
        return ErlCall.call("maps", "get", key, map, defaultValue);
    }

    public static ErlFunction generateUuid() {
        return ErlFunction.function(
                "generate_uuid",
                0,
                List.of(ErlClause.clause(
                        List.of(),
                        ErlCallLocal.callLocal(
                                "list_to_binary",
                                ErlCall.call("uuid", "to_string", ErlCall.call("uuid", "v4"))))));
    }

    public static ErlFunction uriEncode() {
        return ErlFunction.function(
                "uri_encode",
                1,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Value")),
                        ErlCall.call("uri_string", "quote", ErlVar.var("Value")))));
    }

    public static ErlFunction uriDecode() {
        return ErlFunction.function(
                "uri_decode",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("Value")),
                                List.of(ErlGuard.guard("is_binary", ErlVar.var("Value"))),
                                ErlCall.call("uri_string", "unquote", ErlVar.var("Value"))),
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlAtom.atom("undefined"))));
    }

    public static ErlFunction decodeQueryParam() {
        return ErlFunction.function(
                "decode_query_param",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlBinaryPattern.binaryPattern("true")),
                                ErlAtom.atom("true")),
                        ErlClause.clause(
                                List.of(ErlBinaryPattern.binaryPattern("false")),
                                ErlAtom.atom("false")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_binary", ErlVar.var("V"))),
                                ErlVar.var("V"))));
    }

    public static ErlFunction toBinary(ToBinaryVariant variant) {
        List<ErlClause> clauses = new ArrayList<>();
        clauses.add(ErlClause.clause(
                List.of(ErlVarPattern.varPattern("V")),
                List.of(ErlGuard.guard("is_binary", ErlVar.var("V"))),
                ErlVar.var("V")));
        clauses.add(ErlClause.clause(
                List.of(ErlVarPattern.varPattern("V")),
                List.of(ErlGuard.guard("is_list", ErlVar.var("V"))),
                ErlCallLocal.callLocal("list_to_binary", ErlVar.var("V"))));
        if (variant == ToBinaryVariant.REST_JSON) {
            clauses.add(ErlClause.clause(List.of(ErlAtomPattern.atomPattern("true")), ErlBinary.binary("true")));
            clauses.add(ErlClause.clause(List.of(ErlAtomPattern.atomPattern("false")), ErlBinary.binary("false")));
            clauses.add(ErlClause.clause(
                    List.of(ErlVarPattern.varPattern("V")),
                    List.of(ErlGuard.guard("is_atom", ErlVar.var("V"))),
                    ErlCallLocal.callLocal("atom_to_binary", ErlVar.var("V"), ErlAtom.atom("utf8"))));
            clauses.add(ErlClause.clause(
                    List.of(ErlVarPattern.varPattern("V")),
                    List.of(ErlGuard.guard("is_integer", ErlVar.var("V"))),
                    ErlCallLocal.callLocal("integer_to_binary", ErlVar.var("V"))));
            clauses.add(ErlClause.clause(
                    List.of(ErlVarPattern.varPattern("V")),
                    List.of(ErlGuard.guard("is_float", ErlVar.var("V"))),
                    ErlCallLocal.callLocal("float_to_binary", ErlVar.var("V"))));
        } else {
            clauses.add(ErlClause.clause(
                    List.of(ErlVarPattern.varPattern("V")),
                    List.of(ErlGuard.guard("is_atom", ErlVar.var("V"))),
                    ErlCallLocal.callLocal("atom_to_binary", ErlVar.var("V"), ErlAtom.atom("utf8"))));
            clauses.add(ErlClause.clause(
                    List.of(ErlVarPattern.varPattern("V")),
                    List.of(ErlGuard.guard("is_integer", ErlVar.var("V"))),
                    ErlCallLocal.callLocal("integer_to_binary", ErlVar.var("V"))));
            clauses.add(ErlClause.clause(
                    List.of(ErlVarPattern.varPattern("V")),
                    List.of(ErlGuard.guard("is_float", ErlVar.var("V"))),
                    ErlCallLocal.callLocal("float_to_binary", ErlVar.var("V"), ErlList.list(ErlAtom.atom("short")))));
            clauses.add(ErlClause.clause(
                    List.of(ErlVarPattern.varPattern("V")),
                    List.of(ErlGuard.guard("is_boolean", ErlVar.var("V"))),
                    ErlCallLocal.callLocal("atom_to_binary", ErlVar.var("V"), ErlAtom.atom("utf8"))));
        }
        return ErlFunction.function("to_binary", 1, clauses);
    }

    public static ErlFunction encodeQueryValueRestJson() {
        return ErlFunction.function(
                "encode_query_value",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_boolean", ErlVar.var("V"))),
                                ErlCallLocal.callLocal("atom_to_binary", ErlVar.var("V"), ErlAtom.atom("utf8"))),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_integer", ErlVar.var("V"))),
                                ErlCallLocal.callLocal("integer_to_binary", ErlVar.var("V"))),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_float", ErlVar.var("V"))),
                                ErlCallLocal.callLocal("float_to_binary", ErlVar.var("V"))),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_binary", ErlVar.var("V"))),
                                ErlVar.var("V")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_atom", ErlVar.var("V"))),
                                ErlCallLocal.callLocal("atom_to_binary", ErlVar.var("V"), ErlAtom.atom("utf8")))));
    }

    public static ErlFunction encodeQueryValueXmlQuery() {
        return ErlFunction.function(
                "encode_query_value",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_integer", ErlVar.var("V"))),
                                ErlCallLocal.callLocal("integer_to_binary", ErlVar.var("V"))),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_float", ErlVar.var("V"))),
                                ErlCallLocal.callLocal("float_to_binary", ErlVar.var("V"), ErlList.list(ErlAtom.atom("short")))),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_boolean", ErlVar.var("V"))),
                                ErlCallLocal.callLocal("atom_to_binary", ErlVar.var("V"), ErlAtom.atom("utf8"))),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("V")),
                                ErlCallLocal.callLocal("to_binary", ErlVar.var("V")))));
    }

    public static ErlFunction decodeList() {
        List<ErlClause> clauses = new ArrayList<>(nullUndefinedTailClauses());
        clauses.add(ErlClause.clause(
                List.of(ErlVarPattern.varPattern("List")),
                List.of(ErlGuard.guard("is_list", ErlVar.var("List"))),
                ErlListComprehension.comprehension(
                        ErlVar.var("V"),
                        ErlVarPattern.varPattern("V"),
                        ErlVar.var("List"),
                        ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("null")))));
        return ErlFunction.function("decode_list", 1, clauses);
    }

    public static ErlFunction decodeSparseList() {
        ErlCase nullToUndefined = ErlCase.caseExpr(
                ErlVar.var("V"),
                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("null")), ErlAtom.atom("undefined")),
                ErlClause.clause(List.of(ErlVarPattern.varPattern("_")), ErlVar.var("V")));
        List<ErlClause> clauses = new ArrayList<>(nullUndefinedTailClauses());
        clauses.add(ErlClause.clause(
                List.of(ErlVarPattern.varPattern("List")),
                List.of(ErlGuard.guard("is_list", ErlVar.var("List"))),
                ErlListComprehension.comprehension(
                        nullToUndefined,
                        ErlVarPattern.varPattern("V"),
                        ErlVar.var("List"))));
        return ErlFunction.function("decode_sparse_list", 1, clauses);
    }

    public static ErlFunction encodeSparseList() {
        ErlCase undefinedToNull = ErlCase.caseExpr(
                ErlVar.var("V"),
                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("null")),
                ErlClause.clause(List.of(ErlVarPattern.varPattern("_")), ErlVar.var("V")));
        return ErlFunction.function(
                "encode_sparse_list",
                1,
                List.of(
                        ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("null")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("List")),
                                List.of(ErlGuard.guard("is_list", ErlVar.var("List"))),
                                ErlListComprehension.comprehension(
                                        undefinedToNull,
                                        ErlVarPattern.varPattern("V"),
                                        ErlVar.var("List")))));
    }

    public static ErlFunction encodeSparseMap() {
        ErlFun sparseMapFun = ErlFun.fun(
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("_K"), ErlAtomPattern.atomPattern("undefined")),
                        ErlAtom.atom("null")),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("_K"), ErlVarPattern.varPattern("V")),
                        ErlVar.var("V")));
        return ErlFunction.function(
                "encode_sparse_map",
                1,
                List.of(
                        ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("null")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("Map")),
                                List.of(ErlGuard.guard("is_map", ErlVar.var("Map"))),
                                ErlCall.call("maps", "map", sparseMapFun, ErlVar.var("Map")))));
    }

    public static ErlFunction decodeJsonBody() {
        return ErlFunction.function(
                "decode_json_body",
                1,
                List.of(
                        ErlClause.clause(List.of(ErlBinaryPattern.binaryPattern("")), ErlMap.map()),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("Body")),
                                ErlCase.caseExpr(
                                        ErlCall.call("jsone", "try_decode", ErlVar.var("Body")),
                                        ErlClause.clause(
                                                List.of(ErlTuplePattern.tuplePattern(
                                                        ErlAtomPattern.atomPattern("ok"),
                                                        ErlVarPattern.varPattern("V"),
                                                        ErlVarPattern.varPattern("_"))),
                                                List.of(ErlGuard.guard("is_map", ErlVar.var("V"))),
                                                ErlVar.var("V")),
                                        ErlClause.clause(
                                                List.of(ErlVarPattern.varPattern("_")),
                                                ErlMap.map())))));
    }

    public static ErlFunction contentTypeMatches() {
        return ErlFunction.function(
                "content_type_matches",
                2,
                List.of(ErlClause.clause(
                        List.of(
                                ErlVarPattern.varPattern("Headers"),
                                ErlVarPattern.varPattern("Expected")),
                        ErlCase.caseExpr(
                                ErlCall.call(
                                        "proplists",
                                        "get_value",
                                        ErlBinary.binary("Content-Type"),
                                        ErlVar.var("Headers"),
                                        ErlAtom.atom("undefined")),
                                ErlClause.clause(
                                        List.of(ErlVarPattern.varPattern("Expected")),
                                        ErlAtom.atom("true")),
                                ErlClause.clause(
                                        List.of(ErlMatchPattern.matchPattern(
                                                ErlBinPattern.binPattern("_/binary"),
                                                ErlVarPattern.varPattern("CT"))),
                                        ErlOp.op(
                                                "=:=",
                                                ErlCallLocal.callLocal("ct_base", ErlVar.var("CT")),
                                                ErlCallLocal.callLocal("ct_base", ErlVar.var("Expected")))),
                                ErlClause.clause(
                                        List.of(ErlVarPattern.varPattern("_")),
                                        ErlAtom.atom("false"))))));
    }

    public static ErlFunction ctBase() {
        return ErlFunction.function(
                "ct_base",
                1,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("CT")),
                        ErlCase.caseExpr(
                                ErlCall.call("binary", "split", ErlVar.var("CT"), ErlBinary.binary(";")),
                                ErlClause.clause(
                                        List.of(ErlConsPattern.consPattern(
                                                ErlVarPattern.varPattern("Base"),
                                                ErlVarPattern.varPattern("_"))),
                                        ErlVar.var("Base")),
                                ErlClause.clause(
                                        List.of(ErlVarPattern.varPattern("_")),
                                        ErlVar.var("CT"))))));
    }

    public static ErlFunction prefixHeadersToList() {
        ErlBinaryTemplate headerName = ErlBinaryTemplate.binaryTemplate(
                ErlBinaryExpr.expr(ErlVar.var("Prefix"), true),
                ErlBinaryExpr.expr(ErlVar.var("H"), true));
        return ErlFunction.function(
                "prefix_headers_to_list",
                2,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("_Prefix"),
                                        ErlAtomPattern.atomPattern("undefined")),
                                ErlList.list()),
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Prefix"),
                                        ErlVarPattern.varPattern("Map")),
                                List.of(ErlGuard.guard("is_map", ErlVar.var("Map"))),
                                ErlListComprehension.comprehension(
                                        ErlTuple.tuple(
                                                headerName,
                                                ErlCallLocal.callLocal("to_binary", ErlVar.var("V"))),
                                        ErlTuplePattern.tuplePattern(
                                                ErlVarPattern.varPattern("H"),
                                                ErlVarPattern.varPattern("V")),
                                        ErlCall.call("maps", "to_list", ErlVar.var("Map"))))));
    }

    public static ErlFunction prefixHeadersFromList() {
        ErlListComprehension headerEntries = ErlListComprehension.comprehensionWithFilters(
                ErlTuple.tuple(
                        ErlCall.call(
                                "binary",
                                "part",
                                ErlVar.var("Name"),
                                ErlCallLocal.callLocal("byte_size", ErlVar.var("Prefix"))),
                        ErlVar.var("Val")),
                ErlTuplePattern.tuplePattern(
                        ErlVarPattern.varPattern("Name"),
                        ErlVarPattern.varPattern("Val")),
                ErlVar.var("Headers"),
                List.of(
                        ErlOp.op(
                                ">",
                                ErlCallLocal.callLocal("byte_size", ErlVar.var("Name")),
                                ErlCallLocal.callLocal("byte_size", ErlVar.var("Prefix"))),
                        ErlOp.op(
                                "=:=",
                                ErlCall.call(
                                        "binary",
                                        "part",
                                        ErlVar.var("Name"),
                                        ErlInteger.integer(0),
                                        ErlCallLocal.callLocal("byte_size", ErlVar.var("Prefix"))),
                                ErlVar.var("Prefix"))));
        return ErlFunction.function(
                "prefix_headers_from_list",
                2,
                List.of(ErlClause.clause(
                        List.of(
                                ErlVarPattern.varPattern("Headers"),
                                ErlVarPattern.varPattern("Prefix")),
                        ErlExprBlock.block(
                                ErlMatch.match(
                                        ErlVarPattern.varPattern("Map"),
                                        ErlCall.call("maps", "from_list", headerEntries)),
                                ErlCase.caseExpr(
                                        ErlCall.call("maps", "size", ErlVar.var("Map")),
                                        ErlClause.clause(
                                                List.of(ErlIntegerPattern.integerPattern(0)),
                                                ErlAtom.atom("undefined")),
                                        ErlClause.clause(
                                                List.of(ErlVarPattern.varPattern("_")),
                                                ErlVar.var("Map")))))));
    }

    public static ErlFunction encodeTimestampEpochSeconds() {
        return ErlFunction.function(
                "encode_timestamp_epoch_seconds",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlTuplePattern.tuplePattern(
                                        ErlVarPattern.varPattern("Mega"),
                                        ErlVarPattern.varPattern("Secs"),
                                        ErlVarPattern.varPattern("_Micro"))),
                                ErlOp.op(
                                        "+",
                                        ErlOp.op("*", ErlVar.var("Mega"), ErlInteger.integer(1_000_000L)),
                                        ErlVar.var("Secs"))),
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlAtom.atom("undefined"))));
    }

    public static ErlFunction encodeTimestampDateTime() {
        return ErlFunction.function(
                "encode_timestamp_date_time",
                1,
                List.of(
                        ErlClause.clause(
                                List.of(ErlTuplePattern.tuplePattern(
                                        ErlVarPattern.varPattern("Mega"),
                                        ErlVarPattern.varPattern("Secs"),
                                        ErlVarPattern.varPattern("_Micro"))),
                                ErlExprBlock.block(
                                        ErlMatch.match(
                                                ErlVarPattern.varPattern("EpochSecs"),
                                                ErlOp.op(
                                                        "+",
                                                        ErlOp.op(
                                                                "*",
                                                                ErlVar.var("Mega"),
                                                                ErlInteger.integer(1_000_000L)),
                                                        ErlVar.var("Secs"))),
                                        ErlMatch.match(
                                                ErlTuplePattern.tuplePattern(
                                                        ErlTuplePattern.tuplePattern(
                                                                ErlVarPattern.varPattern("Y"),
                                                                ErlVarPattern.varPattern("Mo"),
                                                                ErlVarPattern.varPattern("D")),
                                                        ErlTuplePattern.tuplePattern(
                                                                ErlVarPattern.varPattern("H"),
                                                                ErlVarPattern.varPattern("Mi"),
                                                                ErlVarPattern.varPattern("S"))),
                                                ErlCall.call(
                                                        "calendar",
                                                        "gregorian_seconds_to_datetime",
                                                        ErlOp.op(
                                                                "+",
                                                                ErlVar.var("EpochSecs"),
                                                                ErlInteger.integer(62_167_219_200L)))),
                                        ErlCallLocal.callLocal(
                                                "iolist_to_binary",
                                                ErlCall.call(
                                                        "io_lib",
                                                        "format",
                                                        ErlString.string(
                                                                "~4..0B-~2..0B-~2..0BT~2..0B:~2..0B:~2..0BZ"),
                                                        ErlList.list(
                                                                ErlVar.var("Y"),
                                                                ErlVar.var("Mo"),
                                                                ErlVar.var("D"),
                                                                ErlVar.var("H"),
                                                                ErlVar.var("Mi"),
                                                                ErlVar.var("S")))))),
                        ErlClause.clause(
                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                ErlAtom.atom("undefined"))));
    }

    public static ErlFunction decodeTimestampEpochSeconds() {
        return ErlFunction.function(
                "decode_timestamp_epoch_seconds",
                1,
                List.of(
                        ErlClause.clause(List.of(ErlAtomPattern.atomPattern("null")), ErlAtom.atom("undefined")),
                        ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_number", ErlVar.var("V"))),
                                ErlExprBlock.block(
                                        ErlMatch.match(
                                                ErlVarPattern.varPattern("Mega"),
                                                ErlOp.op("div", ErlVar.var("V"), ErlInteger.integer(1_000_000L))),
                                        ErlMatch.match(
                                                ErlVarPattern.varPattern("Secs"),
                                                ErlOp.op("rem", ErlVar.var("V"), ErlInteger.integer(1_000_000L))),
                                        ErlTuple.tuple(
                                                ErlVar.var("Mega"),
                                                ErlVar.var("Secs"),
                                                ErlInteger.integer(0))))));
    }

    public static ErlFunction decodeTimestampDateTime() {
        ErlTry iso8601Parse = ErlTry.tryExpr(
                List.of(
                        ErlMatch.match(
                                ErlBinPattern.binPattern(
                                        "Y:4/binary, \"-\", Mo:2/binary, \"-\", D:2/binary, \"T\","
                                                + " H:2/binary, \":\", Mi:2/binary, \":\", S:2/binary, _/binary"),
                                ErlVar.var("V")),
                        ErlMatch.match(
                                ErlVarPattern.varPattern("Dt"),
                                ErlTuple.tuple(
                                        ErlTuple.tuple(
                                                ErlCallLocal.callLocal("binary_to_integer", ErlVar.var("Y")),
                                                ErlCallLocal.callLocal("binary_to_integer", ErlVar.var("Mo")),
                                                ErlCallLocal.callLocal("binary_to_integer", ErlVar.var("D"))),
                                        ErlTuple.tuple(
                                                ErlCallLocal.callLocal("binary_to_integer", ErlVar.var("H")),
                                                ErlCallLocal.callLocal("binary_to_integer", ErlVar.var("Mi")),
                                                ErlCallLocal.callLocal("binary_to_integer", ErlVar.var("S"))))),
                        ErlMatch.match(
                                ErlVarPattern.varPattern("GregorianSecs"),
                                ErlCall.call("calendar", "datetime_to_gregorian_seconds", ErlVar.var("Dt"))),
                        ErlMatch.match(
                                ErlVarPattern.varPattern("EpochSecs"),
                                ErlOp.op("-", ErlVar.var("GregorianSecs"), ErlInteger.integer(62_167_219_200L))),
                        ErlMatch.match(
                                ErlVarPattern.varPattern("Mega"),
                                ErlOp.op("div", ErlVar.var("EpochSecs"), ErlInteger.integer(1_000_000L))),
                        ErlTuple.tuple(
                                ErlVar.var("Mega"),
                                ErlOp.op("rem", ErlVar.var("EpochSecs"), ErlInteger.integer(1_000_000L)),
                                ErlInteger.integer(0))),
                List.of(ErlCatchClause.catchClause(
                        ErlVarPattern.varPattern("_"),
                        ErlVarPattern.varPattern("_"),
                        ErlAtom.atom("undefined"))));
        return ErlFunction.function(
                "decode_timestamp_date_time",
                1,
                List.of(
                        ErlClause.clause(List.of(ErlAtomPattern.atomPattern("null")), ErlAtom.atom("undefined")),
                        ErlClause.clause(List.of(ErlAtomPattern.atomPattern("undefined")), ErlAtom.atom("undefined")),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_number", ErlVar.var("V"))),
                                ErlExprBlock.block(
                                        ErlMatch.match(
                                                ErlVarPattern.varPattern("EpochSecs"),
                                                ErlCallLocal.callLocal("trunc", ErlVar.var("V"))),
                                        ErlMatch.match(
                                                ErlVarPattern.varPattern("Mega"),
                                                ErlOp.op(
                                                        "div",
                                                        ErlVar.var("EpochSecs"),
                                                        ErlInteger.integer(1_000_000L))),
                                        ErlTuple.tuple(
                                                ErlVar.var("Mega"),
                                                ErlOp.op(
                                                        "rem",
                                                        ErlVar.var("EpochSecs"),
                                                        ErlInteger.integer(1_000_000L)),
                                                ErlInteger.integer(0)))),
                        ErlClause.clause(
                                List.of(ErlVarPattern.varPattern("V")),
                                List.of(ErlGuard.guard("is_binary", ErlVar.var("V"))),
                                iso8601Parse)));
    }

    public static ErlFunction headersSet() {
        return ErlFunction.function(
                "headers_set",
                3,
                List.of(ErlClause.clause(
                        List.of(
                                ErlVarPattern.varPattern("Name"),
                                ErlVarPattern.varPattern("Value"),
                                ErlVarPattern.varPattern("Headers")),
                        ErlCall.call(
                                "lists",
                                "keystore",
                                ErlVar.var("Name"),
                                ErlInteger.integer(1),
                                ErlVar.var("Headers"),
                                ErlTuple.tuple(ErlVar.var("Name"), ErlVar.var("Value"))))));
    }
}
