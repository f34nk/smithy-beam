defmodule AwsEndpointRules do
  @moduledoc """
  Temporary stub endpoint rules evaluator emitted by smithy-beam codegen.

  The rule set argument is ignored for now. Endpoint resolution uses a minimal placeholder
  until a full AWS rules engine runtime is available.
  """

  @spec evaluate(map(), map()) :: {:ok, %{url: String.t(), headers: map()}} | {:error, term()}
  def evaluate(_rule_set, params) do
    region = Map.get(params, "Region") || Map.get(params, :Region)

    case region do
      nil -> {:error, "Invalid Configuration: Missing Region"}
      value -> {:ok, %{url: "https://ec2.#{value}.amazonaws.com", headers: %{}}}
    end
  end
end
