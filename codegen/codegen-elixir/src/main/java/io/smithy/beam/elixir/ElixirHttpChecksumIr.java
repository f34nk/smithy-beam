package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamHttpChecksumIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExBinaryTemplate;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExIf;
import io.smithy.beam.ir.elixir.ExInteger;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExListPattern;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExPipeCase;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStringPattern;
import io.smithy.beam.ir.elixir.ExStructAccess;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
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

final class ElixirHttpChecksumIr {
  private ElixirHttpChecksumIr() {}

  private static final ExVarPattern W = ExVarPattern.var("_");

  static boolean serviceHasChecksumOperations(Model model, ServiceShape service) {
    BeamHttpChecksumIndex index = BeamHttpChecksumIndex.of(model);
    return ElixirTopDown.containedOperationsSorted(model, service).stream()
        .anyMatch(index::hasChecksumBehavior);
  }

  static Optional<ExExpr> requestChecksumHeadersExpr(
      Model model, OperationShape op, SymbolProvider sp, String headersIn) {
    BeamHttpChecksumIndex checksumIndex = BeamHttpChecksumIndex.of(model);
    List<BeamHttpChecksumIndex.ChecksumBinding> bindings = checksumIndex.requestChecksums(op);
    if (bindings.isEmpty()) {
      return Optional.empty();
    }

    Optional<String> algorithmMember = checksumIndex.requestAlgorithmMemberName(op);
    if (algorithmMember.isPresent()) {
      String field = BeamNameUtils.toSnakeCase(algorithmMember.get());
      List<ExCaseBranch> branches = new ArrayList<>();
      branches.add(ExCaseBranch.branch(ExNilPattern.nil(), ExVar.var(headersIn)));
      for (BeamHttpChecksumIndex.ChecksumBinding binding : bindings) {
        String enumAtom = enumAtomForAlgorithm(model, op, sp, checksumIndex, binding.algorithm());
        branches.add(
            ExCaseBranch.branch(
                ExAtomPattern.atom(enumAtom), checksumBranchExpr(binding, headersIn)));
      }
      branches.add(
          ExCaseBranch.branch(
              ExVarPattern.var("other"),
              ExCall.call(
                  "Kernel",
                  "raise",
                  ExAtom.atom("ArgumentError"),
                  ExTuple.tuple(
                      ExAtom.atom("unsupported_checksum_algorithm"), ExVar.var("other")))));
      return Optional.of(
          ExMatch.match(
              ExVarPattern.var(headersIn),
              ExCase.caseExpr(
                  ExStructAccess.structAccess(ExVar.var("input"), field),
                  branches.toArray(ExCaseBranch[]::new))));
    }

    List<ExExpr> exprs = new ArrayList<>();
    for (int i = 0; i < bindings.size(); i++) {
      BeamHttpChecksumIndex.ChecksumBinding cb = bindings.get(i);
      String checksumVar = "checksum" + i;
      exprs.add(checksumComputationExpr(cb, checksumVar));
      exprs.add(
          ExMatch.match(
              ExVarPattern.var(headersIn),
              ExCallLocal.callLocal(
                  "headers_set",
                  ExString.string(cb.headerName()),
                  ExCallLocal.callLocal("checksum_header_encode", ExVar.var(checksumVar)),
                  ExVar.var(headersIn))));
    }
    return Optional.of(ExExprBlock.block(exprs.toArray(ExExpr[]::new)));
  }

