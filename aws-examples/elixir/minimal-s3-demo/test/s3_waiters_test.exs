defmodule S3WaitersTest do
  use ExUnit.Case, async: true

  alias AmazonS3Types.{HeadBucketInput, HeadBucketOutput}

  @bucket "my-bucket"
  @wait_opts [max_attempts: 3, min_delay_ms: 0, max_delay_ms: 0]

  setup do
    on_exit(fn -> :persistent_term.erase({HeadBucketHttpMock, :responses}) end)
    on_exit(fn -> :persistent_term.erase({HeadBucketHttpMock, :count}) end)
    :ok
  end

  test "wait_bucket_exists/3 returns ok when head_bucket succeeds" do
    HeadBucketHttpMock.reset([{:status, 200}])

    assert {:ok, {:ok, %HeadBucketOutput{}}} =
             AmazonS3Waiters.wait_bucket_exists(client_config(), head_bucket_input(), @wait_opts)

    assert HeadBucketHttpMock.call_count() == 1
  end

  test "wait_bucket_exists/3 retries until head_bucket succeeds" do
    HeadBucketHttpMock.reset([{:status, 404}, {:status, 200}])

    assert {:ok, {:ok, %HeadBucketOutput{}}} =
             AmazonS3Waiters.wait_bucket_exists(client_config(), head_bucket_input(), @wait_opts)

    assert HeadBucketHttpMock.call_count() == 2
  end

  test "wait_bucket_exists/3 returns max_attempts_exceeded when bucket never appears" do
    HeadBucketHttpMock.reset([{:status, 404}, {:status, 404}, {:status, 404}])

    assert {:error, :max_attempts_exceeded} =
             AmazonS3Waiters.wait_bucket_exists(
               client_config(),
               head_bucket_input(),
               Keyword.put(@wait_opts, :max_attempts, 2)
             )

    assert HeadBucketHttpMock.call_count() == 2
  end

  test "wait_bucket_not_exists/3 returns max_attempts_exceeded when bucket still exists" do
    HeadBucketHttpMock.reset([{:status, 200}, {:status, 200}])

    assert {:error, :max_attempts_exceeded} =
             AmazonS3Waiters.wait_bucket_not_exists(
               client_config(),
               head_bucket_input(),
               Keyword.put(@wait_opts, :max_attempts, 2)
             )

    assert HeadBucketHttpMock.call_count() == 2
  end

  defp client_config do
    %{
      base_url: "http://localhost:4566",
      region: "us-east-1",
      endpoint_prefix: "s3",
      signing_name: "s3",
      s3_addressing_style: :path_style,
      http_client: HeadBucketHttpMock,
      credentials: nil,
      retry: [max_attempts: 1, base_delay_ms: 0]
    }
  end

  defp head_bucket_input do
    %HeadBucketInput{bucket: @bucket}
  end
end
