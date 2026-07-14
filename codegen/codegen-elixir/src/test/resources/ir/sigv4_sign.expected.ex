@spec sign(map(), atom(), RuntimeTypes.HttpRequest.t()) :: RuntimeTypes.HttpRequest.t()
def sign(config, operation, request) do
  credentials = Map.fetch!(config, :credentials)
  region = Map.get(config, :region, "us-east-1")
  service = Map.fetch!(config, :signing_name)
  unsigned = Map.get(config, {:unsigned_payload, operation}, false)
  opts =
    %{
      unsigned_payload: unsigned,
      endpoint_host: Utils.endpoint_host_from_config(config)
    }
  sign_request(request, credentials, region, service, opts)
end
