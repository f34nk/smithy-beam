package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Emits {@code aws_endpoint_rules.erl} when the model defines {@code @endpointRuleSet}.
 */
public final class ErlangAwsEndpointRulesEmitter {

    private ErlangAwsEndpointRulesEmitter() {}

    public static void emitIfNeeded(ErlangContext ctx, ServiceShape service) {
        if (!BeamEndpointRuleSetEmitter.hasRuleSet(ctx.model(), service)) {
            return;
        }

        ctx.writerDelegator().useFileWriter("aws_endpoint_rules.erl", writer -> {
            for (String line : loadResource("aws_endpoint_rules.erl").split("\n", -1)) {
                writer.write(line);
            }
        });
    }

    private static String loadResource(String name) {
        try (InputStream in = ErlangAwsEndpointRulesEmitter.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resource: " + name, e);
        }
    }
}
