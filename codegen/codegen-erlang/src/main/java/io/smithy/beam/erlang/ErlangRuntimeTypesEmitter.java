package io.smithy.beam.erlang;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class ErlangRuntimeTypesEmitter {

    private ErlangRuntimeTypesEmitter() {}

    public static void writeBody(ErlangWriter writer, Optional<String> endpointRuleSetMap) {
        for (String line : loadResource("runtime_types.hrl").split("\n", -1)) {
            writer.write(line);
        }
        endpointRuleSetMap.ifPresent(map -> {
            writer.write("");
            writer.write("%% @endpointRuleSet embedded at codegen time.");
            writer.write("-define(ENDPOINT_RULE_SET, $L).", map);
        });
    }

    private static String loadResource(String name) {
        try (InputStream in = ErlangRuntimeTypesEmitter.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resource: " + name, e);
        }
    }
}
