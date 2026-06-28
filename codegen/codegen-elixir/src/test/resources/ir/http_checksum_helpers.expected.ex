defp headers_set(name, value, headers), do: List.keystore(name, 0, headers, {name, value})

defp checksum_header_encode(data) when is_binary(data), do: Base.encode64(data)

defp md5_hash(body), do: :crypto.hash(:md5, body)

defp sha256_hash(body), do: :crypto.hash(:sha256, body)

defp crc32_hash(body), do: <<:erlang.crc32(body) :: "32-big-unsigned-integer">>

defp crc32c_hash(body), do: :crypto.hash(:crc32c, body)

defp checksum_digest(body, "MD5"), do: md5_hash(body)
defp checksum_digest(body, "SHA256"), do: sha256_hash(body)
defp checksum_digest(body, "CRC32"), do: crc32_hash(body)
defp checksum_digest(body, "CRC32C"), do: crc32c_hash(body)

defp validate_response_checksum(_body, _headers, []), do: :ok
defp validate_response_checksum(
  body,
  headers,
  [header_name | rest]
) do
  List.keyfind(headers, header_name, 0)
  |> case do
    {_, expected} -> validate_checksum_match(body, header_name, expected)
    nil -> validate_response_checksum(body, headers, rest)
  end
end

defp validate_checksum_match(
  body,
  header_name,
  expected
) do
  algorithm = checksum_algorithm_from_header(header_name)
  computed = checksum_header_encode(checksum_digest(body, algorithm))
  if(computed == expected, do: :ok, else: {:error, {:checksum_mismatch, header_name}})
end

defp checksum_algorithm_from_header(header_name), do: String.upcase(String.replace_prefix(header_name, "x-amz-checksum-", ""))