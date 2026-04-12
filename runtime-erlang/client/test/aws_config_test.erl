-module(aws_config_test).

-include_lib("eunit/include/eunit.hrl").

%%% Tests for AWS config module
%%% Tests region configuration and resolution

%%====================================================================
%% get_region/0 and get_region/1 Tests
%%====================================================================

%% Test get_region with AWS_REGION environment variable
get_region_from_aws_region_env_test() ->
    %% Set AWS_REGION
    os:putenv("AWS_REGION", "eu-west-1"),

    %% Unset AWS_DEFAULT_REGION
    os:unsetenv("AWS_DEFAULT_REGION"),

    %% Get region
    {ok, Region} = aws_config:get_region(),

    %% Cleanup
    os:unsetenv("AWS_REGION"),

    %% Verify
    ?assertEqual(<<"eu-west-1">>, Region).

%% Test get_region with AWS_DEFAULT_REGION environment variable
get_region_from_aws_default_region_env_test() ->
    %% Unset AWS_REGION
    os:unsetenv("AWS_REGION"),

    %% Set AWS_DEFAULT_REGION
    os:putenv("AWS_DEFAULT_REGION", "ap-southeast-1"),

    %% Get region
    {ok, Region} = aws_config:get_region(),

    %% Cleanup
    os:unsetenv("AWS_DEFAULT_REGION"),

    %% Verify
    ?assertEqual(<<"ap-southeast-1">>, Region).

%% Test AWS_REGION takes precedence over AWS_DEFAULT_REGION
get_region_aws_region_precedence_test() ->
    %% Set both environment variables
    os:putenv("AWS_REGION", "us-west-2"),
    os:putenv("AWS_DEFAULT_REGION", "us-east-1"),

    %% Get region
    {ok, Region} = aws_config:get_region(),

    %% Cleanup
    os:unsetenv("AWS_REGION"),
    os:unsetenv("AWS_DEFAULT_REGION"),

    %% Verify AWS_REGION takes precedence
    ?assertEqual(<<"us-west-2">>, Region).

