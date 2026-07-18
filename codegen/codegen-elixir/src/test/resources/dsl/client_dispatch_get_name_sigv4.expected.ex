req = HttpServiceRestJson1.encode_get_name_request(input)
signed_req = sign_request(config, :get_name, req)

case RuntimeHttp.dispatch(config, signed_req) do
  {:ok, resp} -> HttpServiceRestJson1.decode_get_name_response(resp)
  {:error, reason} -> {:error, reason}
end
