package io.smithy.beam.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.smithy.codegen.core.directed.CodegenDirector;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BeamCodegenTransformsTest {

    @Mock
    @SuppressWarnings("rawtypes")
    private CodegenDirector director;

    @Test
    @SuppressWarnings("unchecked")
    void applySharedCodegenTransforms_invokesDirectorStepsIncludingRelativeFilters() {
        BeamSettings settings = new BeamSettings();
        settings.relativeDate(" 2026-06-01 ");
        settings.relativeVersion(" 1.2.3 ");

        BeamCodegenTransforms.applySharedCodegenTransforms(director, settings);

        InOrder order = inOrder(director);
        order.verify(director).performDefaultCodegenTransforms();
        order.verify(director).createDedicatedInputsAndOutputs();
        order.verify(director).removeShapesDeprecatedBeforeDate("2026-06-01");
        order.verify(director).removeShapesDeprecatedBeforeVersion("1.2.3");
    }

    @Test
    @SuppressWarnings("unchecked")
    void applySharedCodegenTransforms_skipsDeprecationFilters_whenRelativeFieldsBlankOrWhitespace() {
        BeamSettings settings = new BeamSettings();
        settings.relativeDate("   ");
        settings.relativeVersion("\t");

        BeamCodegenTransforms.applySharedCodegenTransforms(director, settings);

        verify(director).performDefaultCodegenTransforms();
        verify(director).createDedicatedInputsAndOutputs();
        verify(director, never()).removeShapesDeprecatedBeforeDate(anyString());
        verify(director, never()).removeShapesDeprecatedBeforeVersion(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void applySharedCodegenTransforms_skipsDeprecationFilters_whenRelativeFieldsUnset() {
        BeamSettings settings = new BeamSettings();

        BeamCodegenTransforms.applySharedCodegenTransforms(director, settings);

        verify(director).performDefaultCodegenTransforms();
        verify(director).createDedicatedInputsAndOutputs();
        verify(director, never()).removeShapesDeprecatedBeforeDate(anyString());
        verify(director, never()).removeShapesDeprecatedBeforeVersion(anyString());
    }
}
