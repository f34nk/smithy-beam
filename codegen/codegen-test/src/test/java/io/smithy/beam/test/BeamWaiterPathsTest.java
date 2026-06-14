package io.smithy.beam.test;

import io.smithy.beam.core.BeamWaiterPaths;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BeamWaiterPathsTest {

    @Test
    void simpleDottedPathEmitsSnakeCaseSegments() {
        assertThat(BeamWaiterPaths.isSimpleDottedPath("Table.TableStatus")).isTrue();
        assertThat(BeamWaiterPaths.emitErlangPath("Table.TableStatus")).isEqualTo("[table, table_status]");
        assertThat(BeamWaiterPaths.emitElixirPath("Table.TableStatus")).isEqualTo("[:table, :table_status]");
    }

    @Test
    void complexPathEmitsStringLiteral() {
        String path =
                "length(KinesisDataStreamDestinations) > `0` && length(KinesisDataStreamDestinations[?DestinationStatus == 'disabled']) == length(KinesisDataStreamDestinations)";
        assertThat(BeamWaiterPaths.isSimpleDottedPath(path)).isFalse();
        assertThat(BeamWaiterPaths.emitErlangPath(path)).isEqualTo("<<\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\">>");
        assertThat(BeamWaiterPaths.emitElixirPath(path)).startsWith("\"");
        assertThat(BeamWaiterPaths.emitElixirPath(path)).endsWith("\"");
    }
}
