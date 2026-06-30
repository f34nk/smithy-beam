@doc "Encode AWS JSON request for smithy.beam.test.awsjson11#GetUser."
@spec encode_get_user_request(Json11serviceTypes.GetUserInput.t()) :: %RuntimeTypes.HttpRequest{}
def encode_get_user_request(input = %Types.GetUserInput{}) do
  body_map =
    %{"userName" => input.user_name}
    |> Enum.reject(fn {_, v} -> Kernel.is_nil(v) end)
    |> Map.new()
  body = Jason.encode!(body_map)
  %RuntimeTypes.HttpRequest{
    method: "POST",
    path: "/",
    query: %{},
    headers: [{"Content-Type", "application/x-amz-json-1.1"}, {"X-Amz-Target", "Json11Service.GetUser"}],
    body: body
  }
end