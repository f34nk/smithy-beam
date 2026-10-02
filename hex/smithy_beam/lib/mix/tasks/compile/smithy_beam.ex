defmodule Mix.Tasks.Compile.SmithyBeam do
  use Mix.Task.Compiler

  @moduledoc """
  Runs Smithy code generation before the Elixir/Erlang compilers.

  Enable in the consumer `mix.exs`:

      compilers: [:smithy_beam] ++ Mix.compilers(),
      smithy_beam: [
        models: "model",
        output: "lib/generated",
        language: :elixir,
        kind: :client,
        edition: "2026"
      ]

  When the `:smithy_beam` project key is absent, this compiler is a no-op
  (including when compiling the `smithy_beam` package itself).
  """

  @recursive true

  def run(_args) do
    case Mix.Project.config()[:smithy_beam] do
      nil ->
        {:noop, []}

      _opts ->
        case SmithyBeam.Generate.run() do
          :ok ->
            {:ok, []}

          {:noop, _} ->
            {:noop, []}

          {:error, msg} ->
            Mix.shell().error(to_string(msg))
            {:error, []}
        end
    end
  end

  def clean do
    root = File.cwd!()
    scratch = Path.join(root, "_build/smithy_beam")
    File.rm_rf(scratch)

    case Mix.Project.config()[:smithy_beam] do
      opts when is_list(opts) ->
        output = Keyword.get(opts, :output)

        if is_binary(output) and output != "" do
          File.rm_rf(Path.expand(output, root))
        end

      _ ->
        :ok
    end

    :ok
  end
end