%% Test get_region with explicit region option
get_region_explicit_option_test() ->
    %% Set environment variables
    os:putenv("AWS_REGION", "us-west-2"),

    %% Get region with explicit option (should override environment)
    {ok, Region} = aws_config:get_region(#{region => <<"eu-central-1">>}),

    %% Cleanup
    os:unsetenv("AWS_REGION"),

    %% Verify explicit option takes precedence
    ?assertEqual(<<"eu-central-1">>, Region).

%% Test get_region with explicit region as string
get_region_explicit_string_test() ->
    %% Get region with string option
    {ok, Region} = aws_config:get_region(#{region => "ap-northeast-1"}),

    %% Verify converted to binary
    ?assertEqual(<<"ap-northeast-1">>, Region).

%% Test get_region defaults to us-east-1
get_region_default_test() ->
    %% Unset all environment variables
    os:unsetenv("AWS_REGION"),
    os:unsetenv("AWS_DEFAULT_REGION"),

    %% Backup config file if present
    Home = os:getenv("HOME"),
    ConfigFile = filename:join([Home, ".aws", "config"]),
    BackupFile = ConfigFile ++ ".test_backup",

    _ =
        case filelib:is_file(ConfigFile) of
            true -> file:rename(ConfigFile, BackupFile);
            false -> ok
        end,

    %% Get region (should default to us-east-1)
    {ok, Region} = aws_config:get_region(),

    %% Restore config file
    _ =
        case filelib:is_file(BackupFile) of
            true -> file:rename(BackupFile, ConfigFile);
            false -> ok
        end,

    %% Verify default
    ?assertEqual(<<"us-east-1">>, Region).

%% Test get_region from config file default profile
get_region_from_config_file_default_test() ->
    %% Unset environment variables
    os:unsetenv("AWS_REGION"),
    os:unsetenv("AWS_DEFAULT_REGION"),

    %% Create temporary config file
    Home = os:getenv("HOME"),
    AwsDir = filename:join(Home, ".aws"),
    ConfigFile = filename:join(AwsDir, "config"),

    %% Backup existing file
    BackupFile = ConfigFile ++ ".test_backup",
    _ =
        case filelib:is_file(ConfigFile) of
            true -> file:rename(ConfigFile, BackupFile);
            false -> ok
        end,

    _ = filelib:ensure_dir(ConfigFile),

    %% Write test config
    Content = <<"[default]\n", "region = us-west-1\n">>,
    _ = file:write_file(ConfigFile, Content),

    %% Get region
    {ok, Region} = aws_config:get_region(),

    %% Cleanup
    _ = file:delete(ConfigFile),
    _ =
        case filelib:is_file(BackupFile) of
            true -> file:rename(BackupFile, ConfigFile);
            false -> ok
        end,

    %% Verify
    ?assertEqual(<<"us-west-1">>, Region).

%% Test get_region from config file with custom profile
get_region_from_config_file_custom_profile_test() ->
    %% Unset environment variables
    os:unsetenv("AWS_REGION"),
    os:unsetenv("AWS_DEFAULT_REGION"),

    %% Create temporary config file
    Home = os:getenv("HOME"),
    AwsDir = filename:join(Home, ".aws"),
    ConfigFile = filename:join(AwsDir, "config"),

    BackupFile = ConfigFile ++ ".test_backup",
    _ =
        case filelib:is_file(ConfigFile) of
            true -> file:rename(ConfigFile, BackupFile);
            false -> ok
        end,

    _ = filelib:ensure_dir(ConfigFile),

    %% Write test config with multiple profiles
    Content =
        <<"[default]\n", "region = us-east-1\n", "\n", "[profile production]\n",
            "region = eu-west-1\n", "\n", "[profile staging]\n", "region = ap-southeast-2\n">>,
    _ = file:write_file(ConfigFile, Content),

    %% Get region with production profile
    {ok, Region} = aws_config:get_region(#{profile => <<"production">>}),

    %% Cleanup
    _ = file:delete(ConfigFile),
    _ =
        case filelib:is_file(BackupFile) of
            true -> file:rename(BackupFile, ConfigFile);
            false -> ok
        end,

    %% Verify
    ?assertEqual(<<"eu-west-1">>, Region).

%% Test config file with whitespace
get_region_from_config_file_whitespace_test() ->
    %% Unset environment variables
    os:unsetenv("AWS_REGION"),
    os:unsetenv("AWS_DEFAULT_REGION"),

    Home = os:getenv("HOME"),
    AwsDir = filename:join(Home, ".aws"),
    ConfigFile = filename:join(AwsDir, "config"),

    BackupFile = ConfigFile ++ ".test_backup",
    _ =
        case filelib:is_file(ConfigFile) of
            true -> file:rename(ConfigFile, BackupFile);
            false -> ok
        end,

    _ = filelib:ensure_dir(ConfigFile),

    %% Write config with extra whitespace
    Content = <<"  [default]  \n", "  region  =  ca-central-1  \n">>,
    _ = file:write_file(ConfigFile, Content),

    {ok, Region} = aws_config:get_region(),

    _ = file:delete(ConfigFile),
    _ =
        case filelib:is_file(BackupFile) of
            true -> file:rename(BackupFile, ConfigFile);
            false -> ok
        end,

    ?assertEqual(<<"ca-central-1">>, Region).

%% Test environment takes precedence over config file
get_region_environment_precedence_test() ->
    %% Set environment variable
    os:putenv("AWS_REGION", "us-east-2"),

    %% Create config file
    Home = os:getenv("HOME"),
    AwsDir = filename:join(Home, ".aws"),
    ConfigFile = filename:join(AwsDir, "config"),

    BackupFile = ConfigFile ++ ".test_backup",
    _ =
        case filelib:is_file(ConfigFile) of
            true -> file:rename(ConfigFile, BackupFile);
            false -> ok
        end,

    _ = filelib:ensure_dir(ConfigFile),

    Content = <<"[default]\n", "region = us-west-2\n">>,
    _ = file:write_file(ConfigFile, Content),

    %% Get region - environment should win
    {ok, Region} = aws_config:get_region(),

    _ = file:delete(ConfigFile),
    _ =
        case filelib:is_file(BackupFile) of
            true -> file:rename(BackupFile, ConfigFile);
            false -> ok
        end,
    os:unsetenv("AWS_REGION"),

    ?assertEqual(<<"us-east-2">>, Region).

%% Test explicit option takes precedence over all
get_region_explicit_precedence_test() ->
    %% Set environment
    os:putenv("AWS_REGION", "us-west-2"),

    %% Create config file
    Home = os:getenv("HOME"),
    AwsDir = filename:join(Home, ".aws"),
    ConfigFile = filename:join(AwsDir, "config"),

    BackupFile = ConfigFile ++ ".test_backup",
    _ =
        case filelib:is_file(ConfigFile) of
            true -> file:rename(ConfigFile, BackupFile);
            false -> ok
        end,

    _ = filelib:ensure_dir(ConfigFile),

    Content = <<"[default]\n", "region = eu-west-1\n">>,
    _ = file:write_file(ConfigFile, Content),

    %% Get region with explicit option - should override everything
    {ok, Region} = aws_config:get_region(#{region => <<"ap-south-1">>}),

    _ = file:delete(ConfigFile),
    _ =
        case filelib:is_file(BackupFile) of
            true -> file:rename(BackupFile, ConfigFile);
            false -> ok
        end,
    os:unsetenv("AWS_REGION"),

    ?assertEqual(<<"ap-south-1">>, Region).

%% Test profile not found in config file defaults to us-east-1
get_region_profile_not_found_test() ->
    %% Unset environment variables
    os:unsetenv("AWS_REGION"),
    os:unsetenv("AWS_DEFAULT_REGION"),

    Home = os:getenv("HOME"),
    AwsDir = filename:join(Home, ".aws"),
    ConfigFile = filename:join(AwsDir, "config"),

    BackupFile = ConfigFile ++ ".test_backup",
    _ =
        case filelib:is_file(ConfigFile) of
            true -> file:rename(ConfigFile, BackupFile);
            false -> ok
        end,

    _ = filelib:ensure_dir(ConfigFile),

    %% Write config with only default profile
    Content = <<"[default]\n", "region = us-east-1\n">>,
    _ = file:write_file(ConfigFile, Content),

    %% Try to get region with non-existent profile
    {ok, Region} = aws_config:get_region(#{profile => <<"nonexistent">>}),

    _ = file:delete(ConfigFile),
    _ =
        case filelib:is_file(BackupFile) of
            true -> file:rename(BackupFile, ConfigFile);
            false -> ok
        end,

    %% Should default to us-east-1
    ?assertEqual(<<"us-east-1">>, Region).

%% Test all common AWS regions
get_region_valid_regions_test() ->
    Regions = [
        <<"us-east-1">>,
        <<"us-east-2">>,
        <<"us-west-1">>,
        <<"us-west-2">>,
        <<"eu-west-1">>,
        <<"eu-west-2">>,
        <<"eu-west-3">>,
        <<"eu-central-1">>,
        <<"ap-south-1">>,
        <<"ap-northeast-1">>,
        <<"ap-northeast-2">>,
        <<"ap-southeast-1">>,
        <<"ap-southeast-2">>,
        <<"ca-central-1">>,
        <<"sa-east-1">>
    ],

    lists:foreach(
        fun(Region) ->
            {ok, ReturnedRegion} = aws_config:get_region(#{region => Region}),
            ?assertEqual(Region, ReturnedRegion)
        end,
        Regions
    ).
