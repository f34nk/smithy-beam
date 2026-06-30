@spec decode_basic_item(nil | map()) :: nil | BasicItem.t()
def decode_basic_item(:nil), do: :nil
def decode_basic_item(map) when is_map(map) do
  %BasicItem{
    name: Map.get(map, "name", :nil),
    count: Map.get(map, "count", :nil)
  }
end
