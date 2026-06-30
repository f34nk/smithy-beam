req = HttpServiceRestJson1.encode_get_name_request(input)
case RuntimeHttp.dispatch(config, req) do
  {:ok, resp} -> HttpServiceRestJson1.decode_get_name_response(resp)
  {:error, reason} -> {:error, reason}
end
