package io.smithy.beam.core;

import software.amazon.smithy.aws.traits.HttpChecksumTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.EnumTrait;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves {@code aws.protocols#httpChecksum} bindings for codec emitters.
 */
public final class BeamHttpChecksumIndex {

    private static final String CONTENT_MD5 = "Content-MD5";

    private final Model model;

    public record ChecksumBinding(
            String algorithm,
            String headerName,
            boolean isRequestChecksum,
            boolean isResponseChecksum
    ) {
        public String algorithmErlangAtom() {
            return switch (algorithm.toUpperCase(Locale.US)) {
                case "MD5" -> "md5";
                case "SHA1" -> "sha";
                case "SHA256" -> "sha256";
                case "SHA512" -> "sha512";
                default -> algorithm.toLowerCase(Locale.US);
            };
        }

        public boolean usesCryptoHash() {
            return switch (algorithm.toUpperCase(Locale.US)) {
                case "CRC32", "CRC32C", "CRC64NVME", "XXHASH64", "XXHASH3", "XXHASH128" -> false;
                default -> true;
            };
        }

        public String hashHelperName() {
            return switch (algorithm.toUpperCase(Locale.US)) {
                case "CRC32" -> "crc32_hash";
                case "CRC32C" -> "crc32c_hash";
                case "SHA256" -> "sha256_hash";
                case "MD5" -> "md5_hash";
                default -> algorithm.toLowerCase(Locale.US) + "_hash";
            };
        }
    }

    private BeamHttpChecksumIndex(Model model) {
        this.model = model;
    }

    public static BeamHttpChecksumIndex of(Model model) {
        return new BeamHttpChecksumIndex(model);
    }

    public Optional<HttpChecksumTrait> httpChecksum(OperationShape operation) {
        return operation.getTrait(HttpChecksumTrait.class);
    }

    public List<ChecksumBinding> requestChecksums(OperationShape operation) {
        Optional<HttpChecksumTrait> traitOpt = httpChecksum(operation);
        if (traitOpt.isEmpty()) {
            return List.of();
        }
        HttpChecksumTrait trait = traitOpt.get();
        if (trait.getRequestAlgorithmMember().isPresent()) {
            return flexibleRequestBindings(operation, trait);
        }
        if (trait.isRequestChecksumRequired()) {
            return List.of(new ChecksumBinding("MD5", CONTENT_MD5, true, false));
        }
        return List.of();
    }

    public List<ChecksumBinding> responseChecksums(OperationShape operation) {
        Optional<HttpChecksumTrait> traitOpt = httpChecksum(operation);
        if (traitOpt.isEmpty()) {
            return List.of();
        }
        HttpChecksumTrait trait = traitOpt.get();
        if (trait.getRequestValidationModeMember().isEmpty()
                || trait.getResponseAlgorithms().isEmpty()) {
            return List.of();
        }
        List<ChecksumBinding> bindings = new ArrayList<>();
        for (String algorithm : trait.getResponseAlgorithms()) {
            bindings.add(new ChecksumBinding(
                    algorithm,
                    HttpChecksumTrait.getChecksumLocationName(algorithm),
                    false,
                    true));
        }
        return List.copyOf(bindings);
    }

    public Optional<String> requestAlgorithmMemberName(OperationShape operation) {
        return httpChecksum(operation).flatMap(HttpChecksumTrait::getRequestAlgorithmMember);
    }

    public Optional<String> requestValidationModeMemberName(OperationShape operation) {
        return httpChecksum(operation).flatMap(HttpChecksumTrait::getRequestValidationModeMember);
    }

    public boolean hasChecksumBehavior(OperationShape operation) {
        return !requestChecksums(operation).isEmpty() || !responseChecksums(operation).isEmpty();
    }

    private List<ChecksumBinding> flexibleRequestBindings(OperationShape operation, HttpChecksumTrait trait) {
        String memberName = trait.getRequestAlgorithmMember().get();
        StructureShape input = model.expectShape(operation.getInputShape(), StructureShape.class);
        Optional<MemberShape> member = input.getMember(memberName);
        if (member.isEmpty()) {
            return List.of();
        }
        Shape target = model.expectShape(member.get().getTarget());
        Set<String> algorithms = enumValues(target);
        List<ChecksumBinding> bindings = new ArrayList<>();
        for (String algorithm : algorithms) {
            if (!HttpChecksumTrait.CHECKSUM_ALGORITHMS.contains(algorithm)) {
                continue;
            }
            bindings.add(new ChecksumBinding(
                    algorithm,
                    HttpChecksumTrait.getChecksumLocationName(algorithm),
                    true,
                    false));
        }
        return List.copyOf(bindings);
    }

    private Set<String> enumValues(Shape target) {
        Set<String> values = new LinkedHashSet<>();
        if (target instanceof EnumShape enumShape) {
            values.addAll(enumShape.getEnumValues().keySet());
            return values;
        }
        target.getTrait(EnumTrait.class).ifPresent(enumTrait -> enumTrait.getValues()
                .forEach(definition -> definition.getName().ifPresent(values::add)));
        return values;
    }
}
