Checksum0 = crypto:hash(md5, Body),
HeadersWithChecksum = headers_set(<<"Content-MD5">>, checksum_header_encode(Checksum0), Headers)

HeadersWithChecksum = case ChecksumAlgorithm of
    undefined -> Headers;
    crc32c ->
        Checksum = crc32c_hash(Body),
        headers_set(<<"x-amz-checksum-crc32c">>, checksum_header_encode(Checksum), Headers);
    sha256 ->
        Checksum = crypto:hash(sha256, Body),
        headers_set(<<"x-amz-checksum-sha256">>, checksum_header_encode(Checksum), Headers);
    Other -> error({unsupported_checksum_algorithm, Other})
end
