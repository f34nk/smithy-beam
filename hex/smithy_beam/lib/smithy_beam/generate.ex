defmodule SmithyBeam.Generate do
  @moduledoc false

  alias SmithyBeam.BuildJson
  alias SmithyBeam.Cli
  alias SmithyBeam.Config
  alias SmithyBeam.Copy
  alias SmithyBeam.Prereqs

  @doc """
  Runs code generation for the current Mix project or an explicit config.

  Options:
    * `:config` - keyword list passed to `SmithyBeam.Config.parse/1`
    * `:prereqs_opts` - options forwarded to `SmithyBeam.Prereqs.check/2`
    * `:cli_opts` - options forwarded to `SmithyBeam.Cli.run_build/5`
    * `:force` - when true, skip noop short-circuit based on manifest
  """
  def run(opts \\ []) do
    with {:ok, config} <- Config.parse(Keyword.get(opts, :config)),
         :ok <- Prereqs.check(config, Keyword.get(opts, :prereqs_opts, [])),
         :ok <- maybe_skip(config, opts),
         :ok <- do_generate(config, opts) do
      :ok
    else
      {:noop, _} = noop -> noop
      {:error, _} = error -> error
    end
  end

  defp maybe_skip(_config, opts) do
    if Keyword.get(opts, :force, false) do
      :ok
    else
      # Manifest-based noop is filled in when incremental support lands.
      :ok
    end
  end

  defp do_generate(config, opts) do
    scratch = Path.join(config.project_root, "_build/smithy_beam")
    scratch_out = Path.join(scratch, "out")
    File.mkdir_p!(scratch)
    File.mkdir_p!(scratch_out)

    config_path =
      if config.config_file do
        resolve(config.project_root, config.config_file)
      else
        path = Path.join(scratch, "smithy-build.json")
        BuildJson.write!(config, path)
        path
      end

    dest = resolve(config.project_root, config.output)

    case Cli.run_build(
           config.project_root,
           config_path,
           scratch_out,
           config.plugin,
           Keyword.get(opts, :cli_opts, [])
         ) do
      :ok ->
        Copy.sync!(scratch_out, config.plugin, dest)
        write_manifest(scratch, config)
        :ok

      {:error, _} = error ->
        error
    end
  end

  defp write_manifest(scratch, config) do
    payload = %{
      "plugin" => config.plugin,
      "models" => config.models,
      "output" => config.output,
      "codegen_version" => config.codegen_version,
      "edition" => config.edition
    }

    File.write!(Path.join(scratch, "manifest"), Jason.encode!(payload, pretty: true) <> "\n")
  end

  defp resolve(root, path) do
    if Path.type(path) == :absolute, do: path, else: Path.expand(path, root)
  end
end
