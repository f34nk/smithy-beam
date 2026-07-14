case HttpChecksum.validate_response_checksum(body, headers, ["x-amz-checksum-crc32c", "x-amz-checksum-sha256"]) do
  :ok -> {:ok, output}
  {:error, reason} -> {:error, {:checksum_validation_failed, reason}}
end
