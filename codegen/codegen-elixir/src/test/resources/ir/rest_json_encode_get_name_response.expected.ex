@doc "Encode response for smithy.beam.demo.http#GetName."
@spec encode_get_name_response(HttpServiceTypes.GetNameOutput.t()) :: map()
def encode_get_name_response(output = %Types.GetNameOutput{name: name}) do
  body_map =
    %{"name" => output.name}
    |> Enum.reject(fn {_, v} -> Kernel.is_nil(v) end)
    |> Map.new()
  body = Jason.encode!(body_map)
  headers = [{"Content-Type", "application/json"}]
  %{
    status: 200,
    headers: headers,
    body: body
  }
end