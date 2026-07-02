-module(demo_app).
-export([run/0]).

-include("s3_types.hrl").

-define(BUCKET_NAME, <<"s3-demo-erlang-configs">>).
-define(CONFIG1_KEY, <<"configs/config1.toml">>).
-define(CONFIG1_BODY, <<"foo = \"bar\"">>).
-define(CONFIG2_KEY, <<"configs/config2.toml">>).
-define(CONFIG2_BODY, <<"baz = \"qux\"">>).
-define(OBJECT_KEY, <<"configs/test.txt">>).
-define(EXPECTED_BODY, <<"Hello World">>).

run() ->
    io:format("~n=== Running S3 Client Application ===~n~n"),

    Config = client_config(),
    io:format("S3 client configured for ~s~n~n", [maps:get(base_url, Config)]),

    setup_infrastructure(Config),

    list_buckets(Config),
    put_object(Config),
    list_objects(Config),
    get_object(Config),

    delete_demo_bucket(Config),

    io:format("=== S3 Client Application Complete ===~n"),
    ok.

client_config() ->
    #{
        region => <<"us-east-1">>,
        endpoint_prefix => <<"s3">>,
        signing_name => <<"s3">>,
        base_url => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        s3_addressing_style => path_style,
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    }.

create_demo_bucket(Config) ->
    io:format("--- CreateBucket ---~n"),
    Input = #create_bucket_input{
        bucket = ?BUCKET_NAME
    },
    case s3_client:create_bucket(Config, Input) of
        {ok, _} ->
            io:format("SUCCESS: Bucket '~s' created~n", [?BUCKET_NAME]);
        {error, #bucket_already_owned_by_you{}} ->
            io:format("SUCCESS: Bucket '~s' already exists~n", [?BUCKET_NAME]);
        {error, Reason} ->
            erlang:error({create_bucket_failed, Reason})
    end,
    io:format("~n"),
    ?BUCKET_NAME.

put_config_object(Config, Key, Body) ->
    Input = #put_object_input{
        bucket = ?BUCKET_NAME,
        key = Key,
        body = Body,
        acl = public_read
    },
    case s3_client:put_object(Config, Input) of
        {ok, _} ->
            io:format("SUCCESS: PutObject '~s'~n", [Key]);
        {error, Reason} ->
            erlang:error({put_object_failed, Key, Reason})
    end.

put_config_objects(Config) ->
    io:format("--- PutObject (config files) ---~n"),
    put_config_object(Config, ?CONFIG1_KEY, ?CONFIG1_BODY),
    put_config_object(Config, ?CONFIG2_KEY, ?CONFIG2_BODY),
    io:format("~n"),
    ok.

setup_infrastructure(Config) ->
    io:format("--- Setup infrastructure ---~n"),
    create_demo_bucket(Config),
    put_config_objects(Config),
    io:format("Infrastructure ready: Bucket=~s~n~n", [?BUCKET_NAME]),
    ok.

delete_config_object(Config, Key) ->
    case s3_client:delete_object(Config,
        #delete_object_input{bucket = ?BUCKET_NAME, key = Key}) of
        {ok, _} ->
            io:format("SUCCESS: Deleted '~s'~n", [Key]);
        {error, Reason} ->
            erlang:error({delete_object_failed, Key, Reason})
    end.

delete_demo_bucket(Config) ->
    io:format("--- DeleteObject (config files) ---~n"),
    delete_config_object(Config, ?CONFIG1_KEY),
    delete_config_object(Config, ?CONFIG2_KEY),
    delete_config_object(Config, ?OBJECT_KEY),
    io:format("--- DeleteBucket ---~n"),
    case s3_client:delete_bucket(Config,
        #delete_bucket_input{bucket = ?BUCKET_NAME}) of
        {ok, _} ->
            io:format("SUCCESS: Bucket '~s' deleted~n", [?BUCKET_NAME]);
        {error, #no_such_bucket{}} ->
            io:format("SUCCESS: Bucket '~s' already absent~n", [?BUCKET_NAME]);
        {error, Reason} ->
            erlang:error({delete_bucket_failed, Reason})
    end,
    io:format("~n"),
    ok.

list_buckets(Config) ->
    io:format("--- ListBuckets ---~n"),
    Input = #list_buckets_input{},
    case s3_client:list_buckets(Config, Input) of
        {ok, Buckets} when is_list(Buckets), Buckets =/= [] ->
            io:format("SUCCESS: Found ~p bucket(s)~n", [length(Buckets)]),
            BucketNames = [Name || #bucket{name = Name} <- Buckets, Name =/= undefined],
            case lists:member(?BUCKET_NAME, BucketNames) of
                true  -> io:format("SUCCESS: Bucket '~s' found~n", [?BUCKET_NAME]);
                false -> erlang:error({assertion_failed, {bucket_not_found, ?BUCKET_NAME}, {in, BucketNames}})
            end,
            lists:foreach(
                fun(#bucket{name = Name}) ->
                    io:format("  - ~s~n", [format_binary(Name)])
                end,
                Buckets
            );
        {ok, _} ->
            erlang:error({assertion_failed, empty_bucket_list});
        {error, Reason} ->
            erlang:error({list_buckets_failed, Reason})
    end,
    io:format("~n").

put_object(Config) ->
    io:format("--- PutObject ---~n"),
    Input = #put_object_input{
        bucket = ?BUCKET_NAME,
        key = ?OBJECT_KEY,
        body = ?EXPECTED_BODY,
        content_type = <<"text/plain">>
    },
    case s3_client:put_object(Config, Input) of
        {ok, PutOutput} ->
            io:format("SUCCESS: PutObject returned successfully!~n"),
            io:format("Response: ~p~n~n", [PutOutput]);
        {error, Reason} ->
            erlang:error({put_object_failed, Reason})
    end.

list_objects(Config) ->
    io:format("--- ListObjects ---~n"),
    Input = #list_objects_input{
        bucket = ?BUCKET_NAME,
        prefix = <<"configs/">>,
        max_keys = 100
    },
    case s3_client:list_objects(Config, Input) of
        {ok, Result} ->
            Contents = contents_from_list_objects(Result),
            case Contents of
                [] ->
                    erlang:error({assertion_failed, empty_object_list});
                _ ->
                    io:format("SUCCESS: Found ~p object(s)~n", [length(Contents)]),
                    ObjectKeys = [Key || #object{key = Key} <- Contents, Key =/= undefined],
                    case lists:member(?OBJECT_KEY, ObjectKeys) of
                        true  -> io:format("SUCCESS: Object '~s' found~n", [?OBJECT_KEY]);
                        false -> erlang:error({assertion_failed, {object_not_found, ?OBJECT_KEY}, {in, ObjectKeys}})
                    end,
                    lists:foreach(
                        fun(#object{key = Key}) ->
                            io:format("  - ~s~n", [format_binary(Key)])
                        end,
                        Contents
                    )
            end;
        {error, Reason} ->
            erlang:error({list_objects_failed, Reason})
    end,
    io:format("~n").

get_object(Config) ->
    io:format("--- GetObject ---~n"),
    Input = #get_object_input{
        bucket = ?BUCKET_NAME,
        key = ?OBJECT_KEY
    },
    case s3_client:get_object(Config, Input) of
        {ok, #get_object_output{body = Body}} ->
            io:format("SUCCESS: GetObject returned successfully!~n"),
            case Body =:= ?EXPECTED_BODY of
                true  -> io:format("SUCCESS: GetObject body matches~n");
                false -> erlang:error({assertion_failed, {expected_body, ?EXPECTED_BODY}, {got, Body}})
            end;
        {error, Reason} ->
            erlang:error({get_object_failed, Reason})
    end,
    io:format("~n").

format_binary(undefined) -> <<"unknown">>;
format_binary(B) when is_binary(B) -> B.

contents_from_list_objects(#list_objects_output{contents = Contents}) ->
    objects_from_contents(Contents);
contents_from_list_objects(Pages) when is_list(Pages) ->
    case Pages of
        [#list_objects_output{} | _] ->
            lists:flatmap(
                fun(#list_objects_output{contents = PageContents}) ->
                    objects_from_contents(PageContents)
                end,
                Pages
            );
        Objects ->
            Objects
    end.

objects_from_contents(undefined) ->
    [];
objects_from_contents(Contents) when is_list(Contents) ->
    Contents;
objects_from_contents(_) ->
    [].
