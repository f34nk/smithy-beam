package io.smithy.beam.gleam;

import io.beam.lang.gleam.Module;

final class GleamClientEmission {

  private GleamClientEmission() {}

  static void writeModule(GleamContext ctx, String file, Module module) {
    GleamModuleEmission.writeModule(ctx, file, module);
  }
}
