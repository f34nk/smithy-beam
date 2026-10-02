defmodule SmithyBeam.Copy do
  @moduledoc false

  @doc """
  Copies plugin artifacts from a Smithy scratch output tree into `dest_dir`.

  Expects `scratch_output` to contain `source/<plugin_id>/...` as produced by
  `smithy build --output <scratch_output>`.
  """
  def sync!(scratch_output, plugin_id, dest_dir) do
    source = Path.join([scratch_output, "source", plugin_id])

    unless File.dir?(source) do
      raise ArgumentError, "smithy plugin output not found: #{source}"
    end

    File.rm_rf!(dest_dir)
    File.mkdir_p!(dest_dir)

    source
    |> File.ls!()
    |> Enum.sort()
    |> Enum.map(fn name ->
      from = Path.join(source, name)
      to = Path.join(dest_dir, name)

      if File.dir?(from) do
        File.cp_r!(from, to)
      else
        File.cp!(from, to)
      end

      to
    end)
  end
end
