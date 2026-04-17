defmodule AwsDemo do
  @moduledoc """
  SQS demo — covers ListQueues, GetQueueUrl, SendMessage, GetQueueAttributes,
  ReceiveMessage, DeleteMessage, and a final empty-queue verification via the
  generated AwsSqsClient.

  Uses the aws.protocols#awsQuery protocol (Query-encoded XML over HTTPS).
  Run against LocalStack: make demo
  """

  @queue_name "sqs-demo-queue"
  @msg1_body "Hello from Elixir! This is message 1."
  @msg2_body "Hello from Elixir! This is message 2."

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
        if urls == [],
          do: raise("assertion failed: expected non-empty queue list")
        IO.puts("SUCCESS: Found #{length(urls)} queue(s)")
        unless Enum.any?(urls, fn url -> String.contains?(url, @queue_name) end),
          do: raise("assertion failed: queue #{inspect(@queue_name)} not found in #{inspect(urls)}")
        IO.puts("SUCCESS: Queue '#{@queue_name}' found in list")
        Enum.each(urls, fn url -> IO.puts("  - #{url}") end)
      {:error, err} ->
        raise("list_queues_failed: #{inspect(err)}")
    end
    IO.puts("")

    # 2. GetQueueUrl
    IO.puts("--- GetQueueUrl ---")
    queue_url =
      case AwsSqsClient.get_queue_url(client, %{"QueueName" => @queue_name}, %{enable_retry: false}) do
        {:ok, output} ->
          url = Map.get(output, "QueueUrl")
          if is_nil(url) or url == "",
            do: raise("assertion failed: GetQueueUrl returned empty URL")
          IO.puts("SUCCESS: Queue URL: #{url}")
          url
        {:error, err} ->
          raise("get_queue_url_failed: #{inspect(err)}")
      end
    IO.puts("")

    # 3. SendMessage — first message
    IO.puts("--- SendMessage ---")
    send_input1 = %{
      "QueueUrl"    => queue_url,
      "MessageBody" => @msg1_body,
      "MessageAttributes" => %{
        "Author" => %{
          "DataType"    => "String",
          "StringValue" => "Smithy-Elixir Demo"
        }
      }
    }
    case AwsSqsClient.send_message(client, send_input1, %{enable_retry: false}) do
      {:ok, output} ->
        msg_id = Map.get(output, "MessageId", "")
        if msg_id == "",
          do: raise("assertion failed: SendMessage returned empty MessageId")
        IO.puts("SUCCESS: Message sent, ID: #{msg_id}")
      {:error, err} ->
        raise("send_message_failed: #{inspect(err)}")
    end
    IO.puts("")

    # 4. SendMessage — second message
    IO.puts("--- SendMessage (second message) ---")
    send_input2 = %{
      "QueueUrl"    => queue_url,
      "MessageBody" => @msg2_body
    }
    case AwsSqsClient.send_message(client, send_input2, %{enable_retry: false}) do
      {:ok, output} ->
        msg_id = Map.get(output, "MessageId", "")
        if msg_id == "",
          do: raise("assertion failed: SendMessage returned empty MessageId")
        IO.puts("SUCCESS: Message sent, ID: #{msg_id}")
      {:error, err} ->
        raise("send_message_failed: #{inspect(err)}")
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
        raise("get_queue_attributes_failed: #{inspect(err)}")
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
          if messages == [],
            do: raise("assertion failed: expected at least 1 message to be received")
          IO.puts("SUCCESS: Received #{length(messages)} message(s)")
          received_bodies = Enum.map(messages, fn msg -> Map.get(msg, "Body", "") end)
          expected_bodies = Enum.sort([@msg1_body, @msg2_body])
          if Enum.sort(received_bodies) != expected_bodies,
            do: raise("assertion failed: expected bodies #{inspect(expected_bodies)}, got #{inspect(received_bodies)}")
          IO.puts("SUCCESS: Message bodies match")
          Enum.map(messages, fn msg ->
            IO.puts("  Message ID: #{Map.get(msg, "MessageId", "unknown")}")
            IO.puts("    Body: #{Map.get(msg, "Body", "empty")}")
            Map.get(msg, "ReceiptHandle")
          end)
        {:error, err} ->
          raise("receive_message_failed: #{inspect(err)}")
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
          {:error, err} -> raise("delete_message_failed: #{inspect(err)}")
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
        raise("receive_message_failed: #{inspect(err)}")
    end

    IO.puts("\n=== SQS Client Application Complete ===")
    :ok
  end
end
