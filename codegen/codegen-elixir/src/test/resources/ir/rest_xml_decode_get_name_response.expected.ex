@spec decode_get_name_response(map()) ::
        {:ok, HttpServiceTypes.GetNameOutput.t()} | {:error, term()}
def decode_get_name_response(%{:status => 200, :headers => headers, :body => body}) do
  case parse_xml_root(body, "GetNameOutput") do
    {:ok, root} -> {:ok, %Types.GetNameOutput{name: xml_child_text(root, "Name")}}
    {:error, reason} -> {:error, reason}
  end
end
def decode_get_name_response(%{:status => status, :body => body}) do
  {:error, {:unknown_error, status, body}}
end
