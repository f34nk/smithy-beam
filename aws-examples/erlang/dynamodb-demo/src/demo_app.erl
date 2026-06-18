-module(demo_app).
-export([run/0]).

-include("dynamodb_types.hrl").

%% Table name and hash key from terraform/main.tf
-define(TABLE_NAME, <<"example">>).
-define(HASH_KEY, <<"TestTableHashKey">>).

run() ->
    io:format("~n=== Running DynamoDB Client Application ===~n~n"),

    Config = client_config(),
    io:format("DynamoDB client configured for ~s~n~n", [maps:get(base_url, Config)]),

    _TableName = setup_infrastructure(Config),

    %% 1. List tables to verify connection
    io:format("--- ListTables ---~n"),
    case dynamodb_client:list_tables(Config, #list_tables_input{}) of
        {ok, TableNames} when is_list(TableNames), TableNames =/= [] ->
            io:format("SUCCESS: Found ~p table(s)~n", [length(TableNames)]),
            case lists:member(?TABLE_NAME, TableNames) of
                true  -> io:format("SUCCESS: Table '~s' found~n", [?TABLE_NAME]);
                false -> erlang:error({assertion_failed, {table_not_found, ?TABLE_NAME}, {in, TableNames}})
            end,
            lists:foreach(
                fun(Name) -> io:format("  - ~s~n", [Name]) end,
                TableNames
            );
        {ok, _} ->
            erlang:error({assertion_failed, empty_table_list});
        {error, ListError} ->
            erlang:error({list_tables_failed, ListError})
    end,
    io:format("~n"),

    %% 2. Put an item into the table
    io:format("--- PutItem ---~n"),
    ItemKey = <<"test-item-001">>,
    PutInput = #put_item_input{
        table_name = ?TABLE_NAME,
        item = #{
            ?HASH_KEY => #{<<"S">> => ItemKey},
            <<"Name">> => #{<<"S">> => <<"John Doe">>},
            <<"Age">> => #{<<"N">> => <<"30">>},
            <<"Active">> => #{<<"BOOL">> => true},
            <<"Tags">> => #{<<"SS">> => [<<"erlang">>, <<"dynamodb">>, <<"smithy">>]}
        }
    },
    case dynamodb_client:put_item(Config, PutInput) of
        {ok, _PutOutput} ->
            io:format("SUCCESS: Item '~s' inserted~n", [ItemKey]);
        {error, PutError} ->
            erlang:error({put_item_failed, PutError})
    end,
    io:format("~n"),

    %% 3. Get the item back by key and assert attribute values
    io:format("--- GetItem ---~n"),
    GetInput = #get_item_input{
        table_name = ?TABLE_NAME,
        key = #{
            ?HASH_KEY => #{<<"S">> => ItemKey}
        }
    },
    case dynamodb_client:get_item(Config, GetInput) of
        {ok, #get_item_output{item = Item}} when is_map(Item) ->
            io:format("SUCCESS: Retrieved item~n"),
            Name = maps:get(<<"S">>, maps:get(<<"Name">>, Item, #{}), <<>>),
            Age  = maps:get(<<"N">>, maps:get(<<"Age">>,  Item, #{}), <<>>),
            case {Name, Age} of
                {<<"John Doe">>, <<"30">>} ->
                    io:format("SUCCESS: Item attributes match (Name=~s, Age=~s)~n", [Name, Age]);
                Got ->
                    erlang:error({assertion_failed, {expected, {<<"John Doe">>, <<"30">>}}, {got, Got}})
            end,
            print_item(Item);
        {ok, _} ->
            erlang:error({assertion_failed, item_not_found, ItemKey});
        {error, GetError} ->
            erlang:error({get_item_failed, GetError})
    end,
    io:format("~n"),

    %% 4. Put a second item
    io:format("--- PutItem (second item) ---~n"),
    ItemKey2 = <<"test-item-002">>,
    PutInput2 = #put_item_input{
        table_name = ?TABLE_NAME,
        item = #{
            ?HASH_KEY => #{<<"S">> => ItemKey2},
            <<"Name">> => #{<<"S">> => <<"Jane Smith">>},
            <<"Age">> => #{<<"N">> => <<"25">>},
            <<"Active">> => #{<<"BOOL">> => false}
        }
    },
    case dynamodb_client:put_item(Config, PutInput2) of
        {ok, _PutOutput2} ->
            io:format("SUCCESS: Item '~s' inserted~n", [ItemKey2]);
        {error, PutError2} ->
            erlang:error({put_item_failed, PutError2})
    end,
    io:format("~n"),

    %% 5. Scan the table to list all items
    io:format("--- Scan ---~n"),
    ScanInput = #scan_input{
        table_name = ?TABLE_NAME
    },
    case dynamodb_client:scan(Config, ScanInput) of
        {ok, Items} when is_list(Items) ->
            Count = length(Items),
            io:format("SUCCESS: Scanned ~p item(s)~n", [Count]),
            case Count =:= 2 of
                true  -> io:format("SUCCESS: Scan count == 2 as expected~n");
                false -> erlang:error({assertion_failed, {expected_scan_count, 2}, {got, Count}})
            end,
            lists:foreach(
                fun(ScanItem) ->
                    io:format("~n  Item:~n"),
                    print_item(ScanItem)
                end,
                Items
            );
        {ok, _} ->
            erlang:error({assertion_failed, scan_failed});
        {error, ScanError} ->
            erlang:error({scan_failed, ScanError})
    end,
    io:format("~n"),

    %% 6. Delete the first item
    io:format("--- DeleteItem ---~n"),
    DeleteInput = #delete_item_input{
        table_name = ?TABLE_NAME,
        key = #{
            ?HASH_KEY => #{<<"S">> => ItemKey}
        }
    },
    case dynamodb_client:delete_item(Config, DeleteInput) of
        {ok, _DeleteOutput} ->
            io:format("SUCCESS: Item '~s' deleted~n", [ItemKey]);
        {error, DeleteError} ->
            erlang:error({delete_item_failed, DeleteError})
    end,
    io:format("~n"),

    %% 7. Verify deletion with GetItem
    io:format("--- GetItem (verify deletion) ---~n"),
    case dynamodb_client:get_item(Config, GetInput) of
        {ok, #get_item_output{item = undefined}} ->
            io:format("SUCCESS: Item '~s' confirmed deleted~n", [ItemKey]);
        {ok, _} ->
            erlang:error({assertion_failed, item_not_deleted, ItemKey});
        {error, GetError2} ->
            erlang:error({get_item_failed, GetError2})
    end,
    io:format("~n"),

    %% 8. Clean up - delete second item
    io:format("--- DeleteItem (cleanup) ---~n"),
    DeleteInput2 = #delete_item_input{
        table_name = ?TABLE_NAME,
        key = #{
            ?HASH_KEY => #{<<"S">> => ItemKey2}
        }
    },
    case dynamodb_client:delete_item(Config, DeleteInput2) of
        {ok, _} ->
            io:format("SUCCESS: Item '~s' deleted~n", [ItemKey2]);
        {error, DeleteError2} ->
            erlang:error({delete_item_failed, DeleteError2})
    end,
    io:format("~n"),

    delete_demo_table(Config),

    io:format("=== DynamoDB Client Application Complete ===~n"),
    ok.

client_config() ->
    #{
        region => <<"us-east-1">>,
        endpoint_prefix => <<"dynamodb">>,
        signing_name => <<"dynamodb">>,
        base_url => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    }.

create_demo_table(Config) ->
    io:format("--- CreateTable ---~n"),
    Input = #create_table_input{
        table_name = ?TABLE_NAME,
        attribute_definitions = [
            #attribute_definition{
                attribute_name = ?HASH_KEY,
                attribute_type = s
            }
        ],
        key_schema = [
            #key_schema_element{
                attribute_name = ?HASH_KEY,
                key_type = hash
            }
        ],
        billing_mode = pay_per_request
    },
    case dynamodb_client:create_table(Config, Input) of
        {ok, _} ->
            io:format("SUCCESS: Table '~s' create requested~n", [?TABLE_NAME]);
        {error, #resource_in_use_exception{}} ->
            io:format("SUCCESS: Table '~s' already exists~n", [?TABLE_NAME]);
        {error, Reason} ->
            erlang:error({create_table_failed, Reason})
    end,
    io:format("--- Wait TableExists ---~n"),
    WaitInput = #describe_table_input{table_name = ?TABLE_NAME},
    case dynamodb_waiters:wait_table_exists(Config, WaitInput, #{}) of
        {ok, _} ->
            io:format("SUCCESS: Table '~s' is ACTIVE~n~n", [?TABLE_NAME]);
        {error, WaitReason} ->
            erlang:error({wait_table_exists_failed, WaitReason})
    end,
    ?TABLE_NAME.

setup_infrastructure(Config) ->
    io:format("--- Setup infrastructure ---~n"),
    TableName = create_demo_table(Config),
    io:format("Infrastructure ready: TableName=~s~n~n", [TableName]),
    TableName.

delete_demo_table(Config) ->
    io:format("--- DeleteTable ---~n"),
    case dynamodb_client:delete_table(Config,
        #delete_table_input{table_name = ?TABLE_NAME}) of
        {ok, _} ->
            io:format("SUCCESS: Table '~s' delete requested~n", [?TABLE_NAME]);
        {error, #resource_not_found_exception{}} ->
            io:format("SUCCESS: Table '~s' already absent~n", [?TABLE_NAME]);
        {error, Reason} ->
            erlang:error({delete_table_failed, Reason})
    end,
    io:format("--- Wait TableNotExists ---~n"),
    case dynamodb_waiters:wait_table_not_exists(Config,
        #describe_table_input{table_name = ?TABLE_NAME}, #{}) of
        {ok, _} ->
            io:format("SUCCESS: Table '~s' removed~n~n", [?TABLE_NAME]);
        {error, WaitReason} ->
            erlang:error({wait_table_not_exists_failed, WaitReason})
    end,
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
