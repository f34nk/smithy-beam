package io.smithy.beam.elixir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class ElixirRuntimeTypesEmitter {

    private ElixirRuntimeTypesEmitter() {}

    public static void writeBody(
            ElixirWriter writer, String moduleName, Optional<String> endpointRuleSetJson) {
        writer.write("defmodule $L do", moduleName);
        writer.indent();
        writer.openBlock("@moduledoc \"\"\"");
        ElixirFormat.writeHeredocBody(
                writer, java.util.List.of("Generated HTTP and client runtime types for Smithy service clients."));
        writer.closeBlock("\"\"\"");
        for (String line : loadResource("runtime_types.ex").split("\n", -1)) {
            writer.write(line);
        }
        endpointRuleSetJson.ifPresent(json -> {
            writer.write("");
            writer.write("@type endpoint_rule_set :: map()");
            writer.write("@endpoint_rule_set_json ~S\"\"\"");
            writer.write(json);
            writer.write("\"\"\"");
            writer.write("Module.register_attribute(__MODULE__, :endpoint_rule_set, persist: true)");
            writer.write("@endpoint_rule_set Jason.decode!(@endpoint_rule_set_json)");
            writer.write("");
            ElixirFormat.writeSpec(
                    writer,
                    "@spec",
                    "endpoint_rule_set",
                    "",
                    "endpoint_rule_set()");
            writer.write("def endpoint_rule_set, do: @endpoint_rule_set");
        });
        ElixirFormat.writeModuleEnd(writer);
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
