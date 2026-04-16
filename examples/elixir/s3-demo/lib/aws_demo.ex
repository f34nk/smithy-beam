defmodule AwsDemo do
  @moduledoc """
  S3 demo — covers ListBuckets, PutObject, ListObjects, and GetObject via
  the generated AwsS3Client.

  Uses the aws.protocols#restXml protocol (XML over HTTPS).
  Run against LocalStack: make demo
  """

  @bucket "us-east-1-nonprod-configs"

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
    try do
      case AwsS3Client.list_buckets(client, %{}, %{enable_retry: false}) do
        {:ok, output} ->
          IO.puts("\nSUCCESS: ListBuckets returned successfully!")
          IO.inspect(output)

          # Response: %{"ListAllMyBucketsResult" => %{"Buckets" => ...}}
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

          IO.puts("Found #{length(bucket_list)} bucket(s):")
          Enum.each(bucket_list, fn bucket ->
            inner = Map.get(bucket, "Bucket", bucket)
            name  = Map.get(inner, "Name", "unknown")
            IO.puts("  - #{name}")
          end)
          IO.puts("")

        {:error, {:aws_error, status, code, message}} ->
          IO.puts("AWS Error: #{status} #{code} - #{message}")
        {:error, err} ->
          IO.puts("Error from S3: #{inspect(err)}")
      end
    rescue
      e -> IO.puts("Unexpected error: #{inspect(e)}")
    end

    # 2. PutObject
    put_input = %{
      "Bucket"      => @bucket,
      "Key"         => "configs/test.txt",
      "Body"        => "Hello World",
      "ContentType" => "text/plain"
    }
    case AwsS3Client.put_object(client, put_input) do
      {:ok, output} ->
        IO.puts("\nSUCCESS: PutObject returned successfully!")
        IO.inspect(output)
        IO.puts("")
      {:error, err} ->
        IO.puts("Failed to upload object: #{inspect(err)}\n")
    end

    # 3. ListObjects
    list_objects_input = %{
      "Bucket"    => @bucket,
      "Prefix"    => "configs/",
      "MaxKeys"   => 100,
      "Delimiter" => ""
    }
    try do
      case AwsS3Client.list_objects(client, list_objects_input) do
        {:ok, output} ->
          IO.puts("\nSUCCESS: ListObjects returned successfully!")
          IO.inspect(output)

          # Response: %{"ListBucketResult" => %{"Contents" => [...]}}
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

          IO.puts("Found #{length(objects)} object(s):")
          Enum.each(objects, fn obj ->
            key = Map.get(obj, "Key", Map.get(obj, "key", "unknown"))
            IO.puts("  - #{key}")
          end)
          IO.puts("")

        {:error, {:aws_error, status, code, message}} ->
          IO.puts("AWS Error: #{status} #{code} - #{message}")
        {:error, err} ->
          IO.puts("Error from S3: #{inspect(err)}")
      end
    rescue
      e -> IO.puts("Unexpected error: #{inspect(e)}")
    end

    # 4. GetObject
    get_input = %{
      "Bucket" => @bucket,
      "Key"    => "configs/test.txt"
    }
    case AwsS3Client.get_object(client, get_input) do
      {:ok, output} ->
        IO.puts("\nSUCCESS: GetObject returned successfully!")
        IO.inspect(output)
        IO.puts("")
      {:error, err} ->
        IO.puts("Failed to get object: #{inspect(err)}\n")
    end

    IO.puts("=== S3 Client Application Complete ===")
    :ok
  end
end
