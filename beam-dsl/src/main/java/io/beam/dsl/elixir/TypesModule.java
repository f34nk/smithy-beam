package io.beam.dsl.elixir;

import java.util.List;

public record TypesModule(
    String name, Moduledoc moduledocOrNull, TypeDef typeDef, List<DefstructField> defstructFields) {

  public static TypesModule of(
      String name, Moduledoc moduledoc, TypeDef typeDef, List<DefstructField> defstructFields) {
    return new TypesModule(name, moduledoc, typeDef, defstructFields);
  }
}
