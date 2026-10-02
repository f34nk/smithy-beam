defmodule SmithyBeam.Config do
  @moduledoc false

  alias SmithyBeam.Defaults

  @default_repositories [%{"url" => "https://repo1.maven.org/maven2"}]

  @doc """
  Parses smithy_beam options into an internal config map.

  Pass an explicit keyword list for tests, or omit to read
  `Mix.Project.config()[:smithy_beam]`.
  """
  def parse(opts \\ nil) do
    opts = opts || Mix.Project.config()[:smithy_beam]

    cond do
      is_nil(opts) ->
        {:error, "missing :smithy_beam project configuration"}

      not Keyword.keyword?(opts) ->
        {:error, ":smithy_beam configuration must be a keyword list"}

      true ->
        do_parse(opts)
    end
  end

  defp do_parse(opts) do
    config_file = Keyword.get(opts, :config_file)

    if config_file do
      parse_config_file_mode(opts, config_file)
    else
      parse_generate_mode(opts)
    end
  end

  defp parse_generate_mode(opts) do
    with {:ok, language} <- fetch_language(opts),
         {:ok, kind} <- fetch_kind(opts),
         :ok <- require_known_plugin(language, kind),
         {:ok, models} <- require_string(opts, :models),
         {:ok, edition} <- require_string(opts, :edition) do
      plugin = Keyword.get(opts, :plugin) || Defaults.plugin_id(language, kind)
      output = Keyword.get(opts, :output) || Defaults.default_output(language)

      {:ok,
       %{
         models: models,
         output: output,
         language: language,
         kind: kind,
         edition: edition,
         service: optional_string(opts, :service),
         name: optional_string(opts, :name),
         protocol: optional_string(opts, :protocol),
         package_version: optional_string(opts, :package_version),
         codegen_version: Keyword.get(opts, :codegen_version, Defaults.codegen_version()),
         maven_deps: Keyword.get(opts, :maven_deps, []),
         maven_repositories: Keyword.get(opts, :maven_repositories, @default_repositories),
         config_file: nil,
         plugin: plugin,
         project_root: project_root(opts)
       }}
    end
  end

  defp parse_config_file_mode(opts, config_file) do
    with {:ok, config_file} <- ensure_non_blank(config_file, :config_file),
         {:ok, language} <- optional_language(opts),
         {:ok, kind} <- optional_kind(opts),
         {:ok, plugin} <- resolve_plugin(opts, language, kind) do
      output =
        Keyword.get(opts, :output) ||
          (language && Defaults.default_output(language)) ||
          "lib/generated"

      {:ok,
       %{
         models: Keyword.get(opts, :models),
         output: output,
         language: language,
         kind: kind,
         edition: optional_string(opts, :edition),
         service: optional_string(opts, :service),
         name: optional_string(opts, :name),
         protocol: optional_string(opts, :protocol),
         package_version: optional_string(opts, :package_version),
         codegen_version: Keyword.get(opts, :codegen_version, Defaults.codegen_version()),
         maven_deps: Keyword.get(opts, :maven_deps, []),
         maven_repositories: Keyword.get(opts, :maven_repositories, @default_repositories),
         config_file: config_file,
         plugin: plugin,
         project_root: project_root(opts)
       }}
    end
  end

  defp resolve_plugin(opts, language, kind) do
    case Keyword.get(opts, :plugin) do
      plugin when is_binary(plugin) and plugin != "" ->
        {:ok, plugin}

      _ when language != nil and kind != nil ->
        if Defaults.known_plugin?({language, kind}) do
          {:ok, Defaults.plugin_id(language, kind)}
        else
          {:error, "unknown language/kind pair: #{inspect(language)}/#{inspect(kind)}"}
        end

      _ ->
        {:error, "config_file mode requires :plugin or both :language and :kind"}
    end
  end

  defp fetch_language(opts) do
    case normalize_atom(Keyword.get(opts, :language)) do
      lang when lang in [:elixir, :erlang] -> {:ok, lang}
      nil -> {:error, "missing required :language (expected :elixir or :erlang)"}
      other -> {:error, "invalid :language #{inspect(other)} (expected :elixir or :erlang)"}
    end
  end

  defp fetch_kind(opts) do
    case normalize_atom(Keyword.get(opts, :kind)) do
      kind when kind in [:client, :server, :types] -> {:ok, kind}
      nil -> {:error, "missing required :kind (expected :client, :server, or :types)"}
      other -> {:error, "invalid :kind #{inspect(other)} (expected :client, :server, or :types)"}
    end
  end

  defp optional_language(opts) do
    case Keyword.fetch(opts, :language) do
      :error -> {:ok, nil}
      {:ok, _} -> fetch_language(opts)
    end
  end

  defp optional_kind(opts) do
    case Keyword.fetch(opts, :kind) do
      :error -> {:ok, nil}
      {:ok, _} -> fetch_kind(opts)
    end
  end

  defp require_known_plugin(language, kind) do
    if Defaults.known_plugin?({language, kind}) do
      :ok
    else
      {:error, "unknown language/kind pair: #{inspect(language)}/#{inspect(kind)}"}
    end
  end

  defp require_string(opts, key) do
    case Keyword.get(opts, key) do
      value when is_binary(value) and value != "" -> {:ok, value}
      nil -> {:error, "missing required :#{key}"}
      other -> {:error, "invalid :#{key} #{inspect(other)} (expected non-empty string)"}
    end
  end

  defp ensure_non_blank(value, _key) when is_binary(value) and value != "", do: {:ok, value}
  defp ensure_non_blank(_, key), do: {:error, "invalid :#{key} (expected non-empty string)"}

  defp optional_string(opts, key) do
    case Keyword.get(opts, key) do
      value when is_binary(value) and value != "" -> value
      _ -> nil
    end
  end

  defp normalize_atom(value) when is_atom(value), do: value

  defp normalize_atom(value) when is_binary(value) do
    String.to_existing_atom(value)
  rescue
    ArgumentError -> nil
  end

  defp normalize_atom(_), do: nil

  defp project_root(opts) do
    Keyword.get(opts, :project_root) || File.cwd!()
  end
end
