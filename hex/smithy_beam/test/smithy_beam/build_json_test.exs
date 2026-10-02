defmodule SmithyBeam.BuildJsonTest do
  use ExUnit.Case, async: true

  alias SmithyBeam.BuildJson
  alias SmithyBeam.Defaults

  defp elixir_config(overrides \\ %{}) do
    Map.merge(
      %{
        models: "model",
        output: "lib/generated",
        language: :elixir,
        kind: :client,
        edition: "2026",
        service: nil,
        name: nil,
        protocol: nil,
        package_version: nil,
        codegen_version: Defaults.codegen_version(),
        maven_deps: [],
        maven_repositories: [%{"url" => "https://repo1.maven.org/maven2"}],
        config_file: nil,
        plugin: "elixir-client-codegen",
        project_root: "/tmp/proj"
      },
      overrides
    )
  end

  test "elixir client map uses defaults and plugin id" do
    map = BuildJson.to_map(elixir_config())

    assert map["version"] == "1.0"
    assert map["sources"] == ["model"]
    assert hd(map["maven"]["dependencies"]) == Defaults.codegen_coordinate(:elixir)
    assert Defaults.common_maven_deps() -- map["maven"]["dependencies"] == []
    assert map["plugins"]["elixir-client-codegen"] == %{"edition" => "2026"}
  end

  test "consumer maven_deps are appended after defaults" do
    extra = "com.example:extra:1.0.0"
    deps = BuildJson.maven_dependencies(elixir_config(%{maven_deps: [extra]}))

    assert List.last(deps) == extra
    assert Enum.member?(deps, Defaults.codegen_coordinate(:elixir))
  end

  test "erlang uses codegen-erlang coordinate" do
    config =
      elixir_config(%{
        language: :erlang,
        kind: :client,
        plugin: "erlang-client-codegen",
        output: "src/generated"
      })

    deps = BuildJson.maven_dependencies(config)
    assert hd(deps) == Defaults.codegen_coordinate(:erlang)
    refute Enum.any?(deps, &String.contains?(&1, "codegen-elixir"))
  end

  test "optional plugin settings are included when set" do
    config =
      elixir_config(%{
        service: "example#Svc",
        name: "weather",
        protocol: "aws.protocols#restJson1",
        package_version: "1.2.3"
      })

    assert BuildJson.plugin_settings(config) == %{
             "edition" => "2026",
             "service" => "example#Svc",
             "name" => "weather",
             "protocol" => "aws.protocols#restJson1",
             "packageVersion" => "1.2.3"
           }
  end

  test "encode! produces JSON" do
    json = BuildJson.encode!(elixir_config())
    assert Jason.decode!(json)["plugins"]["elixir-client-codegen"]["edition"] == "2026"
  end
end
