@doc "Encode AWS JSON response for smithy.beam.test.awsjson11#GetUser."
@spec encode_get_user_response(Json11serviceTypes.GetUserOutput.t()) :: map()
def encode_get_user_response(output = %Types.GetUserOutput{}) do
  body_map =
    %{"userName" => output.user_name}
    |> Enum.reject(fn {_, v} -> Kernel.is_nil(v) end)
    |> Map.new()
  body = Jason.encode!(body_map)
  headers = [{"Content-Type", "application/x-amz-json-1.1"}]
  %{
    status: 200,
    headers: headers,
    body: body
  }
end