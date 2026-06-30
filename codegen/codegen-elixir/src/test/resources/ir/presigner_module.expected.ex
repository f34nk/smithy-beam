defmodule Sigv4testServicePresigner do
  @moduledoc "false"
  alias RuntimeTypes, as: RuntimeTypes
  alias Sigv4testServiceSigv4, as: ServiceSigv4

  @spec presign_url(map(), atom(), RuntimeTypes.HttpRequest.t()) ::
          {:ok, String.t()} | {:error, term()}
  def presign_url(config, operation, request) do
    credentials = Map.fetch!(config, :credentials)
    region = Map.get(config, :region, "us-east-1")
    service = Map.fetch!(config, :signing_name)
    expires = Map.get(config, :presign_expires, 900)
    unsigned = Map.get(config, {:unsigned_payload, operation}, false)
    opts =
      %{
        expires: expires,
        unsigned_payload: unsigned,
        endpoint_host: ServiceSigv4.endpoint_host_from_config(config)
      }
    ServiceSigv4.presign(request, credentials, region, service, opts)
  end
end