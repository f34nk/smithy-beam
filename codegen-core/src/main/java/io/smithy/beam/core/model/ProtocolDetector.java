package io.smithy.beam.core.model;

import io.smithy.beam.core.protocol.ProtocolAnalyzer;
import io.smithy.beam.core.protocol.ProtocolAnalyzerFactory;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Resolves the {@link ProtocolAnalyzer} for a service using {@link ProtocolAnalyzerFactory}.
 */
public final class ProtocolDetector {

    private ProtocolDetector() {}

    public static ProtocolAnalyzer detect(ServiceShape service, Model model) {
        return ProtocolAnalyzerFactory.forService(service, model);
    }
}
