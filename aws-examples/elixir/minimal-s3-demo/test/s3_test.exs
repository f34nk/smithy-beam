defmodule S3Test do
  use ExUnit.Case, async: false

  alias AmazonS3Types.ListBucketsInput

  @bucket_name "smithy-beam-minimal-s3-elixir"

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
end
