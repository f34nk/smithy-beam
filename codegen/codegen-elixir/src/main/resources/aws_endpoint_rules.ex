defmodule AwsEndpointRules do
  @moduledoc false

  @spec evaluate(map(), map()) :: {:ok, %{url: String.t(), headers: map()}} | {:error, term()}
  def evaluate(_rule_set, params) do
    region = Map.get(params, "Region") || Map.get(params, :Region)

    case region do
      nil -> {:error, "Invalid Configuration: Missing Region"}
      value -> {:ok, %{url: "https://ec2.#{value}.amazonaws.com", headers: %{}}}
    end
  end
end
