package io.smithy.beam.elixir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class ElixirRuntimeTypesEmitter {

    private ElixirRuntimeTypesEmitter() {}

    public static void writeBody(
            ElixirWriter writer, String moduleName, Optional<String> endpointRuleSetMap) {
        writer.write("defmodule $L do", moduleName);
        writer.indent();
        writer.openBlock("@moduledoc \"\"\"");
        writer.write("Generated HTTP and client runtime types for Smithy service clients.");
        writer.closeBlock("\"\"\"");
        for (String line : loadResource("runtime_types.ex").split("\n", -1)) {
            writer.write(line);
        }
        endpointRuleSetMap.ifPresent(map -> {
            writer.write("");
            writer.write("Module.register_attribute(__MODULE__, :endpoint_rule_set, persist: true)");
            writer.write("@endpoint_rule_set $L", map);
        });
        writer.dedent();
        writer.write("end");
    }

    private static String loadResource(String name) {
        try (InputStream in = ElixirRuntimeTypesEmitter.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load resource: " + name, e);
        }
    }
}
