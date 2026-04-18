package io.smithy.beam.core;

import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Base settings for all smithy-beam codegen plugins.
 *
 * <p>Subclasses must call {@link #validate()} from their own {@code validate()}
 * override after checking language-specific required fields.
 *
 * <p>Field population is driven by Smithy's {@code NodeMapper}, which requires
 * JavaBean-style {@code getXxx}/{@code setXxx} pairs and a no-arg constructor.
 */
public abstract class BeamSettings {

    private ShapeId service;
    private ShapeId protocol;
    private String edition;
    private String outputDir = "src/generated";
    private String scaffoldDir;
    private String relativeDate;
    private String relativeVersion;

    public ShapeId getService() {
        return service;
    }

    public void setService(ShapeId service) {
        this.service = service;
    }

    public ShapeId getProtocol() {
        return protocol;
    }

    public void setProtocol(ShapeId protocol) {
        this.protocol = protocol;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getScaffoldDir() {
        return scaffoldDir;
    }

    public void setScaffoldDir(String scaffoldDir) {
        this.scaffoldDir = scaffoldDir;
    }

    /** ISO-8601 date string, e.g. {@code "2025-01-01"}. */
    public String getRelativeDate() {
        return relativeDate;
    }

    public void setRelativeDate(String relativeDate) {
        this.relativeDate = relativeDate;
    }

    public String getRelativeVersion() {
        return relativeVersion;
    }

    public void setRelativeVersion(String relativeVersion) {
        this.relativeVersion = relativeVersion;
    }

    /**
     * Returns the {@link Mode} for this settings instance.
     *
     * <p>Leaf settings classes ({@code ErlangClientSettings}, etc.) override
     * this to return a fixed constant so that code generators and integrations
     * can query the mode without an {@code instanceof} check.
     */
    public abstract Mode mode();

    /**
     * Validates that all required fields are present.
     *
     * <p>Throws {@link BeamCodegenException} if {@code service} or
     * {@code edition} are missing. Subclasses should call
     * {@code super.validate()} before checking their own required fields.
     */
    public void validate() {
        if (service == null) {
            throw new BeamCodegenException("BeamSettings requires 'service' to be set");
        }
        if (edition == null || edition.isBlank()) {
            throw new BeamCodegenException("BeamSettings requires 'edition' to be set");
        }
    }
}
