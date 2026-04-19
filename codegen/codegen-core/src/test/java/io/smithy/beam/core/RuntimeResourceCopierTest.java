package io.smithy.beam.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;

class RuntimeResourceCopierTest {

    private static final String STRIP_PREFIX = "test-runtime/";
    private static final String OUTPUT_PREFIX = "runtime/";

    @Test
    void singleFileIsCopiedToOutputPath() {
        MockManifest manifest = new MockManifest();

        RuntimeResourceCopier.copy(
                getClass().getClassLoader(),
                List.of("test-runtime/client/shared_module.beam"),
                manifest,
                STRIP_PREFIX,
                OUTPUT_PREFIX);

        assertThat(manifest.getFileString("runtime/client/shared_module.beam")).isPresent();
    }

    @Test
    void sameBasenameInputsLandInDifferentOutputPaths() {
        MockManifest manifest = new MockManifest();

        RuntimeResourceCopier.copy(
                getClass().getClassLoader(),
                List.of(
                        "test-runtime/client/shared_module.beam",
                        "test-runtime/server/shared_module.beam"),
                manifest,
                STRIP_PREFIX,
                OUTPUT_PREFIX);

        String clientContent = manifest.expectFileString("runtime/client/shared_module.beam");
        String serverContent = manifest.expectFileString("runtime/server/shared_module.beam");

        assertThat(clientContent).isNotEqualTo(serverContent);
    }

    @Test
    void missingResourceThrowsBeamCodegenException() {
        MockManifest manifest = new MockManifest();

        assertThatThrownBy(() -> RuntimeResourceCopier.copy(
                        getClass().getClassLoader(),
                        List.of("test-runtime/client/does_not_exist.beam"),
                        manifest,
                        STRIP_PREFIX,
                        OUTPUT_PREFIX))
                .isInstanceOf(BeamCodegenException.class)
                .hasMessageContaining("test-runtime/client/does_not_exist.beam");
    }

    @Test
    void resourceWithoutStripPrefixUsesFullPathAsOutput() {
        MockManifest manifest = new MockManifest();

        RuntimeResourceCopier.copy(
                getClass().getClassLoader(),
                List.of("test-runtime/client/shared_module.beam"),
                manifest,
                "nonexistent-prefix/",
                OUTPUT_PREFIX);

        assertThat(manifest.getFileString("runtime/test-runtime/client/shared_module.beam"))
                .isPresent();
    }
}
