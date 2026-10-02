defmodule SmithyBeam.Prereqs do
  @moduledoc false

  @doc """
  Validates that the Smithy CLI and configured paths are available.

  Options:
    * `:find_executable` - function of arity 1 used instead of `System.find_executable/1`
  """
  def check(config, opts \\ []) do
    find_executable = Keyword.get(opts, :find_executable, &System.find_executable/1)

    with :ok <- check_smithy_cli(find_executable),
         :ok <- check_models_or_config_file(config),
         :ok <- check_output_parent(config) do
      :ok
    end
  end

  defp check_smithy_cli(find_executable) do
    case find_executable.("smithy") do
      path when is_binary(path) ->
        :ok

      nil ->
        {:error,
         """
         smithy_beam: Smithy CLI not found on PATH.

         Install the Smithy CLI, then re-run compilation.
           macOS:  brew tap smithy-lang/tap && brew install smithy-cli
           docs:   https://smithy.io/2.0/guides/smithy-cli/cli_installation.html

         After install, verify with: smithy --help
         """}
    end
  end

  defp check_models_or_config_file(%{config_file: config_file} = config)
       when is_binary(config_file) do
    path = resolve(config.project_root, config_file)

    if File.exists?(path) do
      :ok
    else
      {:error, "smithy_beam: config_file not found: #{path}"}
    end
  end

  defp check_models_or_config_file(%{models: models} = config) when is_binary(models) do
    path = resolve(config.project_root, models)

    cond do
      File.dir?(path) ->
        :ok

      File.exists?(path) ->
        :ok

      true ->
        {:error, "smithy_beam: models path not found: #{path}"}
    end
  end

  defp check_models_or_config_file(_) do
    {:error, "smithy_beam: models path is required when config_file is not set"}
  end

  defp check_output_parent(%{output: output, project_root: root}) do
    path = resolve(root, output)
    parent = Path.dirname(path)

    case File.mkdir_p(parent) do
      :ok -> :ok
      {:error, reason} -> {:error, "smithy_beam: cannot create output parent #{parent}: #{inspect(reason)}"}
    end
  end

  defp resolve(root, path) do
    if Path.type(path) == :absolute do
      path
    else
      Path.expand(path, root)
    end
  end
end
