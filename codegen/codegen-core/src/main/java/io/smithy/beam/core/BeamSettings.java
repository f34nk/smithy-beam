package io.smithy.beam.core;

import java.time.LocalDate;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Abstract base for all smithy-beam plugin settings.
 *
 * <p>Subclasses expose a concrete {@link Mode} and may add language-specific
 * fields. {@code CodegenDirector} maps JSON settings onto this class via
 * {@code NodeMapper}, which requires JavaBean-style {@code setXxx}/{@code getXxx}
 * pairs for every field.
 */
public abstract class BeamSettings {

    private ShapeId service;
    private ShapeId protocol;
    private String edition;
    private String outputDir = "src/generated";
    private String scaffoldDir;
    private LocalDate relativeDate;
    private String relativeVersion;
    private boolean removeUnreferencedShapes = false;
    private boolean applyAuthSchemes = false;

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

    public LocalDate getRelativeDate() {
        return relativeDate;
    }

    public void setRelativeDate(LocalDate relativeDate) {
        this.relativeDate = relativeDate;
    }

    public String getRelativeVersion() {
        return relativeVersion;
    }

    public void setRelativeVersion(String relativeVersion) {
        this.relativeVersion = relativeVersion;
    }

    /**
     * When {@code true}, shapes not reachable from the service closure are
     * removed before code generation. Defaults to {@code false}.
     */
    public boolean isRemoveUnreferencedShapes() {
        return removeUnreferencedShapes;
    }

    public void setRemoveUnreferencedShapes(boolean removeUnreferencedShapes) {
        this.removeUnreferencedShapes = removeUnreferencedShapes;
    }

    /**
     * When {@code true}, every operation without an explicit {@code @auth}
     * trait will have the service-level effective auth schemes applied to it
     * explicitly. Defaults to {@code false}.
     */
    public boolean isApplyAuthSchemes() {
        return applyAuthSchemes;
    }

    public void setApplyAuthSchemes(boolean applyAuthSchemes) {
        this.applyAuthSchemes = applyAuthSchemes;
    }

    /** Returns whether this plugin generates client or server code. */
    public abstract Mode mode();

    /**
     * Validates that all required settings are present.
     *
     * @throws BeamCodegenException if {@code service} or {@code edition} is missing
     */
    public void validate() {
        if (service == null) {
            throw new BeamCodegenException("'service' is required in codegen settings");
        }
        if (edition == null || edition.isBlank()) {
            throw new BeamCodegenException("'edition' is required in codegen settings");
        }
    }
}
