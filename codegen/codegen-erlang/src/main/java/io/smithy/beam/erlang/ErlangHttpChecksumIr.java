package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamHttpChecksumIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryExpr;
import io.smithy.beam.ir.erlang.ErlBinaryPattern;
import io.smithy.beam.ir.erlang.ErlBinaryTemplate;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlConsPattern;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlGuard;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlNilPattern;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

final class ErlangHttpChecksumIr {
  private ErlangHttpChecksumIr() {}

  static boolean serviceHasChecksumOperations(Model model, ServiceShape service) {
    BeamHttpChecksumIndex index = BeamHttpChecksumIndex.of(model);
    return ErlangTopDown.containedOperationsSorted(model, service).stream()
        .anyMatch(index::hasChecksumBehavior);
  }

  static Optional<ErlExpr> requestChecksumHeadersExpr(
      Model model, OperationShape op, SymbolProvider sp, String headersIn) {
    BeamHttpChecksumIndex checksumIndex = BeamHttpChecksumIndex.of(model);
    List<BeamHttpChecksumIndex.ChecksumBinding> bindings = checksumIndex.requestChecksums(op);
    if (bindings.isEmpty()) {
      return Optional.empty();
    }

    String headersOut = headersIn + "WithChecksum";
    Optional<String> algorithmMember = checksumIndex.requestAlgorithmMemberName(op);
    if (algorithmMember.isPresent()) {
      String bindingVar =
          ErlangJsonCodecSupport.toBindingVar(BeamNameUtils.toSnakeCase(algorithmMember.get()));
      List<ErlClause> clauses = new ArrayList<>();
      clauses.add(
          ErlClause.clause(
              List.of(ErlAtomPattern.atomPattern("undefined")), ErlVar.var(headersIn)));
      for (BeamHttpChecksumIndex.ChecksumBinding binding : bindings) {
        String enumAtom = enumAtomForAlgorithm(model, op, sp, checksumIndex, binding.algorithm());
        clauses.add(
            ErlClause.clause(
                List.of(ErlAtomPattern.atomPattern(enumAtom)),
                checksumBranchExpr(binding, headersIn)));
      }
      clauses.add(
          ErlClause.clause(
              List.of(ErlVarPattern.varPattern("Other")),
              ErlCallLocal.callLocal(
                  "error",
                  ErlTuple.tuple(
                      ErlAtom.atom("unsupported_checksum_algorithm"), ErlVar.var("Other")))));
      return Optional.of(
          ErlMatch.match(
              ErlVarPattern.varPattern(headersOut),
              ErlCase.caseExpr(ErlVar.var(bindingVar), clauses.toArray(ErlClause[]::new))));
    }

    List<ErlExpr> exprs = new ArrayList<>();
    String current = headersIn;
    for (int i = 0; i < bindings.size(); i++) {
      BeamHttpChecksumIndex.ChecksumBinding cb = bindings.get(i);
      String checksumVar = "Checksum" + i;
      String next = i == bindings.size() - 1 ? headersOut : headersIn + "Checksum" + i;
      exprs.add(checksumComputationExpr(cb, checksumVar));
      exprs.add(
          ErlMatch.match(
              ErlVarPattern.varPattern(next),
              ErlCallLocal.callLocal(
                  "headers_set",
                  ErlBinary.binary(cb.headerName()),
                  ErlCallLocal.callLocal("checksum_header_encode", ErlVar.var(checksumVar)),
                  ErlVar.var(current))));
      current = next;
    }
    return Optional.of(ErlExprBlock.block(exprs.toArray(ErlExpr[]::new)));
  }

  static ErlExpr responseChecksumGuardExpr(Model model, OperationShape op, ErlExpr successExpr) {
    BeamHttpChecksumIndex checksumIndex = BeamHttpChecksumIndex.of(model);
    List<BeamHttpChecksumIndex.ChecksumBinding> bindings = checksumIndex.responseChecksums(op);
    if (bindings.isEmpty()) {
      return successExpr;
    }

    List<ErlExpr> headerNames = new ArrayList<>();
    for (BeamHttpChecksumIndex.ChecksumBinding binding : bindings) {
      headerNames.add(ErlBinary.binary(binding.headerName()));
    }
    return ErlCase.caseExpr(
        ErlCallLocal.callLocal(
            "validate_response_checksum",
            ErlVar.var("Body"),
            ErlVar.var("Headers"),
            ErlList.list(headerNames.toArray(ErlExpr[]::new))),
        ErlClause.clause(List.of(ErlAtomPattern.atomPattern("ok")), successExpr),
        ErlClause.clause(
            List.of(
                ErlTuplePattern.tuplePattern(
                    ErlAtomPattern.atomPattern("error"), ErlVarPattern.varPattern("Reason"))),
            ErlTuple.tuple(
                ErlAtom.atom("error"),
                ErlTuple.tuple(ErlAtom.atom("checksum_validation_failed"), ErlVar.var("Reason")))));
  }

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
        List.of(
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Data")),
                List.of(ErlGuard.guard("is_binary", ErlVar.var("Data"))),
                ErlCall.call("base64", "encode", ErlVar.var("Data")))));
  }

  private static ErlFunction md5Hash() {
    return ErlFunction.function(
        "md5_hash",
        1,
        List.of(
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Body")),
                ErlCall.call("crypto", "hash", ErlAtom.atom("md5"), ErlVar.var("Body")))));
  }

  private static ErlFunction sha256Hash() {
    return ErlFunction.function(
        "sha256_hash",
        1,
        List.of(
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Body")),
                ErlCall.call("crypto", "hash", ErlAtom.atom("sha256"), ErlVar.var("Body")))));
  }

  private static ErlFunction crc32Hash() {
    return ErlFunction.function(
        "crc32_hash",
        1,
        List.of(
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Body")),
                ErlBinaryTemplate.binaryTemplate(
                    ErlBinaryExpr.expr(
                        ErlCall.call("erlang", "crc32", ErlVar.var("Body")),
                        "32/big-unsigned-integer")))));
  }

  private static ErlFunction crc32cHash() {
    return ErlFunction.function(
        "crc32c_hash",
        1,
        List.of(
            ErlClause.clause(
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
        List.of(
            ErlClause.clause(
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
                List.of(ErlVarPattern.varPattern("Body"), ErlBinaryPattern.binaryPattern("MD5")),
                ErlCallLocal.callLocal("md5_hash", ErlVar.var("Body"))),
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Body"), ErlBinaryPattern.binaryPattern("SHA256")),
                ErlCallLocal.callLocal("sha256_hash", ErlVar.var("Body"))),
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Body"), ErlBinaryPattern.binaryPattern("CRC32")),
                ErlCallLocal.callLocal("crc32_hash", ErlVar.var("Body"))),
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Body"), ErlBinaryPattern.binaryPattern("CRC32C")),
                ErlCallLocal.callLocal("crc32c_hash", ErlVar.var("Body")))));
  }

  private static ErlFunction validateResponseChecksum() {
    ErlCase checksumMatch =
        ErlCase.caseExpr(
            ErlOp.op(
                "=:=",
                ErlCallLocal.callLocal(
                    "checksum_header_encode",
                    ErlCallLocal.callLocal(
                        "checksum_digest",
                        ErlVar.var("Body"),
                        ErlCallLocal.callLocal(
                            "checksum_algorithm_from_header", ErlVar.var("HeaderName")))),
                ErlVar.var("Expected")),
            ErlClause.clause(List.of(ErlAtomPattern.atomPattern("true")), ErlAtom.atom("ok")),
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("false")),
                ErlTuple.tuple(
                    ErlAtom.atom("error"),
                    ErlTuple.tuple(ErlAtom.atom("checksum_mismatch"), ErlVar.var("HeaderName")))));

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
                        ErlVarPattern.varPattern("HeaderName"), ErlVarPattern.varPattern("Rest"))),
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
                        List.of(ErlVarPattern.varPattern("Expected")), checksumMatch)))));
  }

  private static ErlFunction checksumAlgorithmFromHeader() {
    return ErlFunction.function(
        "checksum_algorithm_from_header",
        1,
        List.of(
            ErlClause.clause(
                List.of(ErlBinPattern.binPattern("\"x-amz-checksum-\", Rest/binary")),
                ErlCallLocal.callLocal(
                    "list_to_binary",
                    ErlCall.call(
                        "string",
                        "uppercase",
                        ErlCallLocal.callLocal("binary_to_list", ErlVar.var("Rest")))))));
  }

  private static ErlExpr checksumBranchExpr(
      BeamHttpChecksumIndex.ChecksumBinding cb, String headersVar) {
    return ErlExprBlock.block(
        checksumComputationExpr(cb, "Checksum"),
        ErlCallLocal.callLocal(
            "headers_set",
            ErlBinary.binary(cb.headerName()),
            ErlCallLocal.callLocal("checksum_header_encode", ErlVar.var("Checksum")),
            ErlVar.var(headersVar)));
  }

  private static ErlExpr checksumComputationExpr(
      BeamHttpChecksumIndex.ChecksumBinding cb, String checksumVar) {
    if (cb.usesCryptoHash()) {
      return ErlMatch.match(
          ErlVarPattern.varPattern(checksumVar),
          ErlCall.call(
              "crypto", "hash", ErlAtom.atom(cb.algorithmErlangAtom()), ErlVar.var("Body")));
    }
    return ErlMatch.match(
        ErlVarPattern.varPattern(checksumVar),
        ErlCallLocal.callLocal(cb.hashHelperName(), ErlVar.var("Body")));
  }

  @SuppressWarnings("unchecked")
  private static String enumAtomForAlgorithm(
      Model model,
      OperationShape op,
      SymbolProvider sp,
      BeamHttpChecksumIndex checksumIndex,
      String algorithm) {
    String memberName = checksumIndex.requestAlgorithmMemberName(op).orElseThrow();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    MemberShape member = input.getMember(memberName).orElseThrow();
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape enumShape) {
      for (MemberShape enumMember : enumShape.members()) {
        if (enumMember.getMemberName().equalsIgnoreCase(algorithm)) {
          Symbol symbol = sp.toSymbol(target);
          Map<String, String> byMember =
              symbol.getProperty("enumAtomByMember", Map.class).orElseThrow();
          return byMember.get(enumMember.getMemberName());
        }
      }
    }
    return algorithm.toLowerCase(Locale.US);
  }
}
