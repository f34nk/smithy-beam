@doc "Decode HTTP request for smithy.beam.demo.http#GetName."
@spec decode_get_name_request(%RuntimeTypes.HttpRequest{}, map()) ::
        HttpServiceTypes.GetNameInput.t()
def decode_get_name_request(%RuntimeTypes.HttpRequest{query: query, headers: headers, body: body}, label_map) do
  %Types.GetNameInput{name: uri_decode(Map.get(label_map, "name"))}
end