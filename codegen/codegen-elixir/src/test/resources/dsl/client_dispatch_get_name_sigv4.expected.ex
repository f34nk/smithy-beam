req = HttpServiceRestJson1.encode_get_name_request(input)
signed_req =
  case Map.get(config, :credentials) do
    nil ->
      case :aws_credentials.get_credentials() do
        :undefined ->
          req

        creds0 ->
          creds =
            %{
              access_key_id: Map.get(creds0, :access_key_id),
              secret_access_key: Map.get(creds0, :secret_access_key),
              session_token: Map.get(creds0, :token)
            }
          AwsSigv4.sign(Map.put(config, :credentials, creds), :get_name, req)
      end

    _ ->
      AwsSigv4.sign(config, :get_name, req)
  end

case RuntimeHttp.dispatch(config, signed_req) do
  {:ok, resp} -> HttpServiceRestJson1.decode_get_name_response(resp)
  {:error, reason} -> {:error, reason}
end
