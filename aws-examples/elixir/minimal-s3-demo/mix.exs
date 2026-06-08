defmodule AwsS3.MixProject do
  use Mix.Project

  def project do
    [
      app: :aws_s3,
      version: "0.1.0",
      elixir: "~> 1.14",
      start_permanent: Mix.env() == :prod,
      deps: deps()
    ]
  end

  def application do
    [extra_applications: [:logger, :inets, :ssl, :xmerl]]
  end

  defp deps do
    [
      {:req, "~> 0.5"},
      {:aws_signature, "~> 0.3.2"}
    ]
  end
end
