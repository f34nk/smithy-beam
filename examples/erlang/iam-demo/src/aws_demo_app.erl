-module(aws_demo_app).
-export([run/0]).

-define(USER_NAME, <<"iam-demo-user">>).
-define(GROUP_NAME, <<"iam-demo-group">>).
-define(NEW_USER_NAME, <<"iam-demo-new-user">>).

run() ->
    io:format("~n=== Running IAM Client Application ===~n~n"),

    %% Create IAM client instance with AWS credentials
    io:format("Creating IAM client...~n"),
    Config = #{
        endpoint => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        region => <<"us-east-1">>,
        service => <<"iam">>,  %% Required for SigV4 signing with custom endpoints
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    },
    {ok, Client} = aws_iam_client:new(Config),
    io:format("Client created successfully~n~n"),

    %% 1. List users to see what was created by Terraform
    io:format("--- ListUsers ---~n"),
    case aws_iam_client:list_users(Client, #{}, #{enable_retry => false}) of
        {ok, ListUsersOutput} ->
            Users = normalize_list(maps:get(<<"Users">>, ListUsersOutput, [])),
            io:format("SUCCESS: Found ~p user(s)~n", [length(Users)]),
            lists:foreach(
                fun(User) ->
                    UserName = maps:get(<<"UserName">>, User, <<"unknown">>),
                    Path = maps:get(<<"Path">>, User, <<"/">>),
                    io:format("  - ~s (path: ~s)~n", [UserName, Path])
                end,
                Users
            );
        {error, ListUsersError} ->
            io:format("ERROR: ~p~n", [ListUsersError])
    end,
    io:format("~n"),

    %% 2. Get the user created by Terraform
    io:format("--- GetUser ---~n"),
    GetUserInput = #{<<"UserName">> => ?USER_NAME},
    case aws_iam_client:get_user(Client, GetUserInput, #{enable_retry => false}) of
        {ok, GetUserOutput} ->
            User = maps:get(<<"User">>, GetUserOutput, #{}),
            UserArn = maps:get(<<"Arn">>, User, <<"unknown">>),
            CreateDate = maps:get(<<"CreateDate">>, User, <<"unknown">>),
            io:format("SUCCESS: User '~s'~n", [?USER_NAME]),
            io:format("  ARN: ~s~n", [UserArn]),
            io:format("  Created: ~s~n", [CreateDate]);
        {error, GetUserError} ->
            io:format("ERROR: ~p~n", [GetUserError])
    end,
    io:format("~n"),

    %% 3. List groups
    io:format("--- ListGroups ---~n"),
    case aws_iam_client:list_groups(Client, #{}, #{enable_retry => false}) of
        {ok, ListGroupsOutput} ->
            Groups = normalize_list(maps:get(<<"Groups">>, ListGroupsOutput, [])),
            io:format("SUCCESS: Found ~p group(s)~n", [length(Groups)]),
            lists:foreach(
                fun(Group) ->
                    GroupName = maps:get(<<"GroupName">>, Group, <<"unknown">>),
                    Path = maps:get(<<"Path">>, Group, <<"/">>),
                    io:format("  - ~s (path: ~s)~n", [GroupName, Path])
                end,
                Groups
            );
        {error, ListGroupsError} ->
            io:format("ERROR: ~p~n", [ListGroupsError])
    end,
    io:format("~n"),

    %% 4. List groups for user
    io:format("--- ListGroupsForUser ---~n"),
    ListGroupsForUserInput = #{<<"UserName">> => ?USER_NAME},
    case aws_iam_client:list_groups_for_user(Client, ListGroupsForUserInput, #{enable_retry => false}) of
        {ok, ListGroupsForUserOutput} ->
            UserGroups = normalize_list(maps:get(<<"Groups">>, ListGroupsForUserOutput, [])),
            io:format("SUCCESS: User '~s' is in ~p group(s)~n", [?USER_NAME, length(UserGroups)]),
            lists:foreach(
                fun(Group) ->
                    GroupName = maps:get(<<"GroupName">>, Group, <<"unknown">>),
                    io:format("  - ~s~n", [GroupName])
                end,
                UserGroups
            );
        {error, ListGroupsForUserError} ->
            io:format("ERROR: ~p~n", [ListGroupsForUserError])
    end,
    io:format("~n"),

    %% 5. Create a new user
    io:format("--- CreateUser ---~n"),
    CreateUserInput = #{
        <<"UserName">> => ?NEW_USER_NAME,
        <<"Path">> => <<"/demo/">>,
        <<"Tags">> => [
            #{<<"Key">> => <<"CreatedBy">>, <<"Value">> => <<"smithy-erlang">>}
        ]
    },
    case aws_iam_client:create_user(Client, CreateUserInput, #{enable_retry => false}) of
        {ok, CreateUserOutput} ->
            NewUser = maps:get(<<"User">>, CreateUserOutput, #{}),
            NewUserArn = maps:get(<<"Arn">>, NewUser, <<"unknown">>),
            io:format("SUCCESS: Created user '~s'~n", [?NEW_USER_NAME]),
            io:format("  ARN: ~s~n", [NewUserArn]);
        {error, CreateUserError} ->
            io:format("ERROR: ~p~n", [CreateUserError])
    end,
    io:format("~n"),

    %% 6. List users again to verify
    io:format("--- ListUsers (verify) ---~n"),
    case aws_iam_client:list_users(Client, #{}, #{enable_retry => false}) of
        {ok, ListUsersOutput2} ->
            Users2 = normalize_list(maps:get(<<"Users">>, ListUsersOutput2, [])),
            io:format("SUCCESS: Found ~p user(s)~n", [length(Users2)]),
            lists:foreach(
                fun(User) ->
                    UserName = maps:get(<<"UserName">>, User, <<"unknown">>),
                    io:format("  - ~s~n", [UserName])
                end,
                Users2
            );
        {error, ListUsersError2} ->
            io:format("ERROR: ~p~n", [ListUsersError2])
    end,
    io:format("~n"),

    %% 7. Add user to group
    io:format("--- AddUserToGroup ---~n"),
    AddUserToGroupInput = #{
        <<"UserName">> => ?NEW_USER_NAME,
        <<"GroupName">> => ?GROUP_NAME
    },
    case aws_iam_client:add_user_to_group(Client, AddUserToGroupInput, #{enable_retry => false}) of
        {ok, _} ->
            io:format("SUCCESS: Added '~s' to group '~s'~n", [?NEW_USER_NAME, ?GROUP_NAME]);
        {error, AddUserError} ->
            io:format("ERROR: ~p~n", [AddUserError])
    end,
    io:format("~n"),

    %% 8. List groups for new user
    io:format("--- ListGroupsForUser (new user) ---~n"),
    ListGroupsForNewUserInput = #{<<"UserName">> => ?NEW_USER_NAME},
    case aws_iam_client:list_groups_for_user(Client, ListGroupsForNewUserInput, #{enable_retry => false}) of
        {ok, ListGroupsForNewUserOutput} ->
            NewUserGroups = normalize_list(maps:get(<<"Groups">>, ListGroupsForNewUserOutput, [])),
            io:format("SUCCESS: User '~s' is in ~p group(s)~n", [?NEW_USER_NAME, length(NewUserGroups)]),
            lists:foreach(
                fun(Group) ->
                    GroupName = maps:get(<<"GroupName">>, Group, <<"unknown">>),
                    io:format("  - ~s~n", [GroupName])
                end,
                NewUserGroups
            );
        {error, ListGroupsForNewUserError} ->
            io:format("ERROR: ~p~n", [ListGroupsForNewUserError])
    end,
    io:format("~n"),

    %% 9. Remove user from group
    io:format("--- RemoveUserFromGroup ---~n"),
    RemoveUserFromGroupInput = #{
        <<"UserName">> => ?NEW_USER_NAME,
        <<"GroupName">> => ?GROUP_NAME
    },
    case aws_iam_client:remove_user_from_group(Client, RemoveUserFromGroupInput, #{enable_retry => false}) of
        {ok, _} ->
            io:format("SUCCESS: Removed '~s' from group '~s'~n", [?NEW_USER_NAME, ?GROUP_NAME]);
        {error, RemoveUserError} ->
            io:format("ERROR: ~p~n", [RemoveUserError])
    end,
    io:format("~n"),

    %% 10. Delete the new user
    io:format("--- DeleteUser ---~n"),
    DeleteUserInput = #{<<"UserName">> => ?NEW_USER_NAME},
    case aws_iam_client:delete_user(Client, DeleteUserInput, #{enable_retry => false}) of
        {ok, _} ->
            io:format("SUCCESS: Deleted user '~s'~n", [?NEW_USER_NAME]);
        {error, DeleteUserError} ->
            io:format("ERROR: ~p~n", [DeleteUserError])
    end,
    io:format("~n"),

    %% 11. Verify deletion
    io:format("--- GetUser (verify deletion) ---~n"),
    GetDeletedUserInput = #{<<"UserName">> => ?NEW_USER_NAME},
    case aws_iam_client:get_user(Client, GetDeletedUserInput, #{enable_retry => false}) of
        {ok, _} ->
            io:format("UNEXPECTED: User still exists~n");
        {error, {aws_error, _, <<"NoSuchEntity">>, _}} ->
            io:format("SUCCESS: User '~s' confirmed deleted~n", [?NEW_USER_NAME]);
        {error, VerifyError} ->
            io:format("Result: ~p~n", [VerifyError])
    end,
    io:format("~n"),

    io:format("=== IAM Client Application Complete ===~n"),
    ok.

%% Helper to normalize AWS Query protocol list responses
%% Single items come as #{<<"member">> => Item}, multiple as [Item1, Item2, ...]
normalize_list(#{<<"member">> := Item}) when is_map(Item) -> [Item];
normalize_list(#{<<"member">> := Items}) when is_list(Items) -> Items;
normalize_list(List) when is_list(List) -> List;
normalize_list(_) -> [].
