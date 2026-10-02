defmodule SmokeElixirClient.MixProject do
  use Mix.Project

  def project do
    [
      app: :smoke_elixir_client,
      version: "0.1.0",
      elixir: "~> 1.14",
      compilers: [:smithy_beam] ++ Mix.compilers(),
      elixirc_paths: ["lib", "lib/generated"],
      start_permanent: Mix.env() == :prod,
      smithy_beam: [
        models: "model",
        output: "lib/generated",
        language: :elixir,
        kind: :client,
        edition: "2026",
        service: "smithy.beam.demo.basic#BasicService"
      ],
      deps: deps()
    ]
  end

  def application do
    [extra_applications: []]
  end

  defp deps do
    [
      {:smithy_beam, path: "../..", runtime: false}
    ]
  end
end
