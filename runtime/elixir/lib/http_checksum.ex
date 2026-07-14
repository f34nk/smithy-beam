defmodule HttpChecksum do
  @moduledoc false

  def headers_set(name, value, headers), do: List.keystore(name, 0, headers, {name, value})

  def checksum_header_encode(data) when is_binary(data), do: Base.encode64(data)

  def md5_hash(body), do: :crypto.hash(:md5, body)

  def sha256_hash(body), do: :crypto.hash(:sha256, body)

  def crc32_hash(body), do: :binary.encode_unsigned(:erlang.crc32(body), :big)

  def crc32c_hash(body), do: :crypto.hash(:crc32c, body)

  def crc64nvme_hash(_body),
    do: Kernel.raise(:ArgumentError, {:unsupported_checksum_algorithm, :crc64nvme})

  def xxhash64_hash(_body),
    do: Kernel.raise(:ArgumentError, {:unsupported_checksum_algorithm, :xxhash64})

  def xxhash3_hash(_body),
    do: Kernel.raise(:ArgumentError, {:unsupported_checksum_algorithm, :xxhash3})

  def xxhash128_hash(_body),
    do: Kernel.raise(:ArgumentError, {:unsupported_checksum_algorithm, :xxhash128})

  def checksum_digest(body, "MD5"), do: md5_hash(body)
  def checksum_digest(body, "SHA256"), do: sha256_hash(body)
  def checksum_digest(body, "CRC32"), do: crc32_hash(body)
  def checksum_digest(body, "CRC32C"), do: crc32c_hash(body)

  def validate_response_checksum(_body, _headers, []), do: :ok

  def validate_response_checksum(body, headers, [header_name | rest]) do
    List.keyfind(headers, header_name, 0)
    |> case do
      {_, expected} -> validate_checksum_match(body, header_name, expected)
      nil -> validate_response_checksum(body, headers, rest)
    end
  end

  defp validate_checksum_match(body, header_name, expected) do
    algorithm = checksum_algorithm_from_header(header_name)
    computed = checksum_header_encode(checksum_digest(body, algorithm))

    if computed == expected do
      :ok
    else
      {:error, {:checksum_mismatch, header_name}}
    end
  end

  defp checksum_algorithm_from_header(header_name),
    do: String.upcase(String.replace_prefix(header_name, "x-amz-checksum-", ""))
end
