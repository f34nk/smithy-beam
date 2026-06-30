defp generate_uuid do
  <<a::32, b::16, _::4, c::12, _::2, d::14, e::48>> = :crypto.strong_rand_bytes(16)
  <<a::32, b::16, 4::4, c::12, 2::2, d::14, e::48>>
  |> Base.encode16(case: :lower)
  |> then(fn hex ->
    <<part_a::8, part_b::4, part_c::4, part_d::4, part_e::12>> = hex
    "#{part_a}-#{part_b}-#{part_c}-#{part_d}-#{part_e}"
  end)
end