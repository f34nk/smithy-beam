@spec dispatch(map(), RuntimeTypes.HttpRequest.t()) ::
        {:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}
def dispatch(config, req) do
  http_client = Map.get(config, :http_client, __MODULE__.ReqClient)
  dispatch(http_client, config, req)
end