  static ExExpr responseChecksumGuardExpr(Model model, OperationShape op, ExExpr successExpr) {
    BeamHttpChecksumIndex checksumIndex = BeamHttpChecksumIndex.of(model);
    List<BeamHttpChecksumIndex.ChecksumBinding> bindings = checksumIndex.responseChecksums(op);
    if (bindings.isEmpty()) {
      return successExpr;
    }

    List<ExExpr> headerNames = new ArrayList<>();
    for (BeamHttpChecksumIndex.ChecksumBinding binding : bindings) {
      headerNames.add(ExString.string(binding.headerName()));
    }
    return ExCase.caseExpr(
        ExCallLocal.callLocal(
            "validate_response_checksum",
            ExVar.var("body"),
            ExVar.var("headers"),
            ExList.list(headerNames.toArray(ExExpr[]::new))),
        ExCaseBranch.branch(ExAtomPattern.atom("ok"), successExpr),
        ExCaseBranch.branch(
            ExTuplePattern.tuple(ExAtomPattern.atom("error"), ExVarPattern.var("reason")),
            ExTuple.tuple(
                ExAtom.atom("error"),
                ExTuple.tuple(ExAtom.atom("checksum_validation_failed"), ExVar.var("reason")))));
  }

  static List<ExFunction> checksumHelperFunctions() {
    return List.of(
        ElixirCodecHelperIr.headersSet(),
        checksumHeaderEncode(),
        md5Hash(),
        sha256Hash(),
        crc32Hash(),
        crc32cHash(),
        crc64nvmeHash(),
        xxhash64Hash(),
        xxhash3Hash(),
        xxhash128Hash(),
        checksumDigest(),
        validateResponseChecksum(),
        validateChecksumMatch(),
        checksumAlgorithmFromHeader());
  }

