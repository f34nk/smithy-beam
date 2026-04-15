package io.smithy.beam.protocols;

import io.smithy.beam.core.protocol.ProtocolAnalyzerFactory;

/**
 * Registers built-in protocol analyzers with {@link ProtocolAnalyzerFactory}. Call before resolving a protocol
 * for a service (e.g. at the start of a Smithy build plugin {@code execute} method).
 */
public final class ProtocolRegistrations {

    private static final Object LOCK = new Object();
    private static volatile boolean registered;

    private ProtocolRegistrations() {}

    public static void init() {
        if (registered) {
            return;
        }
        synchronized (LOCK) {
            if (registered) {
                return;
            }
            ProtocolAnalyzerFactory.register(new RestJsonProtocolAnalyzer());
            ProtocolAnalyzerFactory.register(new AwsJsonProtocolAnalyzer());
            ProtocolAnalyzerFactory.register(new AwsJson11ProtocolAnalyzer());
            ProtocolAnalyzerFactory.register(new AwsQueryProtocolAnalyzer());
            ProtocolAnalyzerFactory.register(new Ec2QueryProtocolAnalyzer());
            ProtocolAnalyzerFactory.register(new RestXmlProtocolAnalyzer());
            registered = true;
        }
    }
}
