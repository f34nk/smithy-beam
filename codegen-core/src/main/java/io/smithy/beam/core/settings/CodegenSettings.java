package io.smithy.beam.core.settings;

import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.Objects;
import java.util.Optional;

/**
 * Parsed plugin configuration from {@code smithy-build.json} for smithy-beam generators.
 */
public final class CodegenSettings {

    private static final String DEFAULT_OUTPUT_DIR = "src/generated";

    private final ShapeId serviceShapeId;
    private final String moduleName;
    private final String outputDir;
    private final String scaffoldDir;
    private final String namespace;

    private CodegenSettings(Builder builder) {
        this.serviceShapeId = Objects.requireNonNull(builder.serviceShapeId, "service is required");
        this.moduleName = builder.moduleName;
        this.outputDir = builder.outputDir != null ? builder.outputDir : DEFAULT_OUTPUT_DIR;
        this.scaffoldDir = builder.scaffoldDir != null ? builder.scaffoldDir : this.outputDir;
        this.namespace = builder.namespace;
    }

    public static CodegenSettings fromNode(ObjectNode node) {
        Builder b = builder();
        b.serviceShapeId(ShapeId.from(node.expectStringMember("service").getValue()));
        node.getStringMember("module").map(n -> n.getValue()).ifPresent(b::moduleName);
        node.getStringMember("outputDir").map(n -> n.getValue()).ifPresent(b::outputDir);
        node.getStringMember("scaffoldDir").map(n -> n.getValue()).ifPresent(b::scaffoldDir);
        node.getStringMember("namespace").map(n -> n.getValue()).ifPresent(b::namespace);
        return b.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public ShapeId serviceShapeId() {
        return serviceShapeId;
    }

    public Optional<String> moduleName() {
        return Optional.ofNullable(moduleName);
    }

    public String outputDir() {
        return outputDir;
    }

    public String scaffoldDir() {
        return scaffoldDir;
    }

    public Optional<String> namespace() {
        return Optional.ofNullable(namespace);
    }

    public static final class Builder {
        private ShapeId serviceShapeId;
        private String moduleName;
        private String outputDir;
        private String scaffoldDir;
        private String namespace;

        private Builder() {}

        public Builder serviceShapeId(ShapeId serviceShapeId) {
            this.serviceShapeId = serviceShapeId;
            return this;
        }

        public Builder moduleName(String moduleName) {
            this.moduleName = moduleName;
            return this;
        }

        public Builder outputDir(String outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public Builder scaffoldDir(String scaffoldDir) {
            this.scaffoldDir = scaffoldDir;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public CodegenSettings build() {
            return new CodegenSettings(this);
        }
    }
}
