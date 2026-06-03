package io.smithy.beam.core;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.Locale;
import java.util.Optional;

/**
 * Detects Amazon S3 models and bucket/object key HTTP label bindings for endpoint customization.
 */
public final class BeamS3CustomizationIndex {

    public enum BucketAddressingStyle {
        VIRTUAL_HOST,
        PATH_STYLE
    }

    private static final String S3_SDK_ID = "s3";
    private static final String BUCKET_LABEL = "bucket";
    private static final String KEY_LABEL = "key";

    private final Model model;
    private final HttpBindingIndex httpIndex;

    private BeamS3CustomizationIndex(Model model, HttpBindingIndex httpIndex) {
        this.model = model;
        this.httpIndex = httpIndex;
    }

    public static BeamS3CustomizationIndex of(Model model) {
        return new BeamS3CustomizationIndex(model, HttpBindingIndex.of(model));
    }

    public static boolean isS3Service(ServiceShape service) {
        return BeamAwsServiceMetadata.from(service)
                .map(meta -> S3_SDK_ID.equalsIgnoreCase(meta.sdkId())
                        || S3_SDK_ID.equalsIgnoreCase(meta.endpointPrefix()))
                .orElse(false);
    }

    public boolean serviceUsesBucketAddressing(ServiceShape service) {
        if (!isS3Service(service)) {
            return false;
        }
        TopDownIndex topDown = TopDownIndex.of(model);
        for (OperationShape operation : topDown.getContainedOperations(service)) {
            if (bucketLabelBinding(operation).isPresent()) {
                return true;
            }
        }
        return false;
    }

    public Optional<HttpBinding> bucketLabelBinding(OperationShape operation) {
        return httpIndex.getRequestBindings(operation, HttpBinding.Location.LABEL).stream()
                .filter(binding -> BUCKET_LABEL.equalsIgnoreCase(binding.getLocationName()))
                .findFirst();
    }

    public Optional<HttpBinding> keyLabelBinding(OperationShape operation) {
        return httpIndex.getRequestBindings(operation, HttpBinding.Location.LABEL).stream()
                .filter(binding -> KEY_LABEL.equalsIgnoreCase(binding.getLocationName()))
                .findFirst();
    }

    public String bucketMemberSnakeCase(OperationShape operation) {
        return bucketLabelBinding(operation)
                .map(binding -> BeamNameUtils.toSnakeCase(binding.getMember().getMemberName()))
                .orElseThrow();
    }

    public Optional<String> keyMemberSnakeCase(OperationShape operation) {
        return keyLabelBinding(operation)
                .map(binding -> BeamNameUtils.toSnakeCase(binding.getMember().getMemberName()));
    }

    public String erlangAddressingStyleAtom(BucketAddressingStyle style) {
        return switch (style) {
            case VIRTUAL_HOST -> "virtual_host";
            case PATH_STYLE -> "path_style";
        };
    }

    public String elixirAddressingStyleAtom(BucketAddressingStyle style) {
        return switch (style) {
            case VIRTUAL_HOST -> ":virtual_host";
            case PATH_STYLE -> ":path_style";
        };
    }

    public static String normalizeLabelName(String locationName) {
        return locationName == null ? "" : locationName.toLowerCase(Locale.ROOT);
    }
}
