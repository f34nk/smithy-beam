-module(aws_credentials).

%% AWS Credentials Provider
%% Loads AWS credentials from various sources following the AWS credentials chain
%% Reference: https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html

%% Public API
-export([
    get_credentials/0,
    get_credentials/1,
    get_credentials_filepath/0,
    from_environment/0,
    from_credentials_file/0,
    from_credentials_file/1
]).

%% @doc Get AWS credentials from the default credential chain
%%
%% This is a convenience function that calls get_credentials(#{}) with no options.
%% Uses the default profile ("default") for the credentials file.
%%
%% Tries credential providers in the following order:
%% 1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
%% 2. Shared credentials file (~/.aws/credentials) with "default" profile
%% 3. EC2 instance metadata (IAM role) - TODO: Not yet implemented
%% 4. ECS container metadata - TODO: Not yet implemented
%%
%% Returns the first successfully loaded credentials, or an error if none are found.
%%
%% @returns {ok, Credentials :: map()} | {error, Reason :: atom()}
%%
%% Example:
%% ```
%% case aws_credentials:get_credentials() of
%%     {ok, Credentials} ->
%%         Client = #{
%%             endpoint => <<"https://s3.amazonaws.com">>,
%%             region => <<"us-east-1">>,
%%             credentials => Credentials
%%         };
%%     {error, Reason} ->
%%         io:format("Failed to load credentials: ~p~n", [Reason])
%% end.
%% ```
-spec get_credentials() -> {ok, map()} | {error, atom()}.
get_credentials() ->
    get_credentials(#{}).

%% @doc Get AWS credentials from the credential chain with options
%%
%% Tries credential providers in the following order:
%% 1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
%% 2. Shared credentials file (~/.aws/credentials) with specified profile
%% 3. EC2 instance metadata (IAM role) - TODO: Not yet implemented
%% 4. ECS container metadata - TODO: Not yet implemented
%%
%% Returns the first successfully loaded credentials, or an error if none are found.
%%
%% Options:
%% - profile: Profile name to use for credentials file (default: <<"default">>)
%%
%% @param Options Options map with optional 'profile' key
%% @returns {ok, Credentials :: map()} | {error, Reason :: atom()}
%%
%% Example:
%% ```
%% %% Use production profile from credentials file
%% case aws_credentials:get_credentials(#{profile => <<"production">>}) of
%%     {ok, Credentials} ->
%%         %% Use production credentials
%%         Client = #{credentials => Credentials, ...};
%%     {error, no_credentials} ->
%%         io:format("No credentials found in any provider~n")
%% end.
%% ```
-spec get_credentials(map()) -> {ok, map()} | {error, atom()}.
get_credentials(Options) when is_map(Options) ->
    %% Extract profile option (default to "default")
    Profile = maps:get(profile, Options, <<"default">>),

    %% Build provider chain with profile-aware credentials file provider
    Providers = [
        fun from_environment/0,
        fun() -> from_credentials_file(Profile) end
        %% TODO: Add fun from_ec2_metadata/0 (IAM role)
        %% TODO: Add fun from_ecs_metadata/0 (ECS container credentials)
    ],

    try_providers(Providers).

%% @doc Get the filepath to the credentials file
%%
%% Returns the filepath to the credentials file in the user's home directory.
%%
%% @returns {ok, CredentialsFilePath :: string()} | {error, Reason :: atom()}
%%
%% Example:
%% ```
%% case aws_credentials:get_credentials_filepath() of
%%     {ok, CredentialsFilePath} ->
%%         %% Use credentials file
%%         {ok, CredentialsFilePath};
%%     {error, home_not_set} ->
%%         io:format("HOME environment variable not set~n")
%% end.
%% ```
-spec get_credentials_filepath() -> string().
get_credentials_filepath() ->
    case os:getenv("HOME") of
        false ->
            {error, home_not_set};
        Home ->
            filename:join([Home, ".aws", "credentials"])
    end.

%% @doc Load AWS credentials from environment variables
%%
%% Reads credentials from the following environment variables:
%% - AWS_ACCESS_KEY_ID (required)
%% - AWS_SECRET_ACCESS_KEY (required)
%% - AWS_SESSION_TOKEN (optional, for temporary credentials)
%%
%% This is the standard AWS SDK environment variable naming convention.
%% These environment variables take precedence over other credential sources
%% in the AWS credentials chain.
%%
%% @returns {ok, Credentials :: map()} | {error, Reason :: atom()}
%%
%% Example:
%% ```
%% %% In shell:
%% %% export AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
%% %% export AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
%%
%% case aws_credentials:from_environment() of
%%     {ok, Credentials} ->
%%         %% Credentials = #{
%%         %%     access_key_id => <<"AKIAIOSFODNN7EXAMPLE">>,
%%         %%     secret_access_key => <<"wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY">>
%%         %% }
%%         {ok, Credentials};
%%     {error, no_access_key} ->
%%         io:format("AWS_ACCESS_KEY_ID not set~n");
%%     {error, no_secret_key} ->
%%         io:format("AWS_SECRET_ACCESS_KEY not set~n")
%% end.
%% ```
-spec from_environment() -> {ok, map()} | {error, atom()}.
from_environment() ->
    %% Read required environment variables
    case {os:getenv("AWS_ACCESS_KEY_ID"), os:getenv("AWS_SECRET_ACCESS_KEY")} of
        {false, _} ->
            %% AWS_ACCESS_KEY_ID not set
            {error, no_access_key};
        {_, false} ->
            %% AWS_SECRET_ACCESS_KEY not set
            {error, no_secret_key};
        {AccessKeyId, SecretAccessKey} when is_list(AccessKeyId), is_list(SecretAccessKey) ->
            %% Both required credentials present
            %% Convert from list (os:getenv returns list) to binary
            Credentials = #{
                access_key_id => list_to_binary(AccessKeyId),
                secret_access_key => list_to_binary(SecretAccessKey)
            },

            %% Check for optional session token (for temporary credentials from STS)
            case os:getenv("AWS_SESSION_TOKEN") of
                false ->
                    %% No session token - permanent credentials (IAM user)
                    {ok, Credentials};
                Token when is_list(Token) ->
                    %% Session token present - temporary credentials (STS)
                    {ok, Credentials#{session_token => list_to_binary(Token)}}
            end
    end.

%% @doc Load AWS credentials from shared credentials file
%%
%% Reads credentials from ~/.aws/credentials using the "default" profile.
%% This is equivalent to calling from_credentials_file("default").
%%
%% The credentials file format is INI-style:
%% ```
%% [default]
%% aws_access_key_id = AKIAIOSFODNN7EXAMPLE
%% aws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
%% ```
%%
%% @returns {ok, Credentials :: map()} | {error, Reason :: atom()}
%%
%% Example:
%% ```
%% case aws_credentials:from_credentials_file() of
%%     {ok, Credentials} ->
%%         %% Use credentials
%%         {ok, Credentials};
%%     {error, credentials_file_not_found} ->
%%         io:format("~/.aws/credentials file not found~n");
%%     {error, {profile_not_found, Profile}} ->
%%         io:format("Profile ~s not found in credentials file~n", [Profile])
%% end.
%% ```
-spec from_credentials_file() -> {ok, map()} | {error, atom() | tuple()}.
from_credentials_file() ->
    from_credentials_file(<<"default">>).

%% @doc Load AWS credentials from shared credentials file using specified profile
%%
%% Reads credentials from ~/.aws/credentials for the given profile name.
%% Supports multiple profiles in the credentials file.
%%
%% File format:
%% ```
%% [default]
%% aws_access_key_id = AKIAIOSFODNN7EXAMPLE
%% aws_secret_access_key = wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
%%
%% [production]
%% aws_access_key_id = AKIAPRODEXAMPLE
%% aws_secret_access_key = prodSecret
%% aws_session_token = prodSessionToken
%% ```
%%
%% @param Profile Profile name as binary (e.g., <<"default">>, <<"production">>)
%% @returns {ok, Credentials :: map()} | {error, Reason :: atom() | tuple()}
%%
%% Example:
%% ```
%% case aws_credentials:from_credentials_file(<<"production">>) of
%%     {ok, Credentials} ->
%%         %% Use production credentials
%%         {ok, Credentials};
%%     {error, {profile_not_found, Profile}} ->
%%         io:format("Profile ~s not found~n", [Profile])
%% end.
%% ```
-spec from_credentials_file(binary()) -> {ok, map()} | {error, atom() | tuple()}.
from_credentials_file(Profile) when is_binary(Profile) ->
    %% Get home directory
    case os:getenv("HOME") of
        false ->
            {error, home_not_set};
        Home ->
            %% Build path to credentials file
            CredFile = filename:join([Home, ".aws", "credentials"]),

            %% Read file
            case file:read_file(CredFile) of
                {ok, Content} ->
                    parse_credentials_file(Content, Profile);
                {error, enoent} ->
                    {error, credentials_file_not_found};
                {error, Reason} ->
                    {error, {credentials_file_error, Reason}}
            end
    end.

%%====================================================================
%% Internal Functions
%%====================================================================

%% @private
%% Parse credentials file content and extract profile credentials
-spec parse_credentials_file(binary(), binary()) -> {ok, map()} | {error, tuple()}.
parse_credentials_file(Content, Profile) ->
    %% Split into lines
    Lines = binary:split(Content, <<"\n">>, [global, trim]),

    %% Build profile section header
    ProfileSection = <<"[", Profile/binary, "]">>,

    %% Find profile and extract credentials
    case find_profile_credentials(Lines, ProfileSection) of
        {ok, Credentials} ->
            {ok, Credentials};
        error ->
            {error, {profile_not_found, Profile}}
    end.

%% @private
%% Find profile section in lines and extract credentials
-spec find_profile_credentials([binary()], binary()) -> {ok, map()} | error.
find_profile_credentials([Line | Rest], ProfileSection) ->
    %% Trim whitespace from line
    TrimmedLine = string:trim(Line, both),

    case TrimmedLine of
        ProfileSection ->
            %% Found profile section, extract credentials
            extract_credentials(Rest, #{});
        _ ->
            %% Not the profile we're looking for, continue searching
            find_profile_credentials(Rest, ProfileSection)
    end;
find_profile_credentials([], _ProfileSection) ->
    %% Profile not found
    error.

%% @private
%% Extract credentials from lines until end of section
-spec extract_credentials([binary()], map()) -> {ok, map()} | error.
extract_credentials([Line | Rest], Acc) ->
    %% Trim whitespace
    TrimmedLine = string:trim(Line, both),

    case TrimmedLine of
        <<>> ->
            %% Empty line, end of section
            validate_credentials(Acc);
        <<"[", _/binary>> ->
            %% Next section started, end of current profile
            validate_credentials(Acc);
        _ ->
            %% Parse key=value line
            case binary:split(TrimmedLine, <<"=">>, [trim]) of
                [Key, Value] ->
                    %% Trim whitespace from key and value
                    TrimmedKey = string:trim(Key, both),
                    TrimmedValue = string:trim(Value, both),

                    %% Add to accumulator based on key
                    NewAcc =
                        case TrimmedKey of
                            <<"aws_access_key_id">> ->
                                Acc#{access_key_id => TrimmedValue};
                            <<"aws_secret_access_key">> ->
                                Acc#{secret_access_key => TrimmedValue};
                            <<"aws_session_token">> ->
                                Acc#{session_token => TrimmedValue};
                            _ ->
                                %% Unknown key, ignore
                                Acc
                        end,

                    extract_credentials(Rest, NewAcc);
                _ ->
                    %% Not a key=value line, skip
                    extract_credentials(Rest, Acc)
            end
    end;
extract_credentials([], Acc) ->
    %% End of file, validate what we have
    validate_credentials(Acc).

%% @private
%% Validate that we have required credentials
-spec validate_credentials(map()) -> {ok, map()} | error.
validate_credentials(#{access_key_id := _, secret_access_key := _} = Credentials) ->
    %% Have both required fields
    {ok, Credentials};
validate_credentials(_) ->
    %% Missing required fields
    error.

%% @private
%% Try each credential provider in order until one succeeds
-spec try_providers([fun(() -> {ok, map()} | {error, atom()})]) ->
    {ok, map()} | {error, no_credentials}.
try_providers([]) ->
    %% No providers succeeded
    {error, no_credentials};
try_providers([Provider | Rest]) ->
    case Provider() of
        {ok, Credentials} ->
            %% Provider succeeded - return credentials
            {ok, Credentials};
        {error, _Reason} ->
            %% Provider failed - try next provider
            try_providers(Rest)
    end.
