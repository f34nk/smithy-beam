-module(aws_demo_app).
-export([run/0]).

%% Table name and hash key from terraform/main.tf
-define(TABLE_NAME, <<"example">>).
-define(HASH_KEY, <<"TestTableHashKey">>).

run() ->
    io:format("~n=== Running DynamoDB Client Application ===~n~n"),

    %% Create DynamoDB client instance with AWS credentials
    io:format("Creating DynamoDB client...~n"),
    Config = #{
        endpoint => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        region => <<"us-east-1">>,
        service => <<"dynamodb">>,  %% Required for SigV4 signing with custom endpoints
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    },
    {ok, Client} = aws_dynamodb_client:new(Config),
    io:format("Client created successfully~n~n"),

    %% 1. List tables to verify connection
    io:format("--- ListTables ---~n"),
    case aws_dynamodb_client:list_tables(Client, #{}, #{enable_retry => false}) of
        {ok, ListOutput} ->
            TableNames = maps:get(<<"TableNames">>, ListOutput, []),
            io:format("SUCCESS: Found ~p table(s)~n", [length(TableNames)]),
            lists:foreach(
                fun(Name) -> io:format("  - ~s~n", [Name]) end,
                TableNames
            );
        {error, ListError} ->
            io:format("ERROR: ~p~n", [ListError])
    end,
    io:format("~n"),

    %% 2. Put an item into the table
    %% Note: DynamoDB attribute values must be in wire format (maps with type keys)
    %% because the generated code doesn't auto-encode tagged tuples yet
    io:format("--- PutItem ---~n"),
    ItemKey = <<"test-item-001">>,
    PutInput = #{
        <<"TableName">> => ?TABLE_NAME,
        <<"Item">> => #{
            ?HASH_KEY => #{<<"S">> => ItemKey},
            <<"Name">> => #{<<"S">> => <<"John Doe">>},
            <<"Age">> => #{<<"N">> => <<"30">>},
            <<"Active">> => #{<<"BOOL">> => true},
            <<"Tags">> => #{<<"SS">> => [<<"erlang">>, <<"dynamodb">>, <<"smithy">>]}
        }
    },
    case aws_dynamodb_client:put_item(Client, PutInput) of
        {ok, _PutOutput} ->
            io:format("SUCCESS: Item '~s' inserted~n", [ItemKey]);
        {error, PutError} ->
            io:format("ERROR: ~p~n", [PutError])
    end,
    io:format("~n"),

    %% 3. Get the item back by key
    io:format("--- GetItem ---~n"),
    GetInput = #{
        <<"TableName">> => ?TABLE_NAME,
        <<"Key">> => #{
            ?HASH_KEY => #{<<"S">> => ItemKey}
        }
    },
    case aws_dynamodb_client:get_item(Client, GetInput) of
        {ok, GetOutput} ->
            case maps:get(<<"Item">>, GetOutput, undefined) of
                undefined ->
                    io:format("Item not found~n");
                Item ->
                    io:format("SUCCESS: Retrieved item~n"),
                    print_item(Item)
            end;
        {error, GetError} ->
            io:format("ERROR: ~p~n", [GetError])
    end,
    io:format("~n"),

    %% 4. Put a second item
    io:format("--- PutItem (second item) ---~n"),
    ItemKey2 = <<"test-item-002">>,
    PutInput2 = #{
        <<"TableName">> => ?TABLE_NAME,
        <<"Item">> => #{
            ?HASH_KEY => #{<<"S">> => ItemKey2},
            <<"Name">> => #{<<"S">> => <<"Jane Smith">>},
            <<"Age">> => #{<<"N">> => <<"25">>},
            <<"Active">> => #{<<"BOOL">> => false}
        }
    },
    case aws_dynamodb_client:put_item(Client, PutInput2) of
        {ok, _PutOutput2} ->
            io:format("SUCCESS: Item '~s' inserted~n", [ItemKey2]);
        {error, PutError2} ->
            io:format("ERROR: ~p~n", [PutError2])
    end,
    io:format("~n"),

    %% 5. Scan the table to list all items
    io:format("--- Scan ---~n"),
    ScanInput = #{
        <<"TableName">> => ?TABLE_NAME
    },
    case aws_dynamodb_client:scan(Client, ScanInput) of
        {ok, ScanOutput} ->
            Count = maps:get(<<"Count">>, ScanOutput, 0),
            Items = maps:get(<<"Items">>, ScanOutput, []),
            io:format("SUCCESS: Scanned ~p item(s)~n", [Count]),
            lists:foreach(
                fun(ScanItem) ->
                    io:format("~n  Item:~n"),
                    print_item(ScanItem)
                end,
                Items
            );
        {error, ScanError} ->
            io:format("ERROR: ~p~n", [ScanError])
    end,
    io:format("~n"),

    %% 6. Delete the first item
    io:format("--- DeleteItem ---~n"),
    DeleteInput = #{
        <<"TableName">> => ?TABLE_NAME,
        <<"Key">> => #{
            ?HASH_KEY => #{<<"S">> => ItemKey}
        }
    },
    case aws_dynamodb_client:delete_item(Client, DeleteInput) of
        {ok, _DeleteOutput} ->
            io:format("SUCCESS: Item '~s' deleted~n", [ItemKey]);
        {error, DeleteError} ->
            io:format("ERROR: ~p~n", [DeleteError])
    end,
    io:format("~n"),

    %% 7. Verify deletion with GetItem
    io:format("--- GetItem (verify deletion) ---~n"),
    case aws_dynamodb_client:get_item(Client, GetInput) of
        {ok, GetOutput2} ->
            case maps:get(<<"Item">>, GetOutput2, undefined) of
                undefined ->
                    io:format("SUCCESS: Item '~s' confirmed deleted~n", [ItemKey]);
                _ ->
                    io:format("UNEXPECTED: Item still exists~n")
            end;
        {error, GetError2} ->
            io:format("ERROR: ~p~n", [GetError2])
    end,
    io:format("~n"),

    %% 8. Clean up - delete second item
    io:format("--- DeleteItem (cleanup) ---~n"),
    DeleteInput2 = #{
        <<"TableName">> => ?TABLE_NAME,
        <<"Key">> => #{
            ?HASH_KEY => #{<<"S">> => ItemKey2}
        }
    },
    case aws_dynamodb_client:delete_item(Client, DeleteInput2) of
        {ok, _} ->
            io:format("SUCCESS: Item '~s' deleted~n", [ItemKey2]);
        {error, DeleteError2} ->
            io:format("ERROR: ~p~n", [DeleteError2])
    end,

    io:format("~n=== DynamoDB Client Application Complete ===~n"),
    ok.

