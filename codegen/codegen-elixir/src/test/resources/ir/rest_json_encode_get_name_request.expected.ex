@spec encode_get_name_request(HttpServiceTypes.GetNameInput.t()) :: %RuntimeTypes.HttpRequest{}
def encode_get_name_request(input) do
  path = "/names/" <> uri_encode(input.name)
  query = %{}
  headers = [{"Content-Type", "application/json"}]
  body = ""
  %RuntimeTypes.HttpRequest{
    method: "GET",
    path: path,
    query: query,
    headers: headers,
    body: body
  }
end