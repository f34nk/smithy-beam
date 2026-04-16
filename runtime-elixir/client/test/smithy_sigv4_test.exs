defmodule SmithySigV4Test do
  use ExUnit.Case, async: true

  # -------------------------------------------------------------------------
  # hash_sha256/1
  # -------------------------------------------------------------------------

  describe "hash_sha256/1" do
    test "empty string matches AWS test vector" do
      expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
      assert SmithySigV4.hash_sha256("") == expected
    end

    test "known input 'hello'" do
      expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
      assert SmithySigV4.hash_sha256("hello") == expected
    end

    test "JSON body produces 64-character lowercase hex" do
      result = SmithySigV4.hash_sha256(~s({"TableName":"Test"}))
      assert byte_size(result) == 64
      assert hex_string?(result)
    end

    test "is deterministic" do
      assert SmithySigV4.hash_sha256("data") == SmithySigV4.hash_sha256("data")
    end
  end

  # -------------------------------------------------------------------------
  # hmac_sha256/2
  # -------------------------------------------------------------------------

  describe "hmac_sha256/2" do
    test "returns 32 bytes" do
      assert byte_size(SmithySigV4.hmac_sha256("key", "data")) == 32
    end

    test "returns binary (not hex)" do
      assert is_binary(SmithySigV4.hmac_sha256("key", "data"))
    end

    test "is deterministic" do
      assert SmithySigV4.hmac_sha256("key", "data") == SmithySigV4.hmac_sha256("key", "data")
    end

    test "empty data" do
      assert byte_size(SmithySigV4.hmac_sha256("secret", "")) == 32
    end

    test "empty key" do
      assert byte_size(SmithySigV4.hmac_sha256("", "data")) == 32
    end
  end

  # -------------------------------------------------------------------------
  # signed_header_list/1
  # -------------------------------------------------------------------------

  describe "signed_header_list/1" do
    test "single header lowercased" do
      assert SmithySigV4.signed_header_list([{"Host", "example.com"}]) == "host"
    end

    test "multiple headers sorted and semicolon-joined" do
      headers = [
        {"X-Amz-Date", "20230101T120000Z"},
        {"Host", "s3.amazonaws.com"},
        {"Content-Type", "application/json"}
      ]

      assert SmithySigV4.signed_header_list(headers) == "content-type;host;x-amz-date"
    end

    test "mixed-case headers lowercased and sorted" do
      headers = [{"HOST", "example.com"}, {"X-AMZ-DATE", "20230101T120000Z"}]
      assert SmithySigV4.signed_header_list(headers) == "host;x-amz-date"
    end
  end

  # -------------------------------------------------------------------------
  # canonicalize_headers/1
  # -------------------------------------------------------------------------

  describe "canonicalize_headers/1" do
    test "single header formatted as name:value\\n" do
      assert SmithySigV4.canonicalize_headers([{"Host", "example.com"}]) ==
               "host:example.com\n"
    end

    test "multiple headers sorted alphabetically" do
      headers = [{"X-Amz-Date", "20230101T120000Z"}, {"Host", "s3.amazonaws.com"}]
      expected = "host:s3.amazonaws.com\nx-amz-date:20230101T120000Z\n"
      assert SmithySigV4.canonicalize_headers(headers) == expected
    end

    test "trims whitespace from values" do
      headers = [{"Host", "  example.com  "}, {"X-Custom", "  value with spaces  "}]
      result = SmithySigV4.canonicalize_headers(headers)
      assert result == "host:example.com\nx-custom:value with spaces\n"
    end

    test "lowercases header names" do
      headers = [{"HOST", "example.com"}, {"Content-TYPE", "application/json"}]
      expected = "content-type:application/json\nhost:example.com\n"
      assert SmithySigV4.canonicalize_headers(headers) == expected
    end
  end

  # -------------------------------------------------------------------------
  # canonicalize_query_string/1
  # -------------------------------------------------------------------------

  describe "canonicalize_query_string/1" do
    test "nil returns empty string" do
      assert SmithySigV4.canonicalize_query_string(nil) == ""
    end

    test "empty string returns empty string" do
      assert SmithySigV4.canonicalize_query_string("") == ""
    end

    test "single parameter preserved" do
      assert SmithySigV4.canonicalize_query_string("key=value") == "key=value"
    end

    test "multiple parameters sorted by key" do
      result = SmithySigV4.canonicalize_query_string("zebra=last&apple=first&middle=center")
      assert result == "apple=first&middle=center&zebra=last"
    end

    test "spaces in values percent-encoded as %20" do
      result = SmithySigV4.canonicalize_query_string("key=value with spaces")
      assert String.contains?(result, "%20")
    end
  end

  # -------------------------------------------------------------------------
  # create_canonical_request/4
  # -------------------------------------------------------------------------

  describe "create_canonical_request/4" do
    test "GET request contains all components" do
      headers = [{"Host", "s3.amazonaws.com"}, {"X-Amz-Date", "20230101T120000Z"}]
      result = SmithySigV4.create_canonical_request("GET", "https://s3.amazonaws.com/mybucket/mykey", headers, "")

      assert String.contains?(result, "GET")
      assert String.contains?(result, "/mybucket/mykey")
      assert String.contains?(result, "host:s3.amazonaws.com")
      # empty body hash
      assert String.contains?(result, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    end

    test "POST with body produces non-empty payload hash" do
      headers = [{"Host", "dynamodb.us-west-2.amazonaws.com"}]
      body = ~s({"TableName":"Test"})
      result = SmithySigV4.create_canonical_request("POST", "https://dynamodb.us-west-2.amazonaws.com/", headers, body)

      assert String.contains?(result, "POST")
      # body is not empty, so hash differs from empty hash
      refute String.contains?(result, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    end

    test "query parameters appear in the canonical request" do
      headers = [{"Host", "s3.amazonaws.com"}]
      result = SmithySigV4.create_canonical_request("GET", "https://s3.amazonaws.com/bucket?prefix=photos&max-keys=100", headers, "")

      assert String.contains?(result, "max-keys")
      assert String.contains?(result, "prefix")
    end

    test "root path normalises to /" do
      headers = [{"Host", "s3.amazonaws.com"}]
      result = SmithySigV4.create_canonical_request("GET", "https://s3.amazonaws.com/", headers, "")
      lines = String.split(result, "\n")
      assert Enum.at(lines, 1) == "/"
    end

    test "missing path normalises to /" do
      headers = [{"Host", "s3.amazonaws.com"}]
      result = SmithySigV4.create_canonical_request("GET", "https://s3.amazonaws.com", headers, "")
      lines = String.split(result, "\n")
      assert Enum.at(lines, 1) == "/"
    end
  end

  # -------------------------------------------------------------------------
  # credential_scope/3
  # -------------------------------------------------------------------------

  describe "credential_scope/3" do
    test "basic format YYYYMMDD/region/service/aws4_request" do
      result = SmithySigV4.credential_scope("20230101", "us-east-1", "s3")
      assert result == "20230101/us-east-1/s3/aws4_request"
    end

    test "different region" do
      result = SmithySigV4.credential_scope("20230515", "eu-west-1", "dynamodb")
      assert result == "20230515/eu-west-1/dynamodb/aws4_request"
    end

    test "various services" do
      for service <- ["s3", "dynamodb", "ec2", "lambda"] do
        result = SmithySigV4.credential_scope("20230101", "us-east-1", service)
        assert result == "20230101/us-east-1/#{service}/aws4_request"
      end
    end
  end

  # -------------------------------------------------------------------------
  # iso8601_datetime/0
  # -------------------------------------------------------------------------

  describe "iso8601_datetime/0" do
    test "returns 16-character string" do
      assert byte_size(SmithySigV4.iso8601_datetime()) == 16
    end

    test "starts with 20 (year 20xx)" do
      assert String.starts_with?(SmithySigV4.iso8601_datetime(), "20")
    end

    test "has T at position 8" do
      dt = SmithySigV4.iso8601_datetime()
      assert String.at(dt, 8) == "T"
    end

    test "ends with Z" do
      assert String.ends_with?(SmithySigV4.iso8601_datetime(), "Z")
    end

    test "sequential calls are non-decreasing" do
      t1 = SmithySigV4.iso8601_datetime()
      t2 = SmithySigV4.iso8601_datetime()
      assert t2 >= t1
    end
  end

  # -------------------------------------------------------------------------
  # create_string_to_sign/3
  # -------------------------------------------------------------------------

  describe "create_string_to_sign/3" do
    test "has 4 newline-separated lines" do
      scope = "20230101/us-east-1/s3/aws4_request"
      canonical = "GET\n/\n\nhost:s3.amazonaws.com\n\nhost\ne3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
      result = SmithySigV4.create_string_to_sign("20230101T120000Z", scope, canonical)
      assert length(String.split(result, "\n")) == 4
    end

    test "line 1 is the algorithm identifier" do
      scope = "20230101/us-east-1/s3/aws4_request"
      result = SmithySigV4.create_string_to_sign("20230101T120000Z", scope, "canonical")
      assert List.first(String.split(result, "\n")) == "AWS4-HMAC-SHA256"
    end

    test "line 2 is the datetime" do
      datetime = "20230515T093000Z"
      scope = "20230515/eu-west-1/dynamodb/aws4_request"
      result = SmithySigV4.create_string_to_sign(datetime, scope, "canonical")
      assert Enum.at(String.split(result, "\n"), 1) == datetime
    end

    test "line 3 is the credential scope" do
      scope = "20230515/eu-west-1/dynamodb/aws4_request"
      result = SmithySigV4.create_string_to_sign("20230515T093000Z", scope, "canonical")
      assert Enum.at(String.split(result, "\n"), 2) == scope
    end

    test "line 4 is a 64-character hex hash" do
      scope = "20230101/us-east-1/s3/aws4_request"
      result = SmithySigV4.create_string_to_sign("20230101T120000Z", scope, "canonical")
      hash = List.last(String.split(result, "\n"))
      assert byte_size(hash) == 64
      assert hex_string?(hash)
    end

    test "empty canonical request produces empty-string hash on line 4" do
      scope = "20230101/us-east-1/s3/aws4_request"
      result = SmithySigV4.create_string_to_sign("20230101T000000Z", scope, "")
      hash = List.last(String.split(result, "\n"))
      assert hash == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    end
  end

  # -------------------------------------------------------------------------
  # derive_signing_key/4
  # -------------------------------------------------------------------------

  describe "derive_signing_key/4" do
    test "returns 32 bytes" do
      key = SmithySigV4.derive_signing_key("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "20150830", "us-east-1", "iam")
      assert byte_size(key) == 32
    end

    test "AWS test vector" do
      # https://docs.aws.amazon.com/general/latest/gr/sigv4-calculate-signature.html
      key = SmithySigV4.derive_signing_key("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "20150830", "us-east-1", "iam")
      expected = Base.decode16!("c4afb1cc5771d871763a393e44b703571b55cc28424d1a5e86da6ed3c154a4b9", case: :lower)
      assert key == expected
    end

    test "is deterministic" do
      k1 = SmithySigV4.derive_signing_key("secret", "20230101", "us-west-2", "s3")
      k2 = SmithySigV4.derive_signing_key("secret", "20230101", "us-west-2", "s3")
      assert k1 == k2
    end

    test "different dates produce different keys" do
      k1 = SmithySigV4.derive_signing_key("secret", "20230101", "us-east-1", "s3")
      k2 = SmithySigV4.derive_signing_key("secret", "20230102", "us-east-1", "s3")
      assert k1 != k2
    end

    test "different regions produce different keys" do
      k1 = SmithySigV4.derive_signing_key("secret", "20230101", "us-east-1", "s3")
      k2 = SmithySigV4.derive_signing_key("secret", "20230101", "eu-west-1", "s3")
      assert k1 != k2
    end

    test "different services produce different keys" do
      k1 = SmithySigV4.derive_signing_key("secret", "20230101", "us-east-1", "s3")
      k2 = SmithySigV4.derive_signing_key("secret", "20230101", "us-east-1", "dynamodb")
      assert k1 != k2
    end
  end

  # -------------------------------------------------------------------------
  # calculate_signature/2
  # -------------------------------------------------------------------------

  describe "calculate_signature/2" do
    test "returns 64-character lowercase hex string" do
      key = SmithySigV4.derive_signing_key("secret", "20230101", "us-east-1", "s3")
      sig = SmithySigV4.calculate_signature(key, "AWS4-HMAC-SHA256\n20230101T120000Z\n20230101/us-east-1/s3/aws4_request\nabc123")
      assert byte_size(sig) == 64
      assert hex_string?(sig)
    end

    test "AWS test vector" do
      # https://docs.aws.amazon.com/general/latest/gr/sigv4-calculate-signature.html
      key = SmithySigV4.derive_signing_key("wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY", "20150830", "us-east-1", "iam")
      string_to_sign =
        "AWS4-HMAC-SHA256\n20150830T123600Z\n20150830/us-east-1/iam/aws4_request\nf536975d06c0309214f805bb90ccff089219ecd68b2577efef23edd43b7e1a59"
      assert SmithySigV4.calculate_signature(key, string_to_sign) ==
               "5d672d79c15b13162d9279b0855cfba6789a8edb4c82c400e06b5924a6f2b5d7"
    end

    test "is deterministic" do
      key = SmithySigV4.derive_signing_key("secret", "20230101", "us-east-1", "s3")
      s = "test string"
      assert SmithySigV4.calculate_signature(key, s) == SmithySigV4.calculate_signature(key, s)
    end

    test "different inputs produce different signatures" do
      key = SmithySigV4.derive_signing_key("secret", "20230101", "us-east-1", "s3")
      assert SmithySigV4.calculate_signature(key, "string1") !=
               SmithySigV4.calculate_signature(key, "string2")
    end
  end

  # -------------------------------------------------------------------------
  # format_auth_header/4
  # -------------------------------------------------------------------------

  describe "format_auth_header/4" do
    test "contains all required components" do
      result = SmithySigV4.format_auth_header(
        "AKIAIOSFODNN7EXAMPLE",
        "20230101/us-east-1/s3/aws4_request",
        "host;x-amz-date",
        "5d672d79c15b13162d9279b0855cfba6789a8edb4c82c400e06b5924a6f2b5d7"
      )

      assert String.starts_with?(result, "AWS4-HMAC-SHA256 ")
      assert String.contains?(result, "Credential=AKIAIOSFODNN7EXAMPLE/")
      assert String.contains?(result, "SignedHeaders=host;x-amz-date")
      assert String.contains?(result, "Signature=5d672d79c15b13162d9279b0855cfba6789a8edb4c82c400e06b5924a6f2b5d7")
    end

    test "exact format matches AWS spec" do
      result = SmithySigV4.format_auth_header(
        "AKIAIOSFODNN7EXAMPLE",
        "20230101/us-east-1/s3/aws4_request",
        "host;x-amz-date",
        "abc123"
      )

      expected =
        "AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20230101/us-east-1/s3/aws4_request, " <>
          "SignedHeaders=host;x-amz-date, Signature=abc123"

      assert result == expected
    end
  end

  # -------------------------------------------------------------------------
  # sign_request/2 — integration
  # -------------------------------------------------------------------------

  describe "sign_request/2" do
    @config %{
      access_key_id: "AKIAIOSFODNN7EXAMPLE",
      secret_access_key: "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
      region: "us-east-1",
      service: "s3"
    }

    defp base_request(overrides \\ %{}) do
      Map.merge(
        %{
          method: "GET",
          url: "https://s3.amazonaws.com/mybucket/mykey",
          headers: [{"host", "s3.amazonaws.com"}],
          body: ""
        },
        overrides
      )
    end

    test "returns {:ok, headers}" do
      assert {:ok, headers} = SmithySigV4.sign_request(base_request(), config: @config)
      assert is_list(headers)
    end

    test "result contains authorization header" do
      {:ok, headers} = SmithySigV4.sign_request(base_request(), config: @config)
      assert List.keyfind(headers, "authorization", 0) != nil
    end

    test "result contains x-amz-date header" do
      {:ok, headers} = SmithySigV4.sign_request(base_request(), config: @config)
      assert List.keyfind(headers, "x-amz-date", 0) != nil
    end

    test "authorization header has correct format" do
      {:ok, headers} = SmithySigV4.sign_request(base_request(), config: @config)
      {_, auth} = List.keyfind(headers, "authorization", 0)
      assert String.starts_with?(auth, "AWS4-HMAC-SHA256 ")
      assert String.contains?(auth, "Credential=")
      assert String.contains?(auth, "SignedHeaders=")
      assert String.contains?(auth, "Signature=")
    end

    test "authorization header contains access key and region" do
      {:ok, headers} = SmithySigV4.sign_request(base_request(), config: @config)
      {_, auth} = List.keyfind(headers, "authorization", 0)
      assert String.contains?(auth, "AKIAIOSFODNN7EXAMPLE")
      assert String.contains?(auth, "/us-east-1/s3/aws4_request")
    end

    test "x-amz-date has correct ISO 8601 format" do
      {:ok, headers} = SmithySigV4.sign_request(base_request(), config: @config)
      {_, datetime} = List.keyfind(headers, "x-amz-date", 0)
      assert byte_size(datetime) == 16
      assert String.at(datetime, 8) == "T"
      assert String.ends_with?(datetime, "Z")
    end

    test "session token header included when session_token provided" do
      config = Map.put(@config, :session_token, "AQoEXAMPLEtoken")
      {:ok, headers} = SmithySigV4.sign_request(base_request(), config: config)
      assert List.keyfind(headers, "x-amz-security-token", 0) != nil
      {_, token} = List.keyfind(headers, "x-amz-security-token", 0)
      assert token == "AQoEXAMPLEtoken"
    end

    test "no session token header when session_token absent" do
      {:ok, headers} = SmithySigV4.sign_request(base_request(), config: @config)
      assert List.keyfind(headers, "x-amz-security-token", 0) == nil
    end

    test "POST with body signed correctly" do
      req = base_request(%{
        method: "POST",
        url: "https://dynamodb.us-east-1.amazonaws.com/",
        headers: [
          {"host", "dynamodb.us-east-1.amazonaws.com"},
          {"content-type", "application/x-amz-json-1.0"}
        ],
        body: ~s({"TableName":"Users"})
      })

      config = %{@config | service: "dynamodb", region: "us-east-1"}
      assert {:ok, headers} = SmithySigV4.sign_request(req, config: config)
      assert List.keyfind(headers, "authorization", 0) != nil
    end

    test "service derived from URL when not in config" do
      config = Map.delete(@config, :service)
      assert {:ok, headers} = SmithySigV4.sign_request(base_request(), config: config)
      {_, auth} = List.keyfind(headers, "authorization", 0)
      assert String.contains?(auth, "/s3/aws4_request")
    end
  end

  # -------------------------------------------------------------------------
  # Helpers
  # -------------------------------------------------------------------------

  defp hex_string?(s) do
    String.match?(s, ~r/\A[0-9a-f]+\z/)
  end
end
