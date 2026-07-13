checksum0 = :crypto.hash(:md5, body)
headers = HttpChecksum.headers_set("Content-MD5", HttpChecksum.checksum_header_encode(checksum0), headers)

headers = case input.checksum_algorithm do
  nil -> headers
  :crc32c ->
    checksum = HttpChecksum.crc32c_hash(body)
    HttpChecksum.headers_set("x-amz-checksum-crc32c", HttpChecksum.checksum_header_encode(checksum), headers)
  :sha256 ->
    checksum = :crypto.hash(:sha256, body)
    HttpChecksum.headers_set("x-amz-checksum-sha256", HttpChecksum.checksum_header_encode(checksum), headers)
  other -> Kernel.raise(:ArgumentError, {:unsupported_checksum_algorithm, other})
end