  private static ExFunction checksumHeaderEncode() {
    return ExFunction.defpFunction(
        "checksum_header_encode",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("data")),
                List.of(ExGuard.guard("is_binary", ExVar.var("data"))),
                ExCall.call("Base", "encode64", ExVar.var("data")))));
  }

  private static ExFunction md5Hash() {
    return ExFunction.defpFunction(
        "md5_hash",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("body")),
                ExCall.call(":crypto", "hash", ExAtom.atom("md5"), ExVar.var("body")))));
  }

  private static ExFunction sha256Hash() {
    return ExFunction.defpFunction(
        "sha256_hash",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("body")),
                ExCall.call(":crypto", "hash", ExAtom.atom("sha256"), ExVar.var("body")))));
  }

  private static ExFunction crc32Hash() {
    return ExFunction.defpFunction(
        "crc32_hash",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("body")),
                ExCall.call(
                    ":binary",
                    "encode_unsigned",
                    ExCall.call(":erlang", "crc32", ExVar.var("body")),
                    ExAtom.atom("big")))));
  }

  private static ExFunction crc32cHash() {
    return ExFunction.defpFunction(
        "crc32c_hash",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("body")),
                ExCall.call(":crypto", "hash", ExAtom.atom("crc32c"), ExVar.var("body")))));
  }

  private static ExFunction crc64nvmeHash() {
    return stubHashFunction("crc64nvme_hash", "crc64nvme");
  }

  private static ExFunction xxhash64Hash() {
    return stubHashFunction("xxhash64_hash", "xxhash64");
  }

  private static ExFunction xxhash3Hash() {
    return stubHashFunction("xxhash3_hash", "xxhash3");
  }

  private static ExFunction xxhash128Hash() {
    return stubHashFunction("xxhash128_hash", "xxhash128");
  }

  private static ExFunction stubHashFunction(String name, String algorithm) {
    return ExFunction.defpFunction(
        name,
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("_body")),
                ExCall.call(
                    "Kernel",
                    "raise",
                    ExAtom.atom("ArgumentError"),
                    ExTuple.tuple(
                        ExAtom.atom("unsupported_checksum_algorithm"), ExAtom.atom(algorithm))))));
  }

  private static ExFunction checksumDigest() {
    return ExFunction.defpFunction(
        "checksum_digest",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("body"), ExStringPattern.string("MD5")),
                ExCallLocal.callLocal("md5_hash", ExVar.var("body"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("body"), ExStringPattern.string("SHA256")),
                ExCallLocal.callLocal("sha256_hash", ExVar.var("body"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("body"), ExStringPattern.string("CRC32")),
                ExCallLocal.callLocal("crc32_hash", ExVar.var("body"))),
            ExClause.inlineClause(
                List.of(ExVarPattern.var("body"), ExStringPattern.string("CRC32C")),
                ExCallLocal.callLocal("crc32c_hash", ExVar.var("body")))));
  }

  private static ExFunction validateResponseChecksum() {
    return ExFunction.defpFunction(
        "validate_response_checksum",
        List.of(
            ExClause.inlineClause(
                List.of(
                    ExVarPattern.var("_body"), ExVarPattern.var("_headers"), ExListPattern.list()),
                ExAtom.atom("ok")),
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("body"),
                    ExVarPattern.var("headers"),
                    ExListPattern.cons(ExVarPattern.var("header_name"), ExVarPattern.var("rest"))),
                ExPipeCase.pipeCase(
                    ExCall.call(
                        "List",
                        "keyfind",
                        ExVar.var("headers"),
                        ExVar.var("header_name"),
                        ExInteger.integer(0)),
                    ExCaseBranch.branch(
                        ExTuplePattern.tuple(W, ExVarPattern.var("expected")),
                        ExCallLocal.callLocal(
                            "validate_checksum_match",
                            ExVar.var("body"),
                            ExVar.var("header_name"),
                            ExVar.var("expected"))),
                    ExCaseBranch.branch(
                        ExNilPattern.nil(),
                        ExCallLocal.callLocal(
                            "validate_response_checksum",
                            ExVar.var("body"),
                            ExVar.var("headers"),
                            ExVar.var("rest")))))));
  }

  private static ExFunction validateChecksumMatch() {
    return ExFunction.defpFunction(
        "validate_checksum_match",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExVarPattern.var("body"),
                    ExVarPattern.var("header_name"),
                    ExVarPattern.var("expected")),
                ExMatch.match(
                    ExVarPattern.var("algorithm"),
                    ExCallLocal.callLocal(
                        "checksum_algorithm_from_header", ExVar.var("header_name"))),
                ExMatch.match(
                    ExVarPattern.var("computed"),
                    ExCallLocal.callLocal(
                        "checksum_header_encode",
                        ExCallLocal.callLocal(
                            "checksum_digest", ExVar.var("body"), ExVar.var("algorithm")))),
                ExIf.ifExpr(
                    ExOp.op("==", ExVar.var("computed"), ExVar.var("expected")),
                    ExAtom.atom("ok"),
                    ExTuple.tuple(
                        ExAtom.atom("error"),
                        ExTuple.tuple(
                            ExAtom.atom("checksum_mismatch"), ExVar.var("header_name")))))));
  }

  private static ExFunction checksumAlgorithmFromHeader() {
    return ExFunction.defpFunction(
        "checksum_algorithm_from_header",
        List.of(
            ExClause.inlineClause(
                List.of(ExVarPattern.var("header_name")),
                ExCall.call(
                    "String",
                    "upcase",
                    ExCall.call(
                        "String",
                        "replace_prefix",
                        ExVar.var("header_name"),
                        ExString.string("x-amz-checksum-"),
                        ExString.string(""))))));
  }

  private static ExExpr checksumBranchExpr(
      BeamHttpChecksumIndex.ChecksumBinding cb, String headersVar) {
    return ExExprBlock.block(
        checksumComputationExpr(cb, "checksum"),
        ExCallLocal.callLocal(
            "headers_set",
            ExString.string(cb.headerName()),
            ExCallLocal.callLocal("checksum_header_encode", ExVar.var("checksum")),
            ExVar.var(headersVar)));
  }

  private static ExExpr checksumComputationExpr(
      BeamHttpChecksumIndex.ChecksumBinding cb, String checksumVar) {
    if (cb.usesCryptoHash()) {
      return ExMatch.match(
          ExVarPattern.var(checksumVar),
          ExCall.call(":crypto", "hash", ExAtom.atom(cb.algorithmErlangAtom()), ExVar.var("body")));
    }
    return ExMatch.match(
        ExVarPattern.var(checksumVar),
        ExCallLocal.callLocal(cb.hashHelperName(), ExVar.var("body")));
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