%% Helper to print a DynamoDB item
print_item(Item) when is_map(Item) ->
    maps:foreach(
        fun(Key, Value) ->
            io:format("    ~s: ~s~n", [Key, format_attribute_value(Value)])
        end,
        Item
    ).

%% Format DynamoDB attribute value for display
format_attribute_value(#{<<"S">> := V}) -> io_lib:format("~s", [V]);
format_attribute_value(#{<<"N">> := V}) -> io_lib:format("~s", [V]);
format_attribute_value(#{<<"BOOL">> := true}) -> "true";
format_attribute_value(#{<<"BOOL">> := false}) -> "false";
format_attribute_value(#{<<"SS">> := V}) -> io_lib:format("~p", [V]);
format_attribute_value(#{<<"NS">> := V}) -> io_lib:format("~p", [V]);
format_attribute_value(#{<<"L">> := V}) -> io_lib:format("~p", [V]);
format_attribute_value(#{<<"M">> := V}) -> io_lib:format("~p", [V]);
format_attribute_value(#{<<"NULL">> := true}) -> "null";
format_attribute_value(#{<<"B">> := _}) -> "<binary>";
format_attribute_value(#{<<"BS">> := _}) -> "<binary set>";
format_attribute_value(Other) -> io_lib:format("~p", [Other]).
