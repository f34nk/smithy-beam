package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.BinaryPattern;
import io.beam.ir.erlang.BinarySegmentExpr;
import io.beam.ir.erlang.BinarySegmentPattern;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.CaseExpr;
import io.beam.ir.erlang.Clause;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.InfixExpr;
import io.beam.ir.erlang.IsTypeGuard;
import io.beam.ir.erlang.ListExpr;
import io.beam.ir.erlang.ListPattern;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.beam.ir.erlang.WildcardPattern;
import io.smithy.beam.core.BeamHttpChecksumIndex;
import io.smithy.beam.core.BeamNameUtils;
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

  static Optional<Expression> requestChecksumHeadersExpr(
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
      List<Clause> clauses = new ArrayList<>();
      clauses.add(Clause.of(AtomPattern.of("undefined"), Variable.of(headersIn)));
      for (BeamHttpChecksumIndex.ChecksumBinding binding : bindings) {
        String enumAtom = enumAtomForAlgorithm(model, op, sp, checksumIndex, binding.algorithm());
        clauses.add(Clause.of(AtomPattern.of(enumAtom), checksumBranchExpr(binding, headersIn)));
      }
      clauses.add(
          Clause.of(
              VariablePattern.of("Other"),
              LocalCallExpr.of(
                  "error",
                  List.of(
                      TupleExpr.of(
                          List.of(
                              AtomExpr.of("unsupported_checksum_algorithm"),
                              Variable.of("Other")))))));
      return Optional.of(
          MatchExpr.bindValue(
              headersOut, CaseExpr.of(Variable.of(bindingVar), clauses)));
    }

    List<Expression> exprs = new ArrayList<>();
    String current = headersIn;
    for (int i = 0; i < bindings.size(); i++) {
      BeamHttpChecksumIndex.ChecksumBinding cb = bindings.get(i);
      String checksumVar = "Checksum" + i;
      String next = i == bindings.size() - 1 ? headersOut : headersIn + "Checksum" + i;
      exprs.add(checksumComputationExpr(cb, checksumVar));
      exprs.add(
          MatchExpr.bindValue(
              next,
              LocalCallExpr.of(
                  "headers_set",
                  List.of(
                      BinaryExpr.of(cb.headerName()),
                      LocalCallExpr.of(
                          "checksum_header_encode", List.of(Variable.of(checksumVar))),
                      Variable.of(current)))));
      current = next;
    }
    return Optional.of(BlockExpr.newlineSeparated(exprs, true));
  }

  static Expression responseChecksumGuardExpr(
      Model model, OperationShape op, Expression successExpr) {
    BeamHttpChecksumIndex checksumIndex = BeamHttpChecksumIndex.of(model);
    List<BeamHttpChecksumIndex.ChecksumBinding> bindings = checksumIndex.responseChecksums(op);
    if (bindings.isEmpty()) {
      return successExpr;
    }

    List<Expression> headerNames = new ArrayList<>();
    for (BeamHttpChecksumIndex.ChecksumBinding binding : bindings) {
      headerNames.add(BinaryExpr.of(binding.headerName()));
    }
    return CaseExpr.of(
        LocalCallExpr.of(
            "validate_response_checksum",
            List.of(
                Variable.of("Body"),
                Variable.of("Headers"),
                ListExpr.of(headerNames))),
        List.of(
            Clause.of(AtomPattern.of("ok"), successExpr),
            Clause.of(
                TuplePattern.of(
                    List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("error"),
                        TupleExpr.of(
                            List.of(
                                AtomExpr.of("checksum_validation_failed"),
                                Variable.of("Reason"))))))));
  }

  static List<Function> checksumHelperFunctions() {
    List<Function> functions = new ArrayList<>();
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

  private static Function checksumHeaderEncode() {
    return Function.of(
        "checksum_header_encode",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Data")),
                IsTypeGuard.of("binary", Variable.of("Data")),
                RemoteCallExpr.of("base64", "encode", List.of(Variable.of("Data"))))));
  }

  private static Function md5Hash() {
    return Function.of(
        "md5_hash",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Body")),
                RemoteCallExpr.of(
                    "crypto", "hash", List.of(AtomExpr.of("md5"), Variable.of("Body"))))));
  }

  private static Function sha256Hash() {
    return Function.of(
        "sha256_hash",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Body")),
                RemoteCallExpr.of(
                    "crypto", "hash", List.of(AtomExpr.of("sha256"), Variable.of("Body"))))));
  }

  private static Function crc32Hash() {
    return Function.of(
        "crc32_hash",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Body")),
                BinaryExpr.of(
                    List.of(
                        BinarySegmentExpr.of(
                            RemoteCallExpr.of("erlang", "crc32", List.of(Variable.of("Body"))),
                            "32/big-unsigned-integer"))))));
  }

  private static Function crc32cHash() {
    return Function.of(
        "crc32c_hash",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Body")),
                RemoteCallExpr.of(
                    "crypto", "hash", List.of(AtomExpr.of("crc32c"), Variable.of("Body"))))));
  }

  private static Function crc64nvmeHash() {
    return stubHashFunction("crc64nvme_hash", "crc64nvme");
  }

  private static Function xxhash64Hash() {
    return stubHashFunction("xxhash64_hash", "xxhash64");
  }

  private static Function xxhash3Hash() {
    return stubHashFunction("xxhash3_hash", "xxhash3");
  }

  private static Function xxhash128Hash() {
    return stubHashFunction("xxhash128_hash", "xxhash128");
  }

  private static Function stubHashFunction(String name, String algorithm) {
    return Function.of(
        name,
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("_Body")),
                LocalCallExpr.of(
                    "error",
                    List.of(
                        TupleExpr.of(
                            List.of(
                                AtomExpr.of("unsupported_checksum_algorithm"),
                                AtomExpr.of(algorithm))))))));
  }

  private static Function checksumDigest() {
    return Function.of(
        "checksum_digest",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Body"), BinaryPattern.of("MD5")),
                LocalCallExpr.of("md5_hash", List.of(Variable.of("Body")))),
            FunctionClause.of(
                List.of(VariablePattern.of("Body"), BinaryPattern.of("SHA256")),
                LocalCallExpr.of("sha256_hash", List.of(Variable.of("Body")))),
            FunctionClause.of(
                List.of(VariablePattern.of("Body"), BinaryPattern.of("CRC32")),
                LocalCallExpr.of("crc32_hash", List.of(Variable.of("Body")))),
            FunctionClause.of(
                List.of(VariablePattern.of("Body"), BinaryPattern.of("CRC32C")),
                LocalCallExpr.of("crc32c_hash", List.of(Variable.of("Body"))))));
  }

  private static Function validateResponseChecksum() {
    CaseExpr checksumMatch =
        CaseExpr.of(
            InfixExpr.of(
                LocalCallExpr.of(
                    "checksum_header_encode",
                    List.of(
                        LocalCallExpr.of(
                            "checksum_digest",
                            List.of(
                                Variable.of("Body"),
                                LocalCallExpr.of(
                                    "checksum_algorithm_from_header",
                                    List.of(Variable.of("HeaderName"))))))),
                "=:=",
                Variable.of("Expected")),
            List.of(
                Clause.of(AtomPattern.of("true"), AtomExpr.of("ok")),
                Clause.of(
                    VariablePattern.of("false"),
                    TupleExpr.of(
                        List.of(
                            AtomExpr.of("error"),
                            TupleExpr.of(
                                List.of(
                                    AtomExpr.of("checksum_mismatch"),
                                    Variable.of("HeaderName"))))))));

    return Function.of(
        "validate_response_checksum",
        List.of(
            FunctionClause.of(
                List.of(
                    VariablePattern.of("_Body"),
                    VariablePattern.of("_Headers"),
                    ListPattern.of(List.of())),
                AtomExpr.of("ok")),
            FunctionClause.of(
                List.of(
                    VariablePattern.of("Body"),
                    VariablePattern.of("Headers"),
                    ListPattern.cons(
                        VariablePattern.of("HeaderName"), VariablePattern.of("Rest"))),
                CaseExpr.of(
                    RemoteCallExpr.of(
                        "proplists",
                        "get_value",
                        List.of(
                            Variable.of("HeaderName"),
                            Variable.of("Headers"),
                            AtomExpr.of("undefined"))),
                    List.of(
                        Clause.of(
                            AtomPattern.of("undefined"),
                            LocalCallExpr.of(
                                "validate_response_checksum",
                                List.of(
                                    Variable.of("Body"),
                                    Variable.of("Headers"),
                                    Variable.of("Rest")))),
                        Clause.of(VariablePattern.of("Expected"), checksumMatch))))));
  }

  private static Function checksumAlgorithmFromHeader() {
    return Function.of(
        "checksum_algorithm_from_header",
        List.of(
            FunctionClause.of(
                List.of(
                    BinaryPattern.of(
                        List.of(
                            BinarySegmentPattern.literal("x-amz-checksum-"),
                            BinarySegmentPattern.of(VariablePattern.of("Rest"), "binary")))),
                LocalCallExpr.of(
                    "list_to_binary",
                    List.of(
                        RemoteCallExpr.of(
                            "string",
                            "uppercase",
                            List.of(
                                LocalCallExpr.of(
                                    "binary_to_list", List.of(Variable.of("Rest"))))))))));
  }

  private static Expression checksumBranchExpr(
      BeamHttpChecksumIndex.ChecksumBinding cb, String headersVar) {
    return BlockExpr.newlineSeparated(
        List.of(
            checksumComputationExpr(cb, "Checksum"),
            LocalCallExpr.of(
                "headers_set",
                List.of(
                    BinaryExpr.of(cb.headerName()),
                    LocalCallExpr.of(
                        "checksum_header_encode", List.of(Variable.of("Checksum"))),
                    Variable.of(headersVar)))),
        true);
  }

  private static Expression checksumComputationExpr(
      BeamHttpChecksumIndex.ChecksumBinding cb, String checksumVar) {
    if (cb.usesCryptoHash()) {
      return MatchExpr.bindValue(
          checksumVar,
          RemoteCallExpr.of(
              "crypto",
              "hash",
              List.of(AtomExpr.of(cb.algorithmErlangAtom()), Variable.of("Body"))));
    }
    return MatchExpr.bindValue(
        checksumVar, LocalCallExpr.of(cb.hashHelperName(), List.of(Variable.of("Body"))));
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
