package io.smithy.beam.elixir;

import static org.assertj.core.api.Assertions.assertThat;

import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.Moduledoc;
import io.beam.dsl.elixir.Spec;
import io.beam.dsl.elixir.TypesModule;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
    ElixirTypesEmission.writeTypesModules(ctx, Integer.MAX_VALUE, Integer.MAX_VALUE);
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
    ElixirTypesEmission.writeTypesModules(ctx, 50, Integer.MAX_VALUE);
    ctx.writerDelegator().flushWriters();

    String root = manifest.expectFileString("foo_types.ex");
    assertThat(root).contains("defmodule SmallShape do");
    assertThat(root).contains("defmodule OrderStatus do");
  }

  @Test
  void splitsOnlyOversizedDefstructModules() {
    MockManifest manifest = new MockManifest();
    ElixirContext ctx = contextWithEntries(manifest, "FooTypes", "foo_types.ex");
    ElixirTypesEmission.writeTypesModules(ctx, 50, Integer.MAX_VALUE);
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

  @Test
  void keepsSmallEnumNested_inCentralFile() {
    MockManifest manifest = new MockManifest();
    ElixirContext ctx = contextWithEntries(manifest, "FooTypes", "foo_types.ex");
    ctx.addTypesEmbeddedNested(smallEnum("SmallStatus"));
    ElixirTypesEmission.writeTypesModules(ctx, Integer.MAX_VALUE, 50);
    ctx.writerDelegator().flushWriters();

    String root = manifest.expectFileString("foo_types.ex");
    assertThat(root).contains("defmodule SmallStatus do");
    assertThat(getFilePaths(manifest)).containsExactly("foo_types.ex");
  }

  @Test
  void splitsOnlyOversizedEnumModules() {
    MockManifest manifest = new MockManifest();
    ElixirContext ctx = contextWithEnumEntries(manifest, "FooTypes", "foo_types.ex");
    ElixirTypesEmission.writeTypesModules(ctx, Integer.MAX_VALUE, 50);
    ctx.writerDelegator().flushWriters();

    String root = manifest.expectFileString("foo_types.ex");
    assertThat(root).contains("defmodule FooTypes do");
    assertThat(root).contains("defmodule SmallStatus do");
    assertThat(root).doesNotContain("defmodule LargeStatus do");

    String large = manifest.expectFileString("types/large_status.ex");
    assertThat(large).contains("defmodule FooTypes.LargeStatus do");
    assertThat(large).contains("@type t ::");
    assertThat(large).contains("def from(");
    assertThat(large).doesNotContain("defmodule LargeStatus do");
  }

  @Test
  void splitsStructuresAndEnumsIndependently() {
    MockManifest manifest = new MockManifest();
    ElixirContext ctx = contextWithEntries(manifest, "FooTypes", "foo_types.ex");
    ctx.addTypesEmbeddedNested(largeEnum("LargeStatus"));
    ElixirTypesEmission.writeTypesModules(ctx, 50, 50);
    ctx.writerDelegator().flushWriters();

    assertThat(manifest.getFileString("types/large_shape.ex")).isPresent();
    assertThat(manifest.getFileString("types/large_status.ex")).isPresent();

    String root = manifest.expectFileString("foo_types.ex");
    assertThat(root).contains("defmodule SmallShape do");
    assertThat(root).doesNotContain("defmodule LargeShape do");
    assertThat(root).doesNotContain("defmodule LargeStatus do");
  }

  private static ElixirContext contextWithEntries(
      MockManifest manifest, String moduleName, String typesFile) {
    BeamSettings settings = new BeamSettings();
    settings.edition("2026");
    settings.name("foo");
    ShapeId serviceId = ShapeId.from("com.example#ExampleService");
    Model model =
        Model.builder().addShape(ServiceShape.builder().id(serviceId).version("1").build()).build();
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
    ctx.addTypesModuledoc(Moduledoc.of("Types."));
    ctx.addTypesRootLine("@type root_alias :: String.t()");
    ctx.addTypesStructNested(smallStructure("SmallShape"));
    ctx.addTypesStructNested(largeStructure("LargeShape"));
    ctx.addTypesEmbeddedNested(
        new ElixirTypesEmbeddedNested(
            "OrderStatus", null, List.of("@type t :: :pending | :shipped"), List.of()));
    return ctx;
  }

  private static TypesModule smallStructure(String name) {
    return ElixirBeamIrTypes.structNested(
        name,
        null,
        ElixirBeamIrTypes.structureTypeDef("t", List.of("name: String.t() | nil")),
        ElixirBeamIrTypes.defstructFields(List.of("name")));
  }

  private static TypesModule largeStructure(String name) {
    String longField =
        "payload: " + "VeryLongNamespace.VeryLongServiceTypes.AnotherNestedType.t() | nil";
    return ElixirBeamIrTypes.structNested(
        name,
        null,
        ElixirBeamIrTypes.structureTypeDef("t", List.of(longField)),
        ElixirBeamIrTypes.defstructFields(List.of("payload")));
  }

  private static ElixirContext contextWithEnumEntries(
      MockManifest manifest, String moduleName, String typesFile) {
    ElixirContext ctx = contextWithEntries(manifest, moduleName, typesFile);
    ctx.addTypesEmbeddedNested(smallEnum("SmallStatus"));
    ctx.addTypesEmbeddedNested(largeEnum("LargeStatus"));
    return ctx;
  }

  private static ElixirTypesEmbeddedNested smallEnum(String name) {
    return new ElixirTypesEmbeddedNested(
        name,
        null,
        List.of("@type t :: :open | :closed"),
        List.of(
            new Function(
                "from",
                false,
                List.of(FunctionHead.of(List.of(VariablePattern.of("v")))),
                Variable.of("v"),
                null,
                null,
                true)));
  }

  private static ElixirTypesEmbeddedNested largeEnum(String name) {
    String longBody =
        IntStream.range(0, 20).mapToObj(i -> ":v" + i).collect(Collectors.joining(" | "))
            + " | {:unknown, String.t()}";
    return new ElixirTypesEmbeddedNested(
        name,
        null,
        List.of("@type t :: " + longBody),
        List.of(
            new Function(
                "from",
                false,
                List.of(FunctionHead.of(List.of(VariablePattern.of("v")))),
                Variable.of("v"),
                Spec.of("from(String.t()) :: t()"),
                null,
                true),
            new Function(
                "to",
                false,
                List.of(FunctionHead.of(List.of(VariablePattern.of("v")))),
                Variable.of("v"),
                Spec.of("to(t()) :: String.t()"),
                null,
                true)));
  }

  private static List<String> getFilePaths(MockManifest manifest) {
    return manifest.getFiles().stream()
        .map(Path::toString)
        .map(path -> path.startsWith("/") ? path.substring(1) : path)
        .sorted()
        .toList();
  }
}
