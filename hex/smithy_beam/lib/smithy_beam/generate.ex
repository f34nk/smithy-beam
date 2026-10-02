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

  defp maybe_skip(config, opts) do
    if Keyword.get(opts, :force, false) do
      :ok
    else
      scratch = scratch_dir(config)
      manifest_path = Path.join(scratch, "manifest")
      dest = resolve(config.project_root, config.output)
      fingerprint = fingerprint(config)

      cond do
        not File.exists?(manifest_path) ->
          :ok

        not File.dir?(dest) ->
          :ok

        File.ls!(dest) == [] ->
          :ok

        File.read!(manifest_path) |> String.trim() == fingerprint ->
          {:noop, []}

        true ->
          :ok
      end
    end
  end

  defp do_generate(config, opts) do
    scratch = scratch_dir(config)
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
        File.write!(Path.join(scratch, "manifest"), fingerprint(config) <> "\n")
        :ok

      {:error, _} = error ->
        error
    end
  end

  defp fingerprint(config) do
    models = resolve(config.project_root, config.models || "")

    model_entries =
      cond do
        config.models && File.dir?(models) ->
          models
          |> Path.join("**/*")
          |> Path.wildcard()
          |> Enum.filter(&File.regular?/1)
          |> Enum.sort()
          |> Enum.map(fn path ->
            %{size: size, mtime: mtime} = File.stat!(path)
            stamp = :calendar.datetime_to_gregorian_seconds(mtime)
            "#{path}:#{size}:#{stamp}"
          end)

        config.models && File.regular?(models) ->
          %{size: size, mtime: mtime} = File.stat!(models)
          stamp = :calendar.datetime_to_gregorian_seconds(mtime)
          ["#{models}:#{size}:#{stamp}"]

        true ->
          []
      end

    payload = %{
      "plugin" => config.plugin,
      "models" => config.models,
      "output" => config.output,
      "codegen_version" => config.codegen_version,
      "edition" => config.edition,
      "service" => config.service,
      "name" => config.name,
      "protocol" => config.protocol,
      "package_version" => config.package_version,
      "maven_deps" => config.maven_deps,
      "config_file" => config.config_file,
      "package_version_hex" => SmithyBeam.version(),
      "model_entries" => model_entries
    }

    payload
    |> Jason.encode!()
    |> then(&:crypto.hash(:sha256, &1))
    |> Base.encode16(case: :lower)
  end

  defp scratch_dir(config), do: Path.join(config.project_root, "_build/smithy_beam")

  defp resolve(root, path) do
    if Path.type(path) == :absolute, do: path, else: Path.expand(path, root)
  end
end
