-module(aws_config).

%% AWS Configuration Management
%% Handles AWS region configuration and resolution
%% Reference: https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html

%% Public API
-export([
    get_region/0,
    get_region/1
]).

%% @doc Get AWS region from the default configuration chain
%%
%% Tries region sources in the following order:
%% 1. AWS_REGION environment variable
%% 2. AWS_DEFAULT_REGION environment variable
%% 3. Shared config file (~/.aws/config) with "default" profile
%% 4. Default to "us-east-1"
%%
%% Returns the first successfully resolved region.
%%
%% @returns {ok, Region :: binary()}
%%
%% Example:
%% ```
%% case aws_config:get_region() of
%%     {ok, Region} ->
%%         io:format("Using region: ~s~n", [Region]),
%%         Client = #{
%%             endpoint => <<"https://s3.amazonaws.com">>,
%%             region => Region
%%         };
%%     {error, Reason} ->
%%         io:format("Failed to get region: ~p~n", [Reason])
%% end.
%% ```
-spec get_region() -> {ok, binary()}.
get_region() ->
    get_region(#{}).

%% @doc Get AWS region with options
%%
%% Tries region sources in the following order:
%% 1. Explicit region option (if provided)
%% 2. AWS_REGION environment variable
%% 3. AWS_DEFAULT_REGION environment variable
%% 4. Shared config file (~/.aws/config) with specified profile
%% 5. Default to "us-east-1"
%%
%% Returns the first successfully resolved region.
%%
%% Options:
%% - region: Explicit region to use (highest priority)
%% - profile: Profile name for config file (default: <<"default">>)
%%
%% @param Options Options map with optional 'region' and 'profile' keys
%% @returns {ok, Region :: binary()}
%%
%% Example:
%% ```
%% %% Use explicit region
%% {ok, Region1} = aws_config:get_region(#{region => <<"us-west-2">>}),
%%
%% %% Use production profile from config file
%% {ok, Region2} = aws_config:get_region(#{profile => <<"production">>}),
%%
%% %% Let it auto-detect
%% {ok, Region3} = aws_config:get_region(#{}).
%% ```
-spec get_region(map()) -> {ok, binary()}.
get_region(Options) when is_map(Options) ->
    %% 1. Check for explicit region option
    case maps:get(region, Options, undefined) of
        undefined ->
            %% 2. Check AWS_REGION environment variable
            case os:getenv("AWS_REGION") of
                false ->
                    %% 3. Check AWS_DEFAULT_REGION environment variable
                    case os:getenv("AWS_DEFAULT_REGION") of
                        false ->
                            %% 4. Try config file
                            Profile = maps:get(profile, Options, <<"default">>),
                            from_config_file(Profile);
                        Region ->
                            {ok, list_to_binary(Region)}
                    end;
                Region ->
                    {ok, list_to_binary(Region)}
            end;
        Region when is_binary(Region) ->
            {ok, Region};
        Region when is_list(Region) ->
            {ok, list_to_binary(Region)}
    end.

%%====================================================================
%% Internal Functions
%%====================================================================

%% @private
%% Load region from AWS config file
-spec from_config_file(binary()) -> {ok, binary()}.
from_config_file(Profile) ->
    case os:getenv("HOME") of
        false ->
            %% No HOME directory - use default
            {ok, <<"us-east-1">>};
        Home ->
            ConfigFile = filename:join([Home, ".aws", "config"]),

            case file:read_file(ConfigFile) of
                {ok, Content} ->
                    case parse_region_from_config(Content, Profile) of
                        {ok, Region} -> {ok, Region};
                        error -> {ok, <<"us-east-1">>}
                    end;
                {error, _} ->
                    %% Config file not found or not readable - use default
                    {ok, <<"us-east-1">>}
            end
    end.

%% @private
%% Parse region from config file content
-spec parse_region_from_config(binary(), binary()) -> {ok, binary()} | error.
parse_region_from_config(Content, Profile) ->
    %% Split into lines
    Lines = binary:split(Content, <<"\n">>, [global, trim]),

    %% Build profile section header
    %% Note: config file uses [profile name] format (not [name] like credentials file)
    ProfileSection =
        case Profile of
            <<"default">> ->
                %% Special case: default profile is just [default]
                <<"[default]">>;
            _ ->
                %% Other profiles use [profile name]
                <<"[profile ", Profile/binary, "]">>
        end,

    %% Find profile and extract region
    find_profile_region(Lines, ProfileSection).

%% @private
%% Find profile section in lines and extract region
-spec find_profile_region([binary()], binary()) -> {ok, binary()} | error.
find_profile_region([Line | Rest], ProfileSection) ->
    %% Trim whitespace from line
    TrimmedLine = string:trim(Line, both),

    case TrimmedLine of
        ProfileSection ->
            %% Found profile section, extract region
            extract_region_from_section(Rest);
        _ ->
            %% Not the profile we're looking for, continue searching
            find_profile_region(Rest, ProfileSection)
    end;
find_profile_region([], _ProfileSection) ->
    %% Profile not found
    error.

%% @private
%% Extract region from profile section lines
-spec extract_region_from_section([binary()]) -> {ok, binary()} | error.
extract_region_from_section([Line | Rest]) ->
    %% Trim whitespace
    TrimmedLine = string:trim(Line, both),

    case TrimmedLine of
        <<>> ->
            %% Empty line, end of section
            error;
        <<"[", _/binary>> ->
            %% Next section started, region not found in this section
            error;
        _ ->
            %% Parse key=value line
            case binary:split(TrimmedLine, <<"=">>, [trim]) of
                [Key, Value] ->
                    %% Trim whitespace from key and value
                    TrimmedKey = string:trim(Key, both),
                    TrimmedValue = string:trim(Value, both),

                    case TrimmedKey of
                        <<"region">> ->
                            %% Found region!
                            {ok, TrimmedValue};
                        _ ->
                            %% Not region key, continue
                            extract_region_from_section(Rest)
                    end;
                _ ->
                    %% Not a key=value line, skip
                    extract_region_from_section(Rest)
            end
    end;
extract_region_from_section([]) ->
    %% End of file, region not found
    error.
