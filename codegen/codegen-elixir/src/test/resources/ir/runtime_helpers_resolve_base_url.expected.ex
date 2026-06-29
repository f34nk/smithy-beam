@spec resolve_base_url(map()) :: String.t()
def resolve_base_url(config) do
  prefix = Map.fetch!(config, :endpoint_prefix)
  region = Map.get(config, :region, "us-east-1")
  "https://#{prefix}.#{region}.amazonaws.com"
end
