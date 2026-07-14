package io.smithy.beam.elixir;

import io.beam.ir.elixir.AtomExpr;
import io.beam.ir.elixir.AtomPattern;
import io.beam.ir.elixir.BlockExpr;
import io.beam.ir.elixir.CaseExpr;
import io.beam.ir.elixir.Clause;
import io.beam.ir.elixir.DotCallExpr;
import io.beam.ir.elixir.Expression;
import io.beam.ir.elixir.ListExpr;
import io.beam.ir.elixir.LocalCallExpr;
import io.beam.ir.elixir.MatchExpr;
import io.beam.ir.elixir.NilPattern;
import io.beam.ir.elixir.RaiseExpr;
import io.beam.ir.elixir.RemoteCallExpr;
import io.beam.ir.elixir.StringExpr;
import io.beam.ir.elixir.TupleExpr;
import io.beam.ir.elixir.TuplePattern;
import io.beam.ir.elixir.Variable;
import io.beam.ir.elixir.VariablePattern;
import io.smithy.beam.core.BeamHttpChecksumIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCase;
import io.smithy.beam.ir.elixir.ExCaseBranch;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExList;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExNilPattern;
import io.smithy.beam.ir.elixir.ExString;
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
  private static final String HTTP_CHECKSUM = "HttpChecksum";

  private ElixirHttpChecksumIr() {}

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
              ExCall.call(
                  HTTP_CHECKSUM,
                  "headers_set",
                  ExString.string(cb.headerName()),
                  ExCall.call(HTTP_CHECKSUM, "checksum_header_encode", ExVar.var(checksumVar)),
                  ExVar.var(headersIn))));
    }
    return Optional.of(ExExprBlock.block(exprs.toArray(ExExpr[]::new)));
  }

  static Optional<Expression> requestChecksumHeadersStatement(
      Model model, OperationShape op, SymbolProvider sp, String headersVar) {
    BeamHttpChecksumIndex checksumIndex = BeamHttpChecksumIndex.of(model);
    List<BeamHttpChecksumIndex.ChecksumBinding> bindings = checksumIndex.requestChecksums(op);
    if (bindings.isEmpty()) {
      return Optional.empty();
    }

    Optional<String> algorithmMember = checksumIndex.requestAlgorithmMemberName(op);
    if (algorithmMember.isPresent()) {
      String field = BeamNameUtils.toSnakeCase(algorithmMember.get());
      List<Clause> branches = new ArrayList<>();
      branches.add(Clause.of(NilPattern.of(), Variable.of(headersVar)));
      for (BeamHttpChecksumIndex.ChecksumBinding binding : bindings) {
        String enumAtom = enumAtomForAlgorithm(model, op, sp, checksumIndex, binding.algorithm());
        branches.add(
            Clause.of(AtomPattern.of(enumAtom), checksumBranchStatement(binding, headersVar)));
      }
      branches.add(
          Clause.of(
              VariablePattern.of("other"),
              new RaiseExpr(
                  AtomExpr.of("ArgumentError"),
                  TupleExpr.of(
                      List.of(
                          AtomExpr.of("unsupported_checksum_algorithm"),
                          Variable.of("other"))),
                  false)));
      return Optional.of(
          MatchExpr.bind(
              VariablePattern.of(headersVar),
              new CaseExpr(
                  new DotCallExpr(Variable.of("input"), field, List.of()), branches)));
    }

    List<Expression> statements = new ArrayList<>();
    for (int i = 0; i < bindings.size(); i++) {
      BeamHttpChecksumIndex.ChecksumBinding cb = bindings.get(i);
      String checksumVar = "checksum" + i;
      statements.add(checksumComputationStatement(cb, checksumVar));
      statements.add(
          MatchExpr.bind(
              VariablePattern.of(headersVar),
              RemoteCallExpr.of(
                  HTTP_CHECKSUM,
                  "headers_set",
                  List.of(
                      StringExpr.of(cb.headerName()),
                      RemoteCallExpr.of(
                          HTTP_CHECKSUM,
                          "checksum_header_encode",
                          List.of(Variable.of(checksumVar))),
                      Variable.of(headersVar)))));
    }
    return Optional.of(new BlockExpr(statements));
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
      headerNames.add(StringExpr.of(binding.headerName()));
    }
    return new CaseExpr(
        RemoteCallExpr.of(
            HTTP_CHECKSUM,
            "validate_response_checksum",
            List.of(
                Variable.of("body"),
                Variable.of("headers"),
                ListExpr.of(headerNames))),
        List.of(
            Clause.of(AtomPattern.of("ok"), successExpr),
            Clause.of(
                TuplePattern.of(
                    List.of(AtomPattern.of("error"), VariablePattern.of("reason"))),
                TupleExpr.of(
                    List.of(
                        AtomExpr.of("error"),
                        TupleExpr.of(
                            List.of(
                                AtomExpr.of("checksum_validation_failed"),
                                Variable.of("reason"))))))));
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
        ExCall.call(
            HTTP_CHECKSUM,
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

  private static Expression checksumBranchStatement(
      BeamHttpChecksumIndex.ChecksumBinding cb, String headersVar) {
    return new BlockExpr(
        List.of(
            checksumComputationStatement(cb, "checksum"),
            RemoteCallExpr.of(
                HTTP_CHECKSUM,
                "headers_set",
                List.of(
                    StringExpr.of(cb.headerName()),
                    RemoteCallExpr.of(
                        HTTP_CHECKSUM,
                        "checksum_header_encode",
                        List.of(Variable.of("checksum"))),
                    Variable.of(headersVar)))));
  }

  private static Expression checksumComputationStatement(
      BeamHttpChecksumIndex.ChecksumBinding cb, String checksumVar) {
    if (cb.usesCryptoHash()) {
      return MatchExpr.bind(
          VariablePattern.of(checksumVar),
          RemoteCallExpr.of(
              ":crypto",
              "hash",
              List.of(AtomExpr.of(cb.algorithmErlangAtom()), Variable.of("body"))));
    }
    return MatchExpr.bind(
        VariablePattern.of(checksumVar),
        RemoteCallExpr.of(HTTP_CHECKSUM, cb.hashHelperName(), List.of(Variable.of("body"))));
  }

  private static ExExpr checksumBranchExpr(
      BeamHttpChecksumIndex.ChecksumBinding cb, String headersVar) {
    return ExExprBlock.block(
        checksumComputationExpr(cb, "checksum"),
        ExCall.call(
            HTTP_CHECKSUM,
            "headers_set",
            ExString.string(cb.headerName()),
            ExCall.call(HTTP_CHECKSUM, "checksum_header_encode", ExVar.var("checksum")),
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
        ExCall.call(HTTP_CHECKSUM, cb.hashHelperName(), ExVar.var("body")));
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
