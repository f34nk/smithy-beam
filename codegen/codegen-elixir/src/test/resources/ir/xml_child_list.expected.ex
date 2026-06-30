defp xml_child_list(parent, nil, item_name) do
  parent
  |> element_content()
  |> Enum.filter(fn item -> is_element(item) and element_name(item) == item_name end)
  |> Enum.map(
    fn
      item ->
        case element_text(item) do
          [] -> nil
          [text | _] -> List.to_string(text)
        end
    end
  )
  |> Enum.reject(fn x -> Kernel.is_nil(x) end)
end
defp xml_child_list(parent, list_name, item_name) do
  case find_element(list_name, element_content(parent)) do
    nil -> nil
    list_element ->
      list_element
      |> element_content()
      |> Enum.filter(fn item -> is_element(item) and element_name(item) == item_name end)
      |> Enum.map(
        fn
          item ->
            case element_text(item) do
              [] -> nil
              [text | _] -> List.to_string(text)
            end
        end
      )
      |> Enum.reject(fn x -> Kernel.is_nil(x) end)
  end
end
