defmodule Demo do
  @moduledoc """
  S3 demo covering ListBuckets, PutObject, ListObjects, and GetObject via
  the generated S3Client.

  Uses the aws.protocols#restXml protocol (XML over HTTPS).
  Run against LocalStack: make demo
  """

  alias S3Types.{
    Bucket,
    BucketAlreadyOwnedByYou,
    CreateBucketInput,
    DeleteBucketInput,
    DeleteObjectInput,
    GetObjectInput,
    GetObjectOutput,
    ListBucketsInput,
    ListObjectsInput,
    ListObjectsOutput,
    NoSuchBucket,
    Object,
    PutObjectInput
  }

  @bucket_name "s3-demo-elixir-configs"
  @config1_key "configs/config1.toml"
  @config1_body "foo = \"bar\""
  @config2_key "configs/config2.toml"
  @config2_body "baz = \"qux\""
  @object_key "configs/test.txt"
  @expected_body "Hello World"

  def run do
    IO.puts("\n=== Running S3 Client Application ===\n")

    config = client_config()
    IO.puts("S3 client configured for #{Map.fetch!(config, :base_url)}\n")

    setup_infrastructure(config)

    list_buckets(config)
    put_object(config)
    list_objects(config)
    get_object(config)

    delete_demo_bucket(config)

    IO.puts("=== S3 Client Application Complete ===")
    :ok
  end

  defp client_config do
    %{
      region: "us-east-1",
      endpoint_prefix: "s3",
      signing_name: "s3",
      base_url: System.get_env("AWS_ENDPOINT"),
      s3_addressing_style: :path_style,
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }
  end

  defp setup_infrastructure(config) do
    IO.puts("--- Setup infrastructure ---")
    create_demo_bucket(config)
    put_config_objects(config)
    IO.puts("Infrastructure ready: Bucket=#{@bucket_name}\n")
    :ok
  end

  defp create_demo_bucket(config) do
    IO.puts("--- CreateBucket ---")

    input = %CreateBucketInput{bucket: @bucket_name}

    case S3Client.create_bucket(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Bucket '#{@bucket_name}' created")

      {:error, %BucketAlreadyOwnedByYou{}} ->
        IO.puts("SUCCESS: Bucket '#{@bucket_name}' already exists")

      {:error, reason} ->
        raise("create_bucket_failed: #{inspect(reason)}")
    end

    IO.puts("")
    @bucket_name
  end

  defp put_config_objects(config) do
    IO.puts("--- PutObject (config files) ---")
    put_config_object(config, @config1_key, @config1_body)
    put_config_object(config, @config2_key, @config2_body)
    IO.puts("")
    :ok
  end

  defp put_config_object(config, key, body) do
    input = %PutObjectInput{
      bucket: @bucket_name,
      key: key,
      body: body,
      acl: :public_read
    }

    case S3Client.put_object(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: PutObject '#{key}'")

      {:error, reason} ->
        raise("put_object_failed: #{inspect({key, reason})}")
    end
  end

  defp list_buckets(config) do
    IO.puts("--- ListBuckets ---")

    case S3Client.list_buckets(config, %ListBucketsInput{}) do
      {:ok, buckets} when is_list(buckets) and buckets != [] ->
        IO.puts("SUCCESS: Found #{length(buckets)} bucket(s)")

        bucket_names =
          for %Bucket{name: name} <- buckets, not is_nil(name), do: name

        unless @bucket_name in bucket_names do
          raise(
            "assertion failed: bucket_not_found #{inspect(@bucket_name)} in #{inspect(bucket_names)}"
          )
        end

        IO.puts("SUCCESS: Bucket '#{@bucket_name}' found")

        Enum.each(buckets, fn %Bucket{name: name} ->
          IO.puts("  - #{format_string(name)}")
        end)

      {:ok, _} ->
        raise("assertion failed: empty_bucket_list")

      {:error, reason} ->
        raise("list_buckets_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp put_object(config) do
    IO.puts("--- PutObject ---")

    input = %PutObjectInput{
      bucket: @bucket_name,
      key: @object_key,
      body: @expected_body,
      content_type: "text/plain"
    }

    case S3Client.put_object(config, input) do
      {:ok, output} ->
        IO.puts("SUCCESS: PutObject returned successfully!")
        IO.inspect(output)
        IO.puts("")

      {:error, reason} ->
        raise("put_object_failed: #{inspect(reason)}")
    end
  end

  defp list_objects(config) do
    IO.puts("--- ListObjects ---")

    input = %ListObjectsInput{
      bucket: @bucket_name,
      prefix: "configs/",
      max_keys: 100
    }

    case S3Client.list_objects(config, input) do
      {:ok, %ListObjectsOutput{} = result} ->
        contents = objects_from_contents(result.contents)

        if contents == [] do
          raise("assertion failed: empty_object_list")
        end

        IO.puts("SUCCESS: Found #{length(contents)} object(s)")

        object_keys =
          for %Object{key: key} <- contents, not is_nil(key), do: key

        unless @object_key in object_keys do
          raise(
            "assertion failed: object_not_found #{inspect(@object_key)} in #{inspect(object_keys)}"
          )
        end

        IO.puts("SUCCESS: Object '#{@object_key}' found")

        Enum.each(contents, fn %Object{key: key} ->
          IO.puts("  - #{format_string(key)}")
        end)

      {:error, reason} ->
        raise("list_objects_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp get_object(config) do
    IO.puts("--- GetObject ---")

    input = %GetObjectInput{
      bucket: @bucket_name,
      key: @object_key
    }

    case S3Client.get_object(config, input) do
      {:ok, %GetObjectOutput{body: body}} ->
        IO.puts("SUCCESS: GetObject returned successfully!")

        if body != @expected_body do
          raise(
            "assertion failed: expected_body #{inspect(@expected_body)}, got #{inspect(body)}"
          )
        end

        IO.puts("SUCCESS: GetObject body matches")

      {:error, reason} ->
        raise("get_object_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp delete_demo_bucket(config) do
    IO.puts("--- DeleteObject (config files) ---")
    delete_config_object(config, @config1_key)
    delete_config_object(config, @config2_key)
    delete_config_object(config, @object_key)

    IO.puts("--- DeleteBucket ---")

    case S3Client.delete_bucket(config, %DeleteBucketInput{bucket: @bucket_name}) do
      {:ok, _} ->
        IO.puts("SUCCESS: Bucket '#{@bucket_name}' deleted")

      {:error, %NoSuchBucket{}} ->
        IO.puts("SUCCESS: Bucket '#{@bucket_name}' already absent")

      {:error, reason} ->
        raise("delete_bucket_failed: #{inspect(reason)}")
    end

    IO.puts("")
    :ok
  end

  defp delete_config_object(config, key) do
    input = %DeleteObjectInput{bucket: @bucket_name, key: key}

    case S3Client.delete_object(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Deleted '#{key}'")

      {:error, reason} ->
        raise("delete_object_failed: #{inspect({key, reason})}")
    end
  end

  defp objects_from_contents(nil), do: []

  defp objects_from_contents(contents) when is_list(contents), do: contents

  defp objects_from_contents(_), do: []

  defp format_string(nil), do: "unknown"
  defp format_string(value) when is_binary(value), do: value
end
