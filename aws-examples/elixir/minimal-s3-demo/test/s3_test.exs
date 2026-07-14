defmodule S3Test do
  use ExUnit.Case, async: false

  alias AmazonS3Types.{HeadBucketInput, HeadBucketOutput, ListBucketsInput}
  alias RuntimeTypes.HttpRequest

  @bucket_name "smithy-beam-minimal-s3-elixir"
  @head_bucket "my-bucket"

  setup do
    on_exit(fn -> :persistent_term.erase({HeadBucketHttpMock, :responses}) end)
    on_exit(fn -> :persistent_term.erase({HeadBucketHttpMock, :count}) end)
    :ok
  end

  test "presign_url returns a SigV4 query-string URL" do
    config = %{
      base_url: "http://localhost:4566",
      region: "us-east-1",
      endpoint_prefix: "s3",
      signing_name: "s3",
      s3_addressing_style: :path_style,
      presign_expires: 3600,
      credentials: %{
        access_key_id: "AKIAIOSFODNN7EXAMPLE",
        secret_access_key: "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
      }
    }

    request = %HttpRequest{
      method: "GET",
      path: "/my-bucket/object.txt",
      host: "localhost:4566"
    }

    assert {:ok, url} = AwsSigv4.presign_url(config, :get_object, request)
    assert String.starts_with?(url, "https://localhost:4566/my-bucket/object.txt?")
    assert url =~ "X-Amz-Algorithm=AWS4-HMAC-SHA256"
    assert url =~ "X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F"
    assert url =~ "X-Amz-Expires=3600"
    assert url =~ "X-Amz-Signature="
  end

  test "list_buckets against LocalStack" do
    endpoint = System.get_env("AWS_ENDPOINT")

    if is_nil(endpoint) or endpoint == "" do
      raise "AWS_ENDPOINT must be set (run via make demo or export AWS_ENDPOINT)"
    end

    config = %{
      base_url: endpoint,
      region: "us-east-1",
      endpoint_prefix: "s3",
      signing_name: "s3",
      s3_addressing_style: :path_style,
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }

    input = %ListBucketsInput{}

    assert {:ok, output} = AmazonS3Client.list_buckets(config, input)
    assert is_list(output.buckets)
    assert length(output.buckets) > 0

    names =
      for %{name: name} <- output.buckets, not is_nil(name), do: name

    assert @bucket_name in names
  end

  test "head_bucket retries on retryable NotFound and succeeds" do
    HeadBucketHttpMock.reset([{:status, 404}, {:status, 200}])

    assert {:ok, %HeadBucketOutput{}} =
             AmazonS3Client.head_bucket(
               head_bucket_client_config(),
               %HeadBucketInput{bucket: @head_bucket}
             )

    assert HeadBucketHttpMock.call_count() == 2
  end

  test "head_bucket stops retrying when NotFound persists" do
    HeadBucketHttpMock.reset([{:status, 404}, {:status, 404}, {:status, 404}])

    assert {:error, %AmazonS3Types.NotFound{}} =
             AmazonS3Client.head_bucket(
               head_bucket_client_config(retry: [max_attempts: 2, base_delay_ms: 0]),
               %HeadBucketInput{bucket: @head_bucket}
             )

    assert HeadBucketHttpMock.call_count() == 2
  end

  defp head_bucket_client_config(extra \\ []) do
    Map.merge(
      %{
        base_url: "http://localhost:4566",
        region: "us-east-1",
        endpoint_prefix: "s3",
        signing_name: "s3",
        s3_addressing_style: :path_style,
        http_client: HeadBucketHttpMock,
        credentials: nil,
        retry: [max_attempts: 3, base_delay_ms: 0]
      },
      Map.new(extra)
    )
  end
end
