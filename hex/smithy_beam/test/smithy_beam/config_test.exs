defmodule SmithyBeam.ConfigTest do
  use ExUnit.Case, async: true

  alias SmithyBeam.Config

  @base [
    models: "model",
    language: :elixir,
    kind: :client,
    edition: "2026",
    project_root: "/tmp/proj"
  ]

  test "happy path applies defaults" do
    assert {:ok, config} = Config.parse(@base)
    assert config.models == "model"
    assert config.output == "lib/generated"
    assert config.language == :elixir
    assert config.kind == :client
    assert config.edition == "2026"
    assert config.plugin == "elixir-client-codegen"
    assert config.codegen_version == SmithyBeam.Defaults.codegen_version()
    assert config.maven_deps == []
    assert config.config_file == nil
    assert config.project_root == "/tmp/proj"
  end

  test "missing edition returns error" do
    opts = Keyword.delete(@base, :edition)
    assert {:error, message} = Config.parse(opts)
    assert message =~ "edition"
  end

  test "unknown kind returns error" do
    opts = Keyword.put(@base, :kind, :unknown)
    assert {:error, message} = Config.parse(opts)
    assert message =~ "kind"
  end

  test "maven_deps are preserved" do
    deps = ["software.amazon.smithy:smithy-model:1.0.0"]
    opts = Keyword.put(@base, :maven_deps, deps)
    assert {:ok, config} = Config.parse(opts)
    assert config.maven_deps == deps
  end

  test "config_file mode skips edition requirement" do
    opts = [
      config_file: "smithy-build.json",
      plugin: "elixir-client-codegen",
      output: "lib/generated",
      project_root: "/tmp/proj"
    ]

    assert {:ok, config} = Config.parse(opts)
    assert config.config_file == "smithy-build.json"
    assert config.plugin == "elixir-client-codegen"
    assert config.edition == nil
  end

  test "erlang defaults to src/generated" do
    opts =
      @base
      |> Keyword.put(:language, :erlang)
      |> Keyword.put(:kind, :server)

    assert {:ok, config} = Config.parse(opts)
    assert config.output == "src/generated"
    assert config.plugin == "erlang-server-codegen"
  end
end
