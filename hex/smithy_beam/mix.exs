defmodule SmithyBeam.MixProject do
  use Mix.Project

  @version "0.1.0"
  @source_url "https://github.com/f34nk/smithy-beam"

  def project do
    [
      app: :smithy_beam,
      version: @version,
      elixir: "~> 1.14",
      start_permanent: Mix.env() == :prod,
      deps: deps(),
      description: description(),
      package: package(),
      docs: [
        main: "readme",
        extras: ["README.md"]
      ],
      name: "smithy_beam",
      source_url: @source_url,
      test_ignore_filters: [~r/test\/support\//]
    ]
  end

  def application do
    [
      extra_applications: []
    ]
  end

  defp deps do
    [
      {:jason, "~> 1.4"},
      {:ex_doc, ">= 0.0.0", only: :dev, runtime: false}
    ]
  end

  defp description do
    "Mix and rebar3 helper that runs the Smithy CLI to generate Elixir and Erlang clients from Smithy models."
  end

  defp package do
    [
      name: "smithy_beam",
      licenses: ["Apache-2.0"],
      links: %{"GitHub" => @source_url},
      files: ~w(lib src mix.exs rebar.config README.md LICENSE),
      build_tools: ["mix", "rebar3"]
    ]
  end
end
