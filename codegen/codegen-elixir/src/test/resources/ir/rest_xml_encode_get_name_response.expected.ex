@spec encode_get_name_response(HttpServiceTypes.GetNameOutput.t()) :: map()
def encode_get_name_response(output) do
  headers = [{"Content-Type", "application/xml"}]
  member_map = %{"Name" => output.name}
  body = encode_xml(%{"GetNameOutput" => member_map}, xml_namespace())
  %RuntimeTypes.HttpResponse{
    status: 200,
    headers: headers,
    body: body
  }
end
