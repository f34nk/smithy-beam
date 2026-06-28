@doc "Decode AWS JSON request for smithy.beam.test.awsjson11#GetUser."
@spec decode_get_user_request(map()) :: Json11serviceTypes.GetUserInput.t()
def decode_get_user_request(%RuntimeTypes.HttpRequest{body: body}) do
  decoded = decode_json_body(body)
  %Types.GetUserInput{user_name: Map.get(decoded, "userName")}
end