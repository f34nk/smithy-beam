package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.model.shapes.ServiceShape;

public final class ElixirRuntimeTypesEmitter {

    private ElixirRuntimeTypesEmitter() {}

    public static String modulePath(BeamSettings settings, ServiceShape service) {
        String app = settings.resolveModule(service.getId().getNamespace());
        return app + "_runtime_types.ex";
    }

    public static void writeBody(ElixirWriter writer, String moduleName) {
        writer.write("defmodule $L do", moduleName);
        writer.indent();
        writer.openBlock("@moduledoc \"\"\"");
        writer.write("Generated HTTP and client runtime types for Smithy service clients.");
        writer.closeBlock("\"\"\"");
        writer.write("@type http_request :: %__MODULE__.HttpRequest{}");
        writer.write("defmodule HttpRequest do");
        writer.indent();
        writer.write("defstruct method: \"GET\", path: \"/\", query: %{}, headers: [], body: \"\"");
        writer.dedent();
        writer.write("end");
        writer.write("defmodule HttpResponse do");
        writer.indent();
        writer.write("defstruct status: 200, headers: [], body: \"\"");
        writer.dedent();
        writer.write("end");
        writer.dedent();
        writer.write("end");
    }
}
