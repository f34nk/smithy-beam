defmodule SmithyBeam.BuildJson do
  @moduledoc false

  alias SmithyBeam.Defaults

  def maven_dependencies(config) do
    primary = Defaults.codegen_coordinate(config.language, config.codegen_version)
    extras = List.wrap(config.maven_deps)

    [primary]
    |> Kernel.++(Defaults.common_maven_deps())
    |> Kernel.++(extras)
    |> Enum.reject(&(&1 in [nil, ""]))
    |> Enum.uniq()
  end

  def plugin_settings(config) do
    base = %{"edition" => config.edition}

    base
    |> maybe_put("service", config.service)
    |> maybe_put("name", config.name)
    |> maybe_put("protocol", config.protocol)
    |> maybe_put("packageVersion", config.package_version)
  end

  def to_map(config) do
    %{
      "version" => "1.0",
      "sources" => [config.models],
      "maven" => %{
        "dependencies" => maven_dependencies(config),
        "repositories" => config.maven_repositories
      },
      "plugins" => %{
        config.plugin => plugin_settings(config)
      }
    }
  end

  def encode!(config) do
    config
    |> to_map()
    |> Jason.encode!(pretty: true)
  end

  def write!(config, path) do
    File.mkdir_p!(Path.dirname(path))
    File.write!(path, encode!(config) <> "\n")
    path
  end

  defp maybe_put(map, _key, nil), do: map
  defp maybe_put(map, _key, ""), do: map
  defp maybe_put(map, key, value), do: Map.put(map, key, value)
end
