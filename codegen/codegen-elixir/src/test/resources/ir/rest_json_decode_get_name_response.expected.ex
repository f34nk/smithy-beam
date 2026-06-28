def decode_get_name_response(%RuntimeTypes.HttpResponse{
  status: 200,
  headers: headers,
  body: body
}) do
  decoded = if(body == "" or Kernel.is_nil(body), do: %{}, else: Jason.decode!(body))
  result = {:ok, %Types.GetNameOutput{name: Map.get(decoded, "name")}}
  result
end
def decode_get_name_response(%RuntimeTypes.HttpResponse{status: status, headers: headers, body: body}), do: decode_get_name_response_error(status, headers, body)