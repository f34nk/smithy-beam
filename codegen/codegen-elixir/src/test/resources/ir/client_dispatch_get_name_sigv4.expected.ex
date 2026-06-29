req = HttpServiceRestJson1.encode_get_name_request(input)
signed_req = case Map.get(config, :credentials) do
  nil -> req
  _ -> HttpServiceSigv4.sign(config, :get_name, req)
end
case RuntimeHttp.dispatch(config, signed_req) do
  {:ok, resp} -> HttpServiceRestJson1.decode_get_name_response(resp)
  {:error, reason} -> {:error, reason}
end
