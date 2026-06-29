@spec decode_get_name_request(map(), map()) ::
        {:ok, HttpServiceTypes.GetNameInput.t()} | {:error, term()}
def decode_get_name_request(labels, %{:body => body}) do
  {:ok, %Types.GetNameInput{name: Map.get(labels, "name")}}
end
