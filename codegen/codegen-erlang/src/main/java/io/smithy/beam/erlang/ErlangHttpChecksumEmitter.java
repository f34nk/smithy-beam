package io.smithy.beam.erlang;

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

/**
 * Emits HTTP checksum encode/decode helpers into REST protocol codec modules.
 */
final class ErlangHttpChecksumEmitter {

    private ErlangHttpChecksumEmitter() {}

    static boolean serviceHasChecksumOperations(Model model, ServiceShape service) {
        BeamHttpChecksumIndex index = BeamHttpChecksumIndex.of(model);
        return ErlangTopDown.containedOperationsSorted(model, service).stream()
                .anyMatch(index::hasChecksumBehavior);
    }

    static void emitRequestChecksumHeaders(
            ErlangWriter writer,
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
            String bindingVar = ErlangJsonCodecSupport.toBindingVar(
                    BeamNameUtils.toSnakeCase(algorithmMember.get()));
            writer.write("Headers = case $L of", bindingVar);
            writer.indent();
            writer.write("undefined ->");
            writer.indent();
            emitChecksumBranch(writer, bindings.get(0), "Headers");
            writer.dedent();
            for (BeamHttpChecksumIndex.ChecksumBinding binding : bindings) {
                String enumAtom = enumAtomForAlgorithm(model, op, sp, checksumIndex, binding.algorithm());
                writer.write("$L ->", enumAtom);
                writer.indent();
                emitChecksumBranch(writer, binding, "Headers");
                writer.dedent();
            }
            writer.write("Other -> error({unsupported_checksum_algorithm, Other})");
            writer.dedent();
            writer.write("end,");
            return;
        }

        for (int i = 0; i < bindings.size(); i++) {
            BeamHttpChecksumIndex.ChecksumBinding cb = bindings.get(i);
            String checksumVar = "Checksum" + i;
            emitChecksumComputation(writer, cb, checksumVar);
            writer.write("Headers = headers_set(<<\"$L\">>, base16_encode($L), Headers),",
                    cb.headerName(), checksumVar);
        }
    }

    static void emitResponseChecksumGuard(
            ErlangWriter writer,
            Model model,
            OperationShape op,
            String successExpression) {
        BeamHttpChecksumIndex checksumIndex = BeamHttpChecksumIndex.of(model);
        List<BeamHttpChecksumIndex.ChecksumBinding> bindings = checksumIndex.responseChecksums(op);
        if (bindings.isEmpty()) {
            writer.write("$L;", successExpression);
            return;
        }

        List<String> headerNames = new ArrayList<>();
        for (BeamHttpChecksumIndex.ChecksumBinding binding : bindings) {
            headerNames.add("<<\"" + binding.headerName() + "\">>");
        }
        writer.write("case validate_response_checksum(Body, Headers, [$L]) of", String.join(", ", headerNames));
        writer.indent();
        writer.write("ok -> $L;", successExpression);
        writer.write("{error, Reason} -> {error, {checksum_validation_failed, Reason}}");
        writer.dedent();
        writer.write("end");
    }

    static void emitChecksumHelpers(ErlangWriter writer) {
        writer.write("headers_set(Name, Value, Headers) ->");
        writer.write("    lists:keystore(Name, 1, Headers, {Name, Value}).");
        writer.write("");
        writer.write("base16_encode(Data) when is_binary(Data) ->");
        writer.write("    lists:flatten([io_lib:format(\"~2.16.0b0\", [B]) || <<B>> <= Data]).");
        writer.write("");
        writer.write("md5_hash(Body) ->");
        writer.write("    crypto:hash(md5, Body).");
        writer.write("");
        writer.write("sha256_hash(Body) ->");
        writer.write("    crypto:hash(sha256, Body).");
        writer.write("");
        writer.write("crc32_hash(Body) ->");
        writer.write("    <<(erlang:crc32(Body)):32/big-unsigned-integer>>.");
        writer.write("");
        writer.write("crc32c_hash(Body) ->");
        writer.write("    crypto:hash(crc32c, Body).");
        writer.write("");
        writer.write("crc64nvme_hash(_Body) ->");
        writer.write("    error({unsupported_checksum_algorithm, crc64nvme}).");
        writer.write("");
        writer.write("xxhash64_hash(_Body) ->");
        writer.write("    error({unsupported_checksum_algorithm, xxhash64}).");
        writer.write("");
        writer.write("xxhash3_hash(_Body) ->");
        writer.write("    error({unsupported_checksum_algorithm, xxhash3}).");
        writer.write("");
        writer.write("xxhash128_hash(_Body) ->");
        writer.write("    error({unsupported_checksum_algorithm, xxhash128}).");
        writer.write("");
        writer.write("checksum_digest(Body, <<\"MD5\">>) -> md5_hash(Body);");
        writer.write("checksum_digest(Body, <<\"SHA256\">>) -> sha256_hash(Body);");
        writer.write("checksum_digest(Body, <<\"CRC32\">>) -> crc32_hash(Body);");
        writer.write("checksum_digest(Body, <<\"CRC32C\">>) -> crc32c_hash(Body).");
        writer.write("");
        writer.write("validate_response_checksum(_Body, _Headers, []) -> ok;");
        writer.write("validate_response_checksum(Body, Headers, [HeaderName | Rest]) ->");
        writer.indent();
        writer.write("case proplists:get_value(HeaderName, Headers, undefined) of");
        writer.indent();
        writer.write("undefined -> validate_response_checksum(Body, Headers, Rest);");
        writer.write("Expected ->");
        writer.indent();
        writer.write("Algorithm = checksum_algorithm_from_header(HeaderName),");
        writer.write("Computed = base16_encode(checksum_digest(Body, Algorithm)),");
        writer.write("case Computed =:= Expected of");
        writer.indent();
        writer.write("true -> ok;");
        writer.write("false -> {error, {checksum_mismatch, HeaderName}}");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end.");
        writer.dedent();
        writer.write("");
        writer.write("checksum_algorithm_from_header(<<\"x-amz-checksum-\", Rest/binary>>) ->");
        writer.write("    list_to_binary(string:uppercase(binary_to_list(Rest))).");
    }

    private static void emitChecksumBranch(
            ErlangWriter writer,
            BeamHttpChecksumIndex.ChecksumBinding cb,
            String headersVar) {
        String checksumVar = "Checksum";
        emitChecksumComputation(writer, cb, checksumVar);
        writer.write("headers_set(<<\"$L\">>, base16_encode($L), $L);",
                cb.headerName(), checksumVar, headersVar);
    }

    private static void emitChecksumComputation(
            ErlangWriter writer,
            BeamHttpChecksumIndex.ChecksumBinding cb,
            String checksumVar) {
        if (cb.usesCryptoHash()) {
            writer.write("$L = crypto:hash($L, Body),", checksumVar, cb.algorithmErlangAtom());
        } else {
            writer.write("$L = $L(Body),", checksumVar, cb.hashHelperName());
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
