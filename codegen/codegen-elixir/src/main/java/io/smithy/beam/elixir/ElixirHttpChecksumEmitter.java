package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamHttpChecksumIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.elixir.ExFunction;
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

final class ElixirHttpChecksumEmitter {

  private ElixirHttpChecksumEmitter() {}

  static boolean serviceHasChecksumOperations(Model model, ServiceShape service) {
    return ElixirHttpChecksumIr.serviceHasChecksumOperations(model, service);
  }

  static void emitRequestChecksumHeaders(
      ElixirWriter writer, Model model, OperationShape op, SymbolProvider sp) {
    BeamHttpChecksumIndex checksumIndex = BeamHttpChecksumIndex.of(model);
    List<BeamHttpChecksumIndex.ChecksumBinding> bindings = checksumIndex.requestChecksums(op);
    if (bindings.isEmpty()) {
      return;
    }

    Optional<String> algorithmMember = checksumIndex.requestAlgorithmMemberName(op);
    if (algorithmMember.isPresent()) {
      String field = BeamNameUtils.toSnakeCase(algorithmMember.get());
      writer.write("headers = case input.$L do", field);
      writer.indent();
      writer.write("nil -> headers");
      for (BeamHttpChecksumIndex.ChecksumBinding binding : bindings) {
        String enumAtom = enumAtomForAlgorithm(model, op, sp, checksumIndex, binding.algorithm());
        writer.write(":$L ->", enumAtom);
        writer.indent();
        emitChecksumBranch(writer, binding, "headers", "body");
        writer.dedent();
      }
      writer.write("");
      writer.write("other -> raise ArgumentError, {:unsupported_checksum_algorithm, other}");
      writer.dedent();
      writer.write("end");
      return;
    }

    for (int i = 0; i < bindings.size(); i++) {
      BeamHttpChecksumIndex.ChecksumBinding cb = bindings.get(i);
      String checksumVar = "checksum" + i;
      emitChecksumComputation(writer, cb, checksumVar, "body");
      writer.write(
          "headers = headers_set(\"$L\", checksum_header_encode($L), headers)",
          cb.headerName(),
          checksumVar);
    }
  }

  static void emitResponseChecksumGuard(
      ElixirWriter writer, Model model, OperationShape op, String successExpression) {
    BeamHttpChecksumIndex checksumIndex = BeamHttpChecksumIndex.of(model);
    List<BeamHttpChecksumIndex.ChecksumBinding> bindings = checksumIndex.responseChecksums(op);
    if (bindings.isEmpty()) {
      writer.write(successExpression);
      return;
    }

    List<String> headerNames = new ArrayList<>();
    for (BeamHttpChecksumIndex.ChecksumBinding binding : bindings) {
      headerNames.add("\"" + binding.headerName() + "\"");
    }
    writer.write(
        "case validate_response_checksum(body, headers, [$L]) do", String.join(", ", headerNames));
    writer.indent();
    writer.write(":ok -> $L", successExpression);
    writer.write("");
    writer.write("{:error, reason} -> {:error, {:checksum_validation_failed, reason}}");
    writer.dedent();
    writer.write("end");
  }

  static void emitChecksumHelpers(ElixirWriter writer) {
    for (ExFunction function : ElixirHttpChecksumIr.checksumHelperFunctions()) {
      writer.write("$L", function.asString());
      writer.write("");
    }
  }

  private static void emitChecksumBranch(
      ElixirWriter writer,
      BeamHttpChecksumIndex.ChecksumBinding cb,
      String headersVar,
      String bodyVar) {
    String checksumVar = "checksum";
    emitChecksumComputation(writer, cb, checksumVar, bodyVar);
    writer.write(
        "$L = headers_set(\"$L\", checksum_header_encode($L), $L)",
        headersVar,
        cb.headerName(),
        checksumVar,
        headersVar);
  }

  private static void emitChecksumComputation(
      ElixirWriter writer,
      BeamHttpChecksumIndex.ChecksumBinding cb,
      String checksumVar,
      String bodyVar) {
    if (cb.usesCryptoHash()) {
      writer.write("$L = :crypto.hash(:$L, $L)", checksumVar, cb.algorithmErlangAtom(), bodyVar);
    } else {
      writer.write("$L = $L($L)", checksumVar, cb.hashHelperName(), bodyVar);
    }
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
