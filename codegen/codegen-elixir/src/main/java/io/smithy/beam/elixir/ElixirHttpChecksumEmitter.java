package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamHttpChecksumIndex;
import io.smithy.beam.core.BeamNameUtils;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class ElixirHttpChecksumEmitter {

    private ElixirHttpChecksumEmitter() {}

    static boolean serviceHasChecksumOperations(Model model, ServiceShape service) {
        BeamHttpChecksumIndex index = BeamHttpChecksumIndex.of(model);
        return ElixirTopDown.containedOperationsSorted(model, service).stream()
                .anyMatch(index::hasChecksumBehavior);
    }

    static void emitRequestChecksumHeaders(
            ElixirWriter writer,
            Model model,
            OperationShape op,
            SymbolProvider sp) {
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
            writer.write("nil ->");
            writer.indent();
            emitChecksumBranch(writer, bindings.get(0), "headers", "body");
            writer.dedent();
            for (BeamHttpChecksumIndex.ChecksumBinding binding : bindings) {
                String enumAtom = enumAtomForAlgorithm(model, op, sp, checksumIndex, binding.algorithm());
                writer.write(":$L ->", enumAtom);
                writer.indent();
                emitChecksumBranch(writer, binding, "headers", "body");
                writer.dedent();
            }
            writer.write("other -> raise ArgumentError, {:unsupported_checksum_algorithm, other}");
            writer.dedent();
            writer.write("end");
            return;
        }

        for (int i = 0; i < bindings.size(); i++) {
            BeamHttpChecksumIndex.ChecksumBinding cb = bindings.get(i);
            String checksumVar = "checksum" + i;
            emitChecksumComputation(writer, cb, checksumVar, "body");
            writer.write("headers = headers_set(\"$L\", base16_encode($L), headers)",
                    cb.headerName(), checksumVar);
        }
    }

    static void emitResponseChecksumGuard(
            ElixirWriter writer,
            Model model,
            OperationShape op,
            String successExpression) {
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
        writer.write("case validate_response_checksum(body, headers, [$L]) do", String.join(", ", headerNames));
        writer.indent();
        writer.write(":ok -> $L", successExpression);
        writer.write("{:error, reason} -> {:error, {:checksum_validation_failed, reason}}");
        writer.dedent();
        writer.write("end");
    }

    static void emitChecksumHelpers(ElixirWriter writer) {
        writer.write("defp headers_set(name, value, headers) do");
        writer.indent();
        writer.write("List.keystore(name, 0, headers, {name, value})");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp base16_encode(data) when is_binary(data) do");
        writer.indent();
        writer.write("Base.encode16(data, case: :lower)");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp md5_hash(body), do: :crypto.hash(:md5, body)");
        writer.write("defp sha256_hash(body), do: :crypto.hash(:sha256, body)");
        writer.write("defp crc32_hash(body), do: <<(:erlang.crc32(body)::32-big-unsigned-integer)>>");
        writer.write("defp crc32c_hash(body), do: :crypto.hash(:crc32c, body)");
        writer.write("");
        writer.write("defp checksum_digest(body, \"MD5\"), do: md5_hash(body)");
        writer.write("defp checksum_digest(body, \"SHA256\"), do: sha256_hash(body)");
        writer.write("defp checksum_digest(body, \"CRC32\"), do: crc32_hash(body)");
        writer.write("defp checksum_digest(body, \"CRC32C\"), do: crc32c_hash(body)");
        writer.write("");
        writer.write("defp validate_response_checksum(_body, _headers, []), do: :ok");
        writer.write("defp validate_response_checksum(body, headers, [header_name | rest]) do");
        writer.indent();
        writer.write("case List.keyfind(headers, header_name, 0) do");
        writer.indent();
        writer.write("{_, expected} ->");
        writer.indent();
        writer.write("algorithm = checksum_algorithm_from_header(header_name),");
        writer.write("computed = base16_encode(checksum_digest(body, algorithm)),");
        writer.write("if computed == expected, do: :ok, else: {:error, {:checksum_mismatch, header_name}}");
        writer.dedent();
        writer.write("nil -> validate_response_checksum(body, headers, rest)");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
        writer.write("");
        writer.write("defp checksum_algorithm_from_header(\"x-amz-checksum-\" <> rest),");
        writer.write("    do: String.upcase(rest)");
    }

    private static void emitChecksumBranch(
            ElixirWriter writer,
            BeamHttpChecksumIndex.ChecksumBinding cb,
            String headersVar,
            String bodyVar) {
        String checksumVar = "checksum";
        emitChecksumComputation(writer, cb, checksumVar, bodyVar);
        writer.write("$L = headers_set(\"$L\", base16_encode($L), $L)",
                headersVar, cb.headerName(), checksumVar, headersVar);
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
                    Map<String, String> byMember = symbol.getProperty("enumAtomByMember", Map.class)
                            .orElseThrow();
                    return byMember.get(enumMember.getMemberName());
                }
            }
        }
        return algorithm.toLowerCase(Locale.US);
    }
}
