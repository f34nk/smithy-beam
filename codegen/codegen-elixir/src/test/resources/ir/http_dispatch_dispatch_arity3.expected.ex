@spec dispatch(module(), map(), RuntimeTypes.HttpRequest.t()) ::
        {:ok, RuntimeTypes.HttpResponse.t()} | {:error, term()}
def dispatch(http_client, config, req = %RuntimeTypes.HttpRequest{}) do
  dispatch_signed(http_client, config, req)
end
