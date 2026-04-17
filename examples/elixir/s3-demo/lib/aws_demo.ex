defmodule AwsDemo do
  @moduledoc """
  S3 demo — covers ListBuckets, PutObject, ListObjects, and GetObject via
  the generated AwsS3Client.

  Uses the aws.protocols#restXml protocol (XML over HTTPS).
  Run against LocalStack: make demo
  """

  @bucket "us-east-1-nonprod-configs"
  @object_key "configs/test.txt"
  @expected_body "Hello World"

  def run do
    IO.puts("\n=== Running S3 Client Application ===\n")

    config = %{
      endpoint: System.get_env("AWS_ENDPOINT"),
      region: "us-east-1",
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }

    {:ok, client} = AwsS3Client.new(config)
    IO.puts("Client created successfully\n")

    # 1. ListBuckets
    case AwsS3Client.list_buckets(client, %{}, %{enable_retry: false}) do
      {:ok, output} ->
        IO.puts("\nSUCCESS: ListBuckets returned successfully!")

        buckets_data =
          output
          |> Map.get("ListAllMyBucketsResult", %{})
          |> Map.get("Buckets", [])

        bucket_list =
          case buckets_data do
            m when is_map(m)  -> [m]
            l when is_list(l) -> l
            _                 -> []
          end

        if bucket_list == [],
          do: raise("assertion failed: expected non-empty bucket list")

        IO.puts("SUCCESS: Found #{length(bucket_list)} bucket(s)")

        bucket_names = Enum.map(bucket_list, fn b ->
          inner = Map.get(b, "Bucket", b)
          Map.get(inner, "Name", "")
        end)

        unless @bucket in bucket_names,
          do: raise("assertion failed: bucket #{inspect(@bucket)} not found in #{inspect(bucket_names)}")

        IO.puts("SUCCESS: Bucket '#{@bucket}' found")

        Enum.each(bucket_list, fn bucket ->
          inner = Map.get(bucket, "Bucket", bucket)
          name  = Map.get(inner, "Name", "unknown")
          IO.puts("  - #{name}")
        end)
        IO.puts("")

      {:error, {:aws_error, status, code, message}} ->
        raise("list_buckets_failed: #{status} #{code} - #{message}")
      {:error, err} ->
        raise("list_buckets_failed: #{inspect(err)}")
    end

    # 2. PutObject
    put_input = %{
      "Bucket"      => @bucket,
      "Key"         => @object_key,
      "Body"        => @expected_body,
      "ContentType" => "text/plain"
    }
    case AwsS3Client.put_object(client, put_input) do
      {:ok, output} ->
        IO.puts("\nSUCCESS: PutObject returned successfully!")
        IO.inspect(output)
        IO.puts("")
      {:error, err} ->
        raise("put_object_failed: #{inspect(err)}")
    end

    # 3. ListObjects
    list_objects_input = %{
      "Bucket"    => @bucket,
      "Prefix"    => "configs/",
      "MaxKeys"   => 100,
      "Delimiter" => ""
    }
    case AwsS3Client.list_objects(client, list_objects_input) do
      {:ok, output} ->
        IO.puts("\nSUCCESS: ListObjects returned successfully!")

        contents =
          output
          |> Map.get("ListBucketResult", %{})
          |> Map.get("Contents", [])

        objects =
          case contents do
            m when is_map(m)  -> [m]
            l when is_list(l) -> l
            _                 -> []
          end

        if objects == [],
          do: raise("assertion failed: expected non-empty object list")

        IO.puts("SUCCESS: Found #{length(objects)} object(s)")

        object_keys = Enum.map(objects, fn obj ->
          Map.get(obj, "Key", Map.get(obj, "key", ""))
        end)

        unless @object_key in object_keys,
          do: raise("assertion failed: object #{inspect(@object_key)} not found in #{inspect(object_keys)}")

        IO.puts("SUCCESS: Object '#{@object_key}' found")

        Enum.each(objects, fn obj ->
          key = Map.get(obj, "Key", Map.get(obj, "key", "unknown"))
          IO.puts("  - #{key}")
        end)
        IO.puts("")

      {:error, {:aws_error, status, code, message}} ->
        raise("list_objects_failed: #{status} #{code} - #{message}")
      {:error, err} ->
        raise("list_objects_failed: #{inspect(err)}")
    end

    # 4. GetObject
    get_input = %{
      "Bucket" => @bucket,
      "Key"    => @object_key
    }
    case AwsS3Client.get_object(client, get_input) do
      {:ok, output} ->
        IO.puts("\nSUCCESS: GetObject returned successfully!")
        # GetObject uses @httpPayload — the runtime returns the raw response body
        # as a binary (decoding: :raw), not a parsed map.
        body = if is_binary(output), do: output, else: Map.get(output, "Body", "")
        if body != @expected_body,
          do: raise("assertion failed: expected body #{inspect(@expected_body)}, got #{inspect(body)}")
        IO.puts("SUCCESS: GetObject body matches")
        IO.puts("")
      {:error, err} ->
        raise("get_object_failed: #{inspect(err)}")
    end

    IO.puts("=== S3 Client Application Complete ===")
    :ok
  end
end
