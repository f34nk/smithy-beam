package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamHttpChecksumIndex;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

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

    static void emitChecksumHelpers(ErlangWriter writer) {
        ErlangHttpChecksumIr.writeFunctions(writer, ErlangHttpChecksumIr.checksumHelperFunctions());
    }
}
