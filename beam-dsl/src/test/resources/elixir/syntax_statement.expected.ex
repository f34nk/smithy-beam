req = Codec.encode_request(input)

case Transport.dispatch(config, req) do
  {:ok, resp} -> Codec.decode_response(resp)
  {:error, reason} -> {:error, reason}
end
