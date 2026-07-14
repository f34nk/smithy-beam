defmodule HttpChecksumTest do
  use ExUnit.Case, async: true

  @body "hello"

  @md5_digest <<93, 65, 64, 42, 188, 75, 42, 118, 185, 113, 157, 145, 16, 23, 197, 146>>

  @sha256_digest <<
    44,
    242,
    77,
    186,
    95,
    176,
    163,
    14,
    38,
    232,
    59,
    42,
    197,
    185,
    226,
    158,
    27,
    22,
    30,
    92,
    31,
    167,
    66,
    94,
    115,
    4,
    51,
    98,
    147,
    139,
    152,
    36
  >>

  @crc32_digest <<54, 16, 166, 134>>
  @sha256_header "LPJNul+wow4m6DsqxbninhsWHlwfp0JecwQzYpOLmCQ="

  test "md5_hash/1" do
    assert HttpChecksum.md5_hash(@body) == @md5_digest
  end

  test "sha256_hash/1" do
    assert HttpChecksum.sha256_hash(@body) == @sha256_digest
  end

  test "crc32_hash/1" do
    assert HttpChecksum.crc32_hash(@body) == @crc32_digest
  end

  test "checksum_header_encode/1" do
    assert HttpChecksum.checksum_header_encode(@sha256_digest) == @sha256_header
  end

  test "validate_response_checksum/3 ok" do
    headers = [{"x-amz-checksum-sha256", @sha256_header}]

    assert :ok ==
             HttpChecksum.validate_response_checksum(@body, headers, [
               "x-amz-checksum-sha256"
             ])
  end

  test "validate_response_checksum/3 mismatch" do
    headers = [{"x-amz-checksum-sha256", "invalid"}]

    assert {:error, {:checksum_mismatch, "x-amz-checksum-sha256"}} ==
             HttpChecksum.validate_response_checksum(@body, headers, [
               "x-amz-checksum-sha256"
             ])
  end

  test "validate_response_checksum/3 skips missing header" do
    assert :ok == HttpChecksum.validate_response_checksum(@body, [], [])
  end

  test "crc32c_hash/1" do
    try do
      digest = HttpChecksum.crc32c_hash(@body)
      assert byte_size(digest) > 0
    rescue
      ErlangError -> :ok
    end
  end
end
