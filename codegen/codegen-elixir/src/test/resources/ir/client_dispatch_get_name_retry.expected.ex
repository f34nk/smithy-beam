retry_opts = Map.get(config, :retry, [])
RuntimeHttp.with_retry(
  fn ->
    req = HttpServiceRestJson1.encode_get_name_request(input)
    case RuntimeHttp.dispatch(config, req) do
      {:ok, resp} -> HttpServiceRestJson1.decode_get_name_response(resp)
      {:error, reason} -> {:error, reason}
    end
  end,
  Keyword.merge([{:should_retry, &HttpServiceClient.should_retry?/1}], retry_opts)
)
