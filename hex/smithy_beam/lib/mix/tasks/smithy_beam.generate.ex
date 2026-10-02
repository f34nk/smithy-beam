defmodule Mix.Tasks.SmithyBeam.Generate do
  use Mix.Task

  @shortdoc "Generate BEAM sources from Smithy models"

  @moduledoc """
  Runs Smithy code generation using the `:smithy_beam` project configuration.

      mix smithy_beam.generate
      mix smithy_beam.generate --force
  """

  @switches [force: :boolean]

  @impl true
  def run(args) do
    {opts, _} = OptionParser.parse!(args, strict: @switches)
    force = Keyword.get(opts, :force, false)

    case SmithyBeam.Generate.run(force: force) do
      :ok ->
        Mix.shell().info("smithy_beam: generation complete")
        :ok

      {:noop, _} ->
        Mix.shell().info("smithy_beam: generation up to date")
        :ok

      {:error, msg} ->
        Mix.raise(to_string(msg))
    end
  end
end
