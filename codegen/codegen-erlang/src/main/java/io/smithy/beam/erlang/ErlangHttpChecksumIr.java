package io.smithy.beam.erlang;

import io.beam.dsl.erlang.AtomExpr;
import io.beam.dsl.erlang.AtomPattern;
import io.beam.dsl.erlang.BinaryExpr;
import io.beam.dsl.erlang.BlockExpr;
import io.beam.dsl.erlang.CaseExpr;
import io.beam.dsl.erlang.Clause;
import io.beam.dsl.erlang.Expression;
import io.beam.dsl.erlang.IntegerExpr;
import io.beam.dsl.erlang.ListExpr;
import io.beam.dsl.erlang.LocalCallExpr;
import io.beam.dsl.erlang.MatchExpr;
import io.beam.dsl.erlang.RemoteCallExpr;
import io.beam.dsl.erlang.TupleExpr;
import io.beam.dsl.erlang.TuplePattern;
import io.beam.dsl.erlang.Variable;
import io.beam.dsl.erlang.VariablePattern;
import io.smithy.beam.core.BeamHttpChecksumIndex;
import io.smithy.beam.core.BeamNameUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private static final String HTTP_CHECKSUM_MOD = "http_checksum";
  private static final Set<String> HTTP_CHECKSUM_HASH_HELPERS =
      Set.of("md5_hash", "sha256_hash", "crc32_hash", "crc32c_hash");

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
          MatchExpr.bindValue(headersOut, CaseExpr.of(Variable.of(bindingVar), clauses)));
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
              headersSetExpr(
                  BinaryExpr.of(cb.headerName()),
                  RemoteCallExpr.of(
                      HTTP_CHECKSUM_MOD,
                      "checksum_header_encode",
                      List.of(Variable.of(checksumVar))),
                  Variable.of(current))));
      current = next;
    }
    return Optional.of(BlockExpr.commaSeparated(exprs, false));
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
        RemoteCallExpr.of(
            HTTP_CHECKSUM_MOD,
            "validate_response_checksum",
            List.of(Variable.of("Body"), Variable.of("Headers"), ListExpr.of(headerNames))),
        List.of(
            Clause.of(AtomPattern.of("ok"), successExpr),
            Clause.of(
                TuplePattern.of(List.of(AtomPattern.of("error"), VariablePattern.of("Reason"))),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("error"),
                        TupleExpr.of(
                            List.of(
                                AtomExpr.of("checksum_validation_failed"),
                                Variable.of("Reason"))))))));
  }

  private static Expression checksumBranchExpr(
      BeamHttpChecksumIndex.ChecksumBinding cb, String headersVar) {
    return BlockExpr.commaSeparated(
        List.of(
            checksumComputationExpr(cb, "Checksum"),
            headersSetExpr(
                BinaryExpr.of(cb.headerName()),
                RemoteCallExpr.of(
                    HTTP_CHECKSUM_MOD, "checksum_header_encode", List.of(Variable.of("Checksum"))),
                Variable.of(headersVar))),
        false);
  }

  private static Expression headersSetExpr(Expression name, Expression value, Expression headers) {
    return RemoteCallExpr.of(
        "lists",
        "keystore",
        List.of(name, IntegerExpr.of(1), headers, TupleExpr.of(List.of(name, value))));
  }

  private static Expression checksumComputationExpr(
      BeamHttpChecksumIndex.ChecksumBinding cb, String checksumVar) {
    Expression hashExpr;
    if (cb.usesCryptoHash()) {
      String helper = cb.hashHelperName();
      if (HTTP_CHECKSUM_HASH_HELPERS.contains(helper)) {
        hashExpr = RemoteCallExpr.of(HTTP_CHECKSUM_MOD, helper, List.of(Variable.of("Body")));
      } else {
        hashExpr =
            RemoteCallExpr.of(
                "crypto",
                "hash",
                List.of(AtomExpr.of(cb.algorithmErlangAtom()), Variable.of("Body")));
      }
    } else {
      hashExpr =
          RemoteCallExpr.of(HTTP_CHECKSUM_MOD, cb.hashHelperName(), List.of(Variable.of("Body")));
    }
    return MatchExpr.bindValue(checksumVar, hashExpr);
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
