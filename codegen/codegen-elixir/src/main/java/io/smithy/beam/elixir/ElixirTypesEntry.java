package io.smithy.beam.elixir;

import io.beam.lang.elixir.Function;
import io.beam.lang.elixir.Moduledoc;
import io.beam.lang.elixir.TypesModule;
import java.util.List;

sealed interface ElixirTypesEntry
    permits ElixirTypesModuledocEntry,
        ElixirTypesRootLine,
        ElixirTypesStructNested,
        ElixirTypesEmbeddedNested {}

record ElixirTypesModuledocEntry(Moduledoc moduledoc) implements ElixirTypesEntry {}

record ElixirTypesRootLine(String line) implements ElixirTypesEntry {}

record ElixirTypesStructNested(TypesModule typesModule) implements ElixirTypesEntry {}

record ElixirTypesEmbeddedNested(
    String name, Moduledoc moduledocOrNull, List<String> extraLines, List<Function> functions)
    implements ElixirTypesEntry {}
