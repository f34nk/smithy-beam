case validate_checksum(body, headers, ["x-checksum-a", "x-checksum-b"]) do
  :ok -> {:ok, output}
  {:error, reason} -> {:error, {:checksum_failed, reason}}
end
