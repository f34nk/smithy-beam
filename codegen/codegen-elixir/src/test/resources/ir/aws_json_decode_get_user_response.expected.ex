@doc "Decode AWS JSON response for smithy.beam.test.awsjson11#GetUser."
@spec decode_get_user_response(map()) ::
        {:ok, Json11serviceTypes.GetUserOutput.t()} | {:error, term()}
def decode_get_user_response(%RuntimeTypes.HttpResponse{
  status: 200,
  body: body
}) do
  decoded = if(body == "" or Kernel.is_nil(body), do: %{}, else: Jason.decode!(body))
  {:ok, %Types.GetUserOutput{user_name: Map.get(decoded, "userName")}}
end
def decode_get_user_response(%RuntimeTypes.HttpResponse{status: status, headers: headers, body: body}), do: decode_get_user_response_error(status, headers, body)