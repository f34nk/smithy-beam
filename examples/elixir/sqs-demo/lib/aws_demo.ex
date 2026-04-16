defmodule AwsDemo do
  @moduledoc """
  SQS demo — covers ListQueues, GetQueueUrl, SendMessage, GetQueueAttributes,
  ReceiveMessage, DeleteMessage, and a final empty-queue verification via the
  generated AwsSqsClient.

  Uses the aws.protocols#awsQuery protocol (Query-encoded XML over HTTPS).
  Run against LocalStack: make demo
  """

  @queue_name "sqs-demo-queue"

  def run do
    IO.puts("\n=== Running SQS Client Application ===\n")

    config = %{
      endpoint: System.get_env("AWS_ENDPOINT"),
      region: "us-east-1",
      service: "sqs",
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }

    {:ok, client} = AwsSqsClient.new(config)
    IO.puts("Client created successfully\n")

    # 1. ListQueues — show what Terraform provisioned
    IO.puts("--- ListQueues ---")
    case AwsSqsClient.list_queues(client, %{}, %{enable_retry: false}) do
      {:ok, output} ->
        urls = Map.get(output, "QueueUrls", [])
        IO.puts("SUCCESS: Found #{length(urls)} queue(s)")
        Enum.each(urls, fn url -> IO.puts("  - #{url}") end)
      {:error, err} ->
        IO.puts("ERROR: #{inspect(err)}")
    end
    IO.puts("")

    # 2. GetQueueUrl
    IO.puts("--- GetQueueUrl ---")
    queue_url =
      case AwsSqsClient.get_queue_url(client, %{"QueueName" => @queue_name}, %{enable_retry: false}) do
        {:ok, output} ->
          url = Map.get(output, "QueueUrl")
          IO.puts("SUCCESS: Queue URL: #{url}")
          url
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
          nil
      end
    IO.puts("")

    if queue_url do
      # 3. SendMessage — first message
      IO.puts("--- SendMessage ---")
      send_input1 = %{
        "QueueUrl"    => queue_url,
        "MessageBody" => "Hello from Elixir! This is message 1.",
        "MessageAttributes" => %{
          "Author" => %{
            "DataType"    => "String",
            "StringValue" => "Smithy-Elixir Demo"
          }
        }
      }
      case AwsSqsClient.send_message(client, send_input1, %{enable_retry: false}) do
        {:ok, output} ->
          IO.puts("SUCCESS: Message sent, ID: #{Map.get(output, "MessageId", "unknown")}")
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
      end
      IO.puts("")

      # 4. SendMessage — second message
      IO.puts("--- SendMessage (second message) ---")
      send_input2 = %{
        "QueueUrl"    => queue_url,
        "MessageBody" => "Hello from Elixir! This is message 2."
      }
      case AwsSqsClient.send_message(client, send_input2, %{enable_retry: false}) do
        {:ok, output} ->
          IO.puts("SUCCESS: Message sent, ID: #{Map.get(output, "MessageId", "unknown")}")
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
      end
      IO.puts("")

      # 5. GetQueueAttributes
      IO.puts("--- GetQueueAttributes ---")
      attr_input = %{
        "QueueUrl"       => queue_url,
        "AttributeNames" => ["All"]
      }
      case AwsSqsClient.get_queue_attributes(client, attr_input, %{enable_retry: false}) do
        {:ok, output} ->
          attrs = Map.get(output, "Attributes", %{})
          IO.puts("SUCCESS: Queue attributes:")
          Enum.each(attrs, fn {k, v} -> IO.puts("    #{k}: #{v}") end)
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
      end
      IO.puts("")

      # 6. ReceiveMessage
      IO.puts("--- ReceiveMessage ---")
      receive_input = %{
        "QueueUrl"              => queue_url,
        "MaxNumberOfMessages"   => 10,
        "WaitTimeSeconds"       => 1,
        "MessageAttributeNames" => ["All"]
      }
      receipt_handles =
        case AwsSqsClient.receive_message(client, receive_input, %{enable_retry: false}) do
          {:ok, output} ->
            messages = Map.get(output, "Messages", [])
            IO.puts("SUCCESS: Received #{length(messages)} message(s)")
            Enum.map(messages, fn msg ->
              IO.puts("  Message ID: #{Map.get(msg, "MessageId", "unknown")}")
              IO.puts("    Body: #{Map.get(msg, "Body", "empty")}")
              Map.get(msg, "ReceiptHandle")
            end)
          {:error, err} ->
            IO.puts("ERROR: #{inspect(err)}")
            []
        end
      IO.puts("")

      # 7. DeleteMessage — delete all received messages
      IO.puts("--- DeleteMessage ---")
      Enum.each(receipt_handles, fn
        nil -> :ok
        handle ->
          delete_input = %{"QueueUrl" => queue_url, "ReceiptHandle" => handle}
          case AwsSqsClient.delete_message(client, delete_input, %{enable_retry: false}) do
            {:ok, _} -> IO.puts("SUCCESS: Message deleted")
            {:error, err} -> IO.puts("ERROR: #{inspect(err)}")
          end
      end)
      IO.puts("")

      # 8. ReceiveMessage — verify queue is empty
      IO.puts("--- ReceiveMessage (verify empty) ---")
      case AwsSqsClient.receive_message(client, receive_input, %{enable_retry: false}) do
        {:ok, output} ->
          case Map.get(output, "Messages", []) do
            [] -> IO.puts("SUCCESS: Queue is empty")
            msgs -> IO.puts("INFO: Queue still has #{length(msgs)} message(s)")
          end
        {:error, err} ->
          IO.puts("ERROR: #{inspect(err)}")
      end
    else
      IO.puts("Cannot continue without queue URL")
    end

    IO.puts("\n=== SQS Client Application Complete ===")
    :ok
  end
end
