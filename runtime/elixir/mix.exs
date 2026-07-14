defmodule SmithyBeamRuntime.MixProject do
  use Mix.Project

  def project do
    [
      app: :smithy_beam_runtime,
      version: "0.1.0",
      elixir: "~> 1.14",
      test_ignore_filters: [~r/test\/support\//],
      deps: deps()
    ]
  end

  defp deps do
    [
      {:req, "~> 0.5", only: :test},
      {:aws_signature, "~> 0.3.2", only: :test},
      {:jason, "~> 1.4", only: :test}
    ]
  end
end
