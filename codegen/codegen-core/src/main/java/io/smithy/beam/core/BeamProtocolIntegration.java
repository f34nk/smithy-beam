package io.smithy.beam.core;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.Optional;

public interface BeamProtocolIntegration {

    /**
     * Returns a protocol codegen implementation when this integration owns the given id.
     */
    default Optional<BeamProtocolCodegen> createProtocolCodegen(
            Model model, ShapeId protocolTraitId) {
        return Optional.empty();
    }

    /**
     * Module suffix for codec filenames, e.g. "open_riak_http".
     * When empty, smithy-beam derives a suffix from the trait shape name.
     */
    default Optional<String> codecModuleSuffix(ShapeId protocolTraitId) {
        return Optional.empty();
    }

    /**
     * True when this integration emits complete wire codecs for the protocol
     * without a BeamProtocolCodegen instance (legacy customize()-only plugins).
     */
    default boolean emitsWireCodecs(ShapeId protocolTraitId) {
        return false;
    }
}
