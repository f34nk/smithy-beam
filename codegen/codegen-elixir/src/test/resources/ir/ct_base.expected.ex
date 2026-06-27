defp ct_base(ct) do
  case String.split(ct, ";") do
    [base | _] -> base
    _ -> ct
  end
end