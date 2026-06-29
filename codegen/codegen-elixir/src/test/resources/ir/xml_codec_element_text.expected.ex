defp element_text({:xmlElement, _, _, _, _, _, _, _, content, _, _, _}) do
  Enum.flat_map(content, & collect_text/1)
end
defp element_text(_), do: []