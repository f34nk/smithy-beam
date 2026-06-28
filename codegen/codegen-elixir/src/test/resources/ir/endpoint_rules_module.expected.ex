defmodule EndpointRulesServiceEndpoints do
  @moduledoc "Generated endpoint rule resolver for Smithy service clients."
  alias RuntimeTypes, as: RuntimeTypes

  @spec resolve(map(), map()) :: {:ok, %{url: String.t()}} | {:error, term()}
  def resolve(config, params) do
    AwsEndpointRules.evaluate(RuntimeTypes.endpoint_rule_set(), merge_params(config, params))
  end

  defp merge_params(config, params) do
    config_params = config_to_rule_params(config)
    client_params = client_context_params(config)
    Map.merge(Map.merge(config_params, client_params), params)
  end

  defp config_to_rule_params(config) do
    case Map.get(config, :region) do
      nil -> %{}

      value -> %{"Region" => value}
    end
  end

  defp client_context_params(config) do
    [optional_param(config, :region, "Region"), optional_param(config, :bucket, "Bucket")]
    |> Enum.reduce(%{}, fn map, acc -> Map.merge(acc, map) end)
  end

  defp optional_param(
    config,
    key,
    rule_key
  ) do
    case Map.get(config, key) do
      nil -> %{}

      value -> %{rule_key => value}
    end
  end
end