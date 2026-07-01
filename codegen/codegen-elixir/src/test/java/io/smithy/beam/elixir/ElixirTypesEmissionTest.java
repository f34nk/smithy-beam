package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExDefstruct;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExNestedModule;
import io.smithy.beam.ir.elixir.ExTypeDef;
import java.util.List;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.build.MockManifest;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

class ElixirTypesEmissionTest {

  @Test
  void monolithicWhenNoDefstructExceedsThreshold() {
    MockManifest manifest = new MockManifest();
    ElixirContext ctx = contextWithEntries(manifest, "FooTypes", "foo_types.ex");
    ElixirTypesEmission.writeTypesModules(ctx, Integer.MAX_VALUE);
    ctx.writerDelegator().flushWriters();

    String root = manifest.expectFileString("foo_types.ex");
    assertThat(root).contains("defmodule FooTypes do");
    assertThat(root).contains("defmodule SmallShape do");
    assertThat(root).contains("defmodule OrderStatus do");
    assertThat(getFilePaths(manifest)).containsExactly("foo_types.ex");
  }

  @Test
  void keepsSmallDefstructNested_inCentralFile() {
    MockManifest manifest = new MockManifest();
    ElixirContext ctx = contextWithEntries(manifest, "FooTypes", "foo_types.ex");
    ElixirTypesEmission.writeTypesModules(ctx, 50);
    ctx.writerDelegator().flushWriters();

    String root = manifest.expectFileString("foo_types.ex");
    assertThat(root).contains("defmodule SmallShape do");
    assertThat(root).contains("defmodule OrderStatus do");
  }

  @Test
  void splitsOnlyOversizedDefstructModules() {
    MockManifest manifest = new MockManifest();
    ElixirContext ctx = contextWithEntries(manifest, "FooTypes", "foo_types.ex");
    ElixirTypesEmission.writeTypesModules(ctx, 50);
    ctx.writerDelegator().flushWriters();

    String root = manifest.expectFileString("foo_types.ex");
    assertThat(root).contains("defmodule FooTypes do");
    assertThat(root).contains("@type root_alias :: String.t()");
    assertThat(root).contains("defmodule SmallShape do");
    assertThat(root).contains("defmodule OrderStatus do");
    assertThat(root).doesNotContain("defmodule LargeShape do");

    String large = manifest.expectFileString("types/large_shape.ex");
    assertThat(large).contains("defmodule FooTypes.LargeShape do");
    assertThat(large).contains("@type t :: %__MODULE__{");
    assertThat(large).doesNotContain("defmodule LargeShape do");
  }

  private static ElixirContext contextWithEntries(
      MockManifest manifest, String moduleName, String typesFile) {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    settings.name("foo");
    ShapeId serviceId = ShapeId.from("com.example#ExampleService");
    Model model =
        Model.builder()
            .addShape(ServiceShape.builder().id(serviceId).version("1").build())
            .build();
    ServiceShape service = model.expectShape(serviceId, ServiceShape.class);
    SymbolProvider sp =
        SymbolProvider.cache(
            new ElixirSymbolProvider(
                settings, model, service, typesFile, moduleName, BeamCodegenKind.TYPES));
    ElixirContext ctx =
        new ElixirContext(
            model,
            settings,
            sp,
            manifest,
            new WriterDelegator<>(manifest, sp, ElixirWriter.factory("test")),
            List.of(),
            service,
            null,
            null,
            null,
            moduleName,
            typesFile);
    ctx.addTypesPreambleEntry(ExModuledoc.moduledoc("Types."));
    ctx.addTypesEntry(ExTypeDef.alias("root_alias", "String.t()"));
    ctx.addTypesEntry(smallStructure("SmallShape"));
    ctx.addTypesEntry(largeStructure("LargeShape"));
    ctx.addTypesEntry(
        ExNestedModule.nestedModule(
            "OrderStatus",
            List.of(),
            List.of(ExTypeDef.alias("t", ":pending | :shipped")),
            List.of()));
    return ctx;
  }

  private static ExNestedModule smallStructure(String name) {
    return ExNestedModule.nestedModule(
        name,
        List.of(),
        List.of(
            ExDefstruct.defstruct(List.of(":name")),
            ExTypeDef.structureType("t", List.of("name: String.t() | nil"))),
        List.of());
  }

  private static ExNestedModule largeStructure(String name) {
    String longField =
        "payload: "
            + "VeryLongNamespace.VeryLongServiceTypes.AnotherNestedType.t() | nil";
    return ExNestedModule.nestedModule(
        name,
        List.of(),
        List.of(
            ExDefstruct.defstruct(List.of(":payload")),
            ExTypeDef.structureType("t", List.of(longField))),
        List.of());
  }

  private static List<String> getFilePaths(MockManifest manifest) {
    return manifest.getFiles().stream()
        .map(Path::toString)
        .map(path -> path.startsWith("/") ? path.substring(1) : path)
        .sorted()
        .toList();
  }
}
