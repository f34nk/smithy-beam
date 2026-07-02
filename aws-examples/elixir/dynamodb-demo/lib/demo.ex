defmodule Demo do
  @moduledoc """
  DynamoDB demo covering CreateTable, ListTables, PutItem, GetItem, Scan,
  DeleteItem, and DeleteTable via the generated DynamodbClient.

  Uses the aws.protocols#awsJson1_0 protocol (JSON over HTTPS).
  Run against LocalStack: make demo
  """

  alias DynamodbTypes.{
    AttributeDefinition,
    CreateTableInput,
    DeleteItemInput,
    DeleteTableInput,
    DescribeTableInput,
    GetItemInput,
    GetItemOutput,
    KeySchemaElement,
    PutItemInput,
    ResourceInUseException,
    ResourceNotFoundException,
    ScanInput
  }

  @table_name "dynamodb-demo-elixir"
  @hash_key "TestTableHashKey"

  def run do
    IO.puts("\n=== Running DynamoDB Client Application ===\n")

    config = client_config()
    IO.puts("DynamoDB client configured for #{Map.fetch!(config, :base_url)}\n")

    setup_infrastructure(config)
    list_tables(config)
    put_item(config, "test-item-001", build_item_1())
    get_item(config, "test-item-001")
    put_item(config, "test-item-002", build_item_2())
    scan_table(config)
    delete_item(config, "test-item-001")
    verify_deletion(config, "test-item-001")
    delete_item(config, "test-item-002")
    delete_demo_table(config)

    IO.puts("=== DynamoDB Client Application Complete ===")
    :ok
  end

  defp client_config do
    %{
      region: "us-east-1",
      endpoint_prefix: "dynamodb",
      signing_name: "dynamodb",
      base_url: System.get_env("AWS_ENDPOINT"),
      credentials: %{
        access_key_id: "dummy",
        secret_access_key: "dummy"
      }
    }
  end

  defp setup_infrastructure(config) do
    IO.puts("--- Setup infrastructure ---")
    create_demo_table(config)
    IO.puts("Infrastructure ready: TableName=#{@table_name}\n")
  end

  defp create_demo_table(config) do
    IO.puts("--- CreateTable ---")

    input = %CreateTableInput{
      table_name: @table_name,
      attribute_definitions: [
        %AttributeDefinition{
          attribute_name: @hash_key,
          attribute_type: :s
        }
      ],
      key_schema: [
        %KeySchemaElement{
          attribute_name: @hash_key,
          key_type: :hash
        }
      ],
      billing_mode: :pay_per_request
    }

    case DynamodbClient.create_table(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Table '#{@table_name}' create requested")

      {:error, %ResourceInUseException{}} ->
        IO.puts("SUCCESS: Table '#{@table_name}' already exists")

      {:error, reason} ->
        raise("create_table_failed: #{inspect(reason)}")
    end

    IO.puts("--- Wait TableExists ---")

    wait_input = %DescribeTableInput{table_name: @table_name}

    case DynamodbWaiters.wait_table_exists(config, wait_input, []) do
      {:ok, _} ->
        IO.puts("SUCCESS: Table '#{@table_name}' is ACTIVE\n")

      {:error, reason} ->
        raise("wait_table_exists_failed: #{inspect(reason)}")
    end
  end

  defp list_tables(config) do
    IO.puts("--- ListTables ---")

    case DynamodbClient.list_tables(config, %DynamodbTypes.ListTablesInput{}) do
      {:ok, table_names} when is_list(table_names) and table_names != [] ->
        IO.puts("SUCCESS: Found #{length(table_names)} table(s)")

        unless @table_name in table_names do
          raise(
            "assertion failed: table #{inspect(@table_name)} not found in #{inspect(table_names)}"
          )
        end

        IO.puts("SUCCESS: Table '#{@table_name}' found")
        Enum.each(table_names, fn name -> IO.puts("  - #{name}") end)

      {:ok, _} ->
        raise("assertion failed: empty table list")

      {:error, reason} ->
        raise("list_tables_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp put_item(config, item_key, item) do
    label =
      if item_key == "test-item-002" do
        "--- PutItem (second item) ---"
      else
        "--- PutItem ---"
      end

    IO.puts(label)

    input = %PutItemInput{
      table_name: @table_name,
      item: item
    }

    case DynamodbClient.put_item(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Item '#{item_key}' inserted")

      {:error, reason} ->
        raise("put_item_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp get_item(config, item_key) do
    IO.puts("--- GetItem ---")

    input = %GetItemInput{
      table_name: @table_name,
      key: %{
        @hash_key => {:s, item_key}
      }
    }

    case DynamodbClient.get_item(config, input) do
      {:ok, %GetItemOutput{item: item}} when is_map(item) ->
        IO.puts("SUCCESS: Retrieved item")

        name = attribute_string(Map.get(item, "Name"))
        age = attribute_number(Map.get(item, "Age"))

        if {name, age} != {"John Doe", "30"} do
          raise(
            "assertion failed: expected {\"John Doe\", \"30\"}, got #{inspect({name, age})}"
          )
        end

        IO.puts("SUCCESS: Item attributes match (Name=#{name}, Age=#{age})")
        print_item(item)

      {:ok, _} ->
        raise("assertion failed: item not found: #{item_key}")

      {:error, reason} ->
        raise("get_item_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp scan_table(config) do
    IO.puts("--- Scan ---")

    case DynamodbClient.scan(config, %ScanInput{table_name: @table_name}) do
      {:ok, items} when is_list(items) ->
        count = length(items)
        IO.puts("SUCCESS: Scanned #{count} item(s)")

        if count != 2 do
          raise("assertion failed: expected scan count 2, got #{count}")
        end

        IO.puts("SUCCESS: Scan count == 2 as expected")

        Enum.each(items, fn scan_item ->
          IO.puts("\n  Item:")
          print_item(scan_item)
        end)

      {:ok, _} ->
        raise("assertion failed: scan failed")

      {:error, reason} ->
        raise("scan_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp delete_item(config, item_key) do
    label =
      if item_key == "test-item-002" do
        "--- DeleteItem (cleanup) ---"
      else
        "--- DeleteItem ---"
      end

    IO.puts(label)

    input = %DeleteItemInput{
      table_name: @table_name,
      key: %{
        @hash_key => {:s, item_key}
      }
    }

    case DynamodbClient.delete_item(config, input) do
      {:ok, _} ->
        IO.puts("SUCCESS: Item '#{item_key}' deleted")

      {:error, reason} ->
        raise("delete_item_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp verify_deletion(config, item_key) do
    IO.puts("--- GetItem (verify deletion) ---")

    input = %GetItemInput{
      table_name: @table_name,
      key: %{
        @hash_key => {:s, item_key}
      }
    }

    case DynamodbClient.get_item(config, input) do
      {:ok, %GetItemOutput{item: nil}} ->
        IO.puts("SUCCESS: Item '#{item_key}' confirmed deleted")

      {:ok, _} ->
        raise("assertion failed: item not deleted: #{item_key}")

      {:error, reason} ->
        raise("get_item_failed: #{inspect(reason)}")
    end

    IO.puts("")
  end

  defp delete_demo_table(config) do
    IO.puts("--- DeleteTable ---")

    case DynamodbClient.delete_table(config, %DeleteTableInput{table_name: @table_name}) do
      {:ok, _} ->
        IO.puts("SUCCESS: Table '#{@table_name}' delete requested")

      {:error, %ResourceNotFoundException{}} ->
        IO.puts("SUCCESS: Table '#{@table_name}' already absent")

      {:error, reason} ->
        raise("delete_table_failed: #{inspect(reason)}")
    end

    IO.puts("--- Wait TableNotExists ---")

    wait_input = %DescribeTableInput{table_name: @table_name}

    case DynamodbWaiters.wait_table_not_exists(config, wait_input, []) do
      {:ok, _} ->
        IO.puts("SUCCESS: Table '#{@table_name}' removed\n")

      {:error, reason} ->
        raise("wait_table_not_exists_failed: #{inspect(reason)}")
    end
  end

  defp build_item_1 do
    %{
      @hash_key => {:s, "test-item-001"},
      "Name" => {:s, "John Doe"},
      "Age" => {:n, "30"},
      "Active" => {:bool, true},
      "Tags" => {:ss, ["elixir", "dynamodb", "smithy"]}
    }
  end

  defp build_item_2 do
    %{
      @hash_key => {:s, "test-item-002"},
      "Name" => {:s, "Jane Smith"},
      "Age" => {:n, "25"},
      "Active" => {:bool, false}
    }
  end

  defp print_item(item) when is_map(item) do
    Enum.each(item, fn {key, value} ->
      IO.puts("    #{key}: #{format_attribute_value(value)}")
    end)
  end

  defp attribute_string({:s, value}), do: value
  defp attribute_string(_), do: ""

  defp attribute_number({:n, value}), do: value
  defp attribute_number(_), do: ""

  defp format_attribute_value({:s, value}), do: value
  defp format_attribute_value({:n, value}), do: value
  defp format_attribute_value({:bool, true}), do: "true"
  defp format_attribute_value({:bool, false}), do: "false"
  defp format_attribute_value({:ss, value}), do: inspect(value)
  defp format_attribute_value({:ns, value}), do: inspect(value)
  defp format_attribute_value({:l, value}), do: inspect(value)
  defp format_attribute_value({:m, value}), do: inspect(value)
  defp format_attribute_value({:null, true}), do: "null"
  defp format_attribute_value({:b, _}), do: "<binary>"
  defp format_attribute_value({:bs, _}), do: "<binary set>"
  defp format_attribute_value(%{"S" => value}), do: value
  defp format_attribute_value(%{"N" => value}), do: value
  defp format_attribute_value(%{"BOOL" => value}), do: to_string(value)
  defp format_attribute_value(%{"SS" => value}), do: inspect(value)
  defp format_attribute_value(other), do: inspect(other)
end
