package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryExpr;
import io.smithy.beam.ir.erlang.ErlBinaryPattern;
import io.smithy.beam.ir.erlang.ErlBinaryTemplate;
import io.smithy.beam.ir.erlang.ErlBinPattern;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlConsPattern;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlInteger;
import io.smithy.beam.ir.erlang.ErlNilPattern;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;

import java.util.ArrayList;
import java.util.List;

final class ErlangHttpChecksumIr {
    private ErlangHttpChecksumIr() {}

    static List<ErlFunction> checksumHelperFunctions() {
        List<ErlFunction> functions = new ArrayList<>();
        functions.add(ErlangCodecHelperIr.headersSet());
        functions.add(checksumHeaderEncode());
        functions.add(md5Hash());
        functions.add(sha256Hash());
        functions.add(crc32Hash());
        functions.add(crc32cHash());
        functions.add(crc64nvmeHash());
        functions.add(xxhash64Hash());
        functions.add(xxhash3Hash());
        functions.add(xxhash128Hash());
        functions.add(checksumDigest());
        functions.add(validateResponseChecksum());
        functions.add(checksumAlgorithmFromHeader());
        return functions;
    }

    private static ErlFunction checksumHeaderEncode() {
        return ErlFunction.function(
                "checksum_header_encode",
                1,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Data")),
                        List.of(ErlGuard.guard("is_binary", ErlVar.var("Data"))),
                        ErlCall.call("base64", "encode", ErlVar.var("Data")))));
    }

    private static ErlFunction md5Hash() {
        return ErlFunction.function(
                "md5_hash",
                1,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Body")),
                        ErlCall.call("crypto", "hash", ErlAtom.atom("md5"), ErlVar.var("Body")))));
    }

    private static ErlFunction sha256Hash() {
        return ErlFunction.function(
                "sha256_hash",
                1,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Body")),
                        ErlCall.call("crypto", "hash", ErlAtom.atom("sha256"), ErlVar.var("Body")))));
    }

    private static ErlFunction crc32Hash() {
        return ErlFunction.function(
                "crc32_hash",
                1,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Body")),
                        ErlBinaryTemplate.binaryTemplate(ErlBinaryExpr.expr(
                                ErlCall.call("erlang", "crc32", ErlVar.var("Body")),
                                "32/big-unsigned-integer")))));
    }

    private static ErlFunction crc32cHash() {
        return ErlFunction.function(
                "crc32c_hash",
                1,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Body")),
                        ErlCall.call("crypto", "hash", ErlAtom.atom("crc32c"), ErlVar.var("Body")))));
    }

    private static ErlFunction crc64nvmeHash() {
        return stubHashFunction("crc64nvme_hash", "crc64nvme");
    }

    private static ErlFunction xxhash64Hash() {
        return stubHashFunction("xxhash64_hash", "xxhash64");
    }

    private static ErlFunction xxhash3Hash() {
        return stubHashFunction("xxhash3_hash", "xxhash3");
    }

    private static ErlFunction xxhash128Hash() {
        return stubHashFunction("xxhash128_hash", "xxhash128");
    }

    private static ErlFunction stubHashFunction(String name, String algorithm) {
        return ErlFunction.function(
                name,
                1,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("_Body")),
                        ErlCallLocal.callLocal(
                                "error",
                                ErlTuple.tuple(
                                        ErlAtom.atom("unsupported_checksum_algorithm"),
                                        ErlAtom.atom(algorithm))))));
    }

    private static ErlFunction checksumDigest() {
        return ErlFunction.function(
                "checksum_digest",
                2,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Body"),
                                        ErlBinaryPattern.binaryPattern("MD5")),
                                ErlCallLocal.callLocal("md5_hash", ErlVar.var("Body"))),
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Body"),
                                        ErlBinaryPattern.binaryPattern("SHA256")),
                                ErlCallLocal.callLocal("sha256_hash", ErlVar.var("Body"))),
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Body"),
                                        ErlBinaryPattern.binaryPattern("CRC32")),
                                ErlCallLocal.callLocal("crc32_hash", ErlVar.var("Body"))),
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Body"),
                                        ErlBinaryPattern.binaryPattern("CRC32C")),
                                ErlCallLocal.callLocal("crc32c_hash", ErlVar.var("Body")))));
    }

    private static ErlFunction validateResponseChecksum() {
        ErlCase checksumMatch = ErlCase.caseExpr(
                ErlOp.op(
                        "=:=",
                        ErlCallLocal.callLocal(
                                "checksum_header_encode",
                                ErlCallLocal.callLocal(
                                        "checksum_digest",
                                        ErlVar.var("Body"),
                                        ErlCallLocal.callLocal(
                                                "checksum_algorithm_from_header",
                                                ErlVar.var("HeaderName")))),
                        ErlVar.var("Expected")),
                ErlClause.clause(List.of(ErlAtomPattern.atomPattern("true")), ErlAtom.atom("ok")),
                ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("false")),
                        ErlTuple.tuple(
                                ErlAtom.atom("error"),
                                ErlTuple.tuple(
                                        ErlAtom.atom("checksum_mismatch"),
                                        ErlVar.var("HeaderName")))));

        return ErlFunction.function(
                "validate_response_checksum",
                3,
                List.of(
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("_Body"),
                                        ErlVarPattern.varPattern("_Headers"),
                                        ErlNilPattern.nilPattern()),
                                ErlAtom.atom("ok")),
                        ErlClause.clause(
                                List.of(
                                        ErlVarPattern.varPattern("Body"),
                                        ErlVarPattern.varPattern("Headers"),
                                        ErlConsPattern.consPattern(
                                                ErlVarPattern.varPattern("HeaderName"),
                                                ErlVarPattern.varPattern("Rest"))),
                                ErlCase.caseExpr(
                                        ErlCall.call(
                                                "proplists",
                                                "get_value",
                                                ErlVar.var("HeaderName"),
                                                ErlVar.var("Headers"),
                                                ErlAtom.atom("undefined")),
                                        ErlClause.clause(
                                                List.of(ErlAtomPattern.atomPattern("undefined")),
                                                ErlCallLocal.callLocal(
                                                        "validate_response_checksum",
                                                        ErlVar.var("Body"),
                                                        ErlVar.var("Headers"),
                                                        ErlVar.var("Rest"))),
                                        ErlClause.clause(
                                                List.of(ErlVarPattern.varPattern("Expected")),
                                                checksumMatch)))));
    }

    private static ErlFunction checksumAlgorithmFromHeader() {
        return ErlFunction.function(
                "checksum_algorithm_from_header",
                1,
                List.of(ErlClause.clause(
                        List.of(ErlBinPattern.binPattern("\"x-amz-checksum-\", Rest/binary")),
                        ErlCallLocal.callLocal(
                                "list_to_binary",
                                ErlCall.call(
                                        "string",
                                        "uppercase",
                                        ErlCallLocal.callLocal("binary_to_list", ErlVar.var("Rest")))))));
    }
}
