defmodule SmithyBeam.Cli do
  @moduledoc false

  @doc """
  Runs `smithy build` for the given config and plugin.

  Options:
    * `:cmd` - function `(binary, [binary], keyword) -> {binary, non_neg_integer}`
      used instead of `System.cmd/3` (for tests)
    * `:find_executable` - used to locate `smithy` (defaults to `System.find_executable/1`)
  """
  def run_build(project_root, config_path, output_path, plugin_id, opts \\ []) do
    find_executable = Keyword.get(opts, :find_executable, &System.find_executable/1)
    cmd_fun = Keyword.get(opts, :cmd, &System.cmd/3)

    case find_executable.("smithy") do
      nil ->
        {:error, "smithy executable not found on PATH"}

      smithy ->
        args = [
          "build",
          "--config",
          config_path,
          "--output",
          output_path,
          "--plugin",
          plugin_id
        ]

        {output, status} =
          cmd_fun.(smithy, args, cd: project_root, stderr_to_stdout: true)

        if status == 0 do
          :ok
        else
          {:error,
           """
           smithy_beam: smithy build failed (exit #{status})

           #{String.trim(output)}
           """}
        end
    end
  end
end
