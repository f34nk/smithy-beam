defmodule SmithyBeam do
  @moduledoc """
  Build-time helper that invokes the Smithy CLI for BEAM code generation.
  """

  @version Mix.Project.config()[:version]

  def version, do: @version
end
