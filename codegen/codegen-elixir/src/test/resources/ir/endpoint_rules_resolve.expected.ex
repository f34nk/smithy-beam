@spec resolve(map(), map()) :: {:ok, %{url: String.t()}} | {:error, term()}
def resolve(
  config,
  params
) do
  AwsEndpointRules.evaluate(RuntimeTypes.endpoint_rule_set(), merge_params(config, params))
end