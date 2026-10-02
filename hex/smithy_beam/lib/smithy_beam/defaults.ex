defmodule SmithyBeam.Defaults do
  @moduledoc false

  @codegen_version "0.3.1"

  @common_maven_deps [
    "software.amazon.smithy:smithy-aws-traits:1.64.0",
    "software.amazon.smithy:smithy-aws-endpoints:1.64.0",
    "software.amazon.smithy:smithy-rules-engine:1.70.0",
    "software.amazon.smithy:smithy-aws-smoke-test-model:1.64.0",
    "software.amazon.smithy:smithy-aws-iam-traits:1.64.0",
    "software.amazon.smithy:smithy-waiters:1.64.0"
  ]

  @plugins %{
    {:elixir, :client} => "elixir-client-codegen",
    {:elixir, :server} => "elixir-server-codegen",
    {:elixir, :types} => "elixir-types-codegen",
    {:erlang, :client} => "erlang-client-codegen",
    {:erlang, :server} => "erlang-server-codegen",
    {:erlang, :types} => "erlang-types-codegen"
  }

  def codegen_version, do: @codegen_version

  def common_maven_deps, do: @common_maven_deps

  def default_output(:elixir), do: "lib/generated"
  def default_output(:erlang), do: "src/generated"

  def codegen_artifact(:elixir), do: "codegen-elixir"
  def codegen_artifact(:erlang), do: "codegen-erlang"

  def codegen_coordinate(language, version \\ codegen_version()) do
    "io.github.f34nk.smithy.beam:#{codegen_artifact(language)}:#{version}"
  end

  def plugin_id(language, kind) do
    Map.fetch!(@plugins, {language, kind})
  end

  def known_plugin?({language, kind}), do: Map.has_key?(@plugins, {language, kind})
end
