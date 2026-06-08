defmodule CustomProtocolDemo.MixProject do
  use Mix.Project

  def project do
    [
      app: :custom_protocol_demo,
      version: "0.1.0",
      elixir: "~> 1.14",
      start_permanent: Mix.env() == :prod,
      deps: deps()
    ]
  end

  def application do
    [extra_applications: [:logger]]
  end

  defp deps do
    [
      {:req, "~> 0.5"}
    ]
  end
end
