-module(demo_app).
-export([run/0]).

-include("iam_types.hrl").

-define(USER_NAME, <<"iam-demo-user">>).
-define(GROUP_NAME, <<"iam-demo-group">>).
-define(NEW_USER_NAME, <<"iam-demo-new-user">>).
-define(DEMO_PATH, <<"/demo/">>).

run() ->
    io:format("~n=== Running IAM Client Application ===~n~n"),

    Config = client_config(),
    io:format("IAM client configured for ~s~n~n", [maps:get(base_url, Config)]),

    setup_infrastructure(Config),

    %% 1. List users
    io:format("--- ListUsers ---~n"),
    CountBefore = case iam_client:list_users(Config, #list_users_input{}) of
        {ok, Users} when is_list(Users) ->
            case Users of
                [] -> erlang:error({assertion_failed, empty_user_list});
                _  -> io:format("SUCCESS: Found ~p user(s)~n", [length(Users)])
            end,
            lists:foreach(
                fun(#user{user_name = UserName, path = Path}) ->
                    io:format("  - ~s (path: ~s)~n", [UserName, Path])
                end,
                Users
            ),
            length(Users);
        {error, ListUsersError} ->
            erlang:error({list_users_failed, ListUsersError})
    end,
    io:format("~n"),

    %% 2. Get the demo user
    io:format("--- GetUser ---~n"),
    GetUserInput = #get_user_input{user_name = ?USER_NAME},
    case iam_client:get_user(Config, GetUserInput) of
        {ok, #get_user_output{user = #user{
            user_name = ReturnedUserName,
            arn = UserArn,
            create_date = CreateDate
        }}} ->
            case ReturnedUserName =:= ?USER_NAME of
                true  -> io:format("SUCCESS: GetUser UserName matches '~s'~n", [?USER_NAME]);
                false -> erlang:error({assertion_failed, {expected_username, ?USER_NAME}, {got, ReturnedUserName}})
            end,
            io:format("  ARN: ~s~n", [UserArn]),
            io:format("  Created: ~s~n", [CreateDate]);
        {error, GetUserError} ->
            erlang:error({get_user_failed, GetUserError})
    end,
    io:format("~n"),

    %% 3. List groups
    io:format("--- ListGroups ---~n"),
    case iam_client:list_groups(Config, #list_groups_input{}) of
        {ok, Groups} when is_list(Groups) ->
            case Groups of
                [] -> erlang:error({assertion_failed, empty_group_list});
                _  -> io:format("SUCCESS: Found ~p group(s)~n", [length(Groups)])
            end,
            lists:foreach(
                fun(#group{group_name = GroupName, path = Path}) ->
                    io:format("  - ~s (path: ~s)~n", [GroupName, Path])
                end,
                Groups
            );
        {error, ListGroupsError} ->
            erlang:error({list_groups_failed, ListGroupsError})
    end,
    io:format("~n"),

    %% 4. List groups for user
    io:format("--- ListGroupsForUser ---~n"),
    ListGroupsForUserInput = #list_groups_for_user_input{user_name = ?USER_NAME},
    case iam_client:list_groups_for_user(Config, ListGroupsForUserInput) of
        {ok, UserGroups} when is_list(UserGroups) ->
            io:format("SUCCESS: User '~s' is in ~p group(s)~n", [?USER_NAME, length(UserGroups)]),
            lists:foreach(
                fun(#group{group_name = GroupName}) ->
                    io:format("  - ~s~n", [GroupName])
                end,
                UserGroups
            );
        {error, ListGroupsForUserError} ->
            erlang:error({list_groups_for_user_failed, ListGroupsForUserError})
    end,
    io:format("~n"),

    %% 5. Create a new user
    io:format("--- CreateUser ---~n"),
    CreateUserInput = #create_user_input{
        user_name = ?NEW_USER_NAME,
        path = ?DEMO_PATH,
        tags = [#tag{key = <<"CreatedBy">>, value = <<"smithy-erlang">>}]
    },
    case iam_client:create_user(Config, CreateUserInput) of
        {ok, #create_user_output{user = #user{user_name = CreatedName, arn = NewUserArn}}} ->
            case CreatedName =:= ?NEW_USER_NAME of
                true  -> io:format("SUCCESS: Created user '~s'~n", [?NEW_USER_NAME]);
                false -> erlang:error({assertion_failed, {expected_username, ?NEW_USER_NAME}, {got, CreatedName}})
            end,
            io:format("  ARN: ~s~n", [NewUserArn]);
        {error, CreateUserError} ->
            erlang:error({create_user_failed, CreateUserError})
    end,
    io:format("~n"),

    %% 6. List users again to verify user count grew and new user is present
    io:format("--- ListUsers (verify) ---~n"),
    case iam_client:list_users(Config, #list_users_input{}) of
        {ok, Users2} when is_list(Users2) ->
            CountAfter = length(Users2),
            io:format("SUCCESS: Found ~p user(s)~n", [CountAfter]),
            case CountAfter > CountBefore of
                true  -> io:format("SUCCESS: User count grew from ~p to ~p~n", [CountBefore, CountAfter]);
                false -> erlang:error({assertion_failed, {user_count_unchanged, CountBefore}})
            end,
            case lists:any(fun(#user{user_name = Name}) -> Name =:= ?NEW_USER_NAME end, Users2) of
                true  -> io:format("SUCCESS: '~s' found in user list~n", [?NEW_USER_NAME]);
                false -> erlang:error({assertion_failed, {user_not_in_list, ?NEW_USER_NAME}})
            end,
            lists:foreach(
                fun(#user{user_name = UserName}) ->
                    io:format("  - ~s~n", [UserName])
                end,
                Users2
            );
        {error, ListUsersError2} ->
            erlang:error({list_users_failed, ListUsersError2})
    end,
    io:format("~n"),

    %% 7. Add user to group
    io:format("--- AddUserToGroup ---~n"),
    AddUserToGroupInput = #add_user_to_group_input{
        user_name = ?NEW_USER_NAME,
        group_name = ?GROUP_NAME
    },
    case iam_client:add_user_to_group(Config, AddUserToGroupInput) of
        {ok, _} ->
            io:format("SUCCESS: Added '~s' to group '~s'~n", [?NEW_USER_NAME, ?GROUP_NAME]);
        {error, AddUserError} ->
            erlang:error({add_user_to_group_failed, AddUserError})
    end,
    io:format("~n"),

    %% 8. List groups for new user
    io:format("--- ListGroupsForUser (new user) ---~n"),
    ListGroupsForNewUserInput = #list_groups_for_user_input{user_name = ?NEW_USER_NAME},
    case iam_client:list_groups_for_user(Config, ListGroupsForNewUserInput) of
        {ok, NewUserGroups} when is_list(NewUserGroups) ->
            case NewUserGroups of
                [] -> erlang:error({assertion_failed, {new_user_not_in_any_group, ?NEW_USER_NAME}});
                _  -> io:format("SUCCESS: User '~s' is in ~p group(s)~n", [?NEW_USER_NAME, length(NewUserGroups)])
            end,
            GroupNames = [GroupName || #group{group_name = GroupName} <- NewUserGroups],
            case lists:member(?GROUP_NAME, GroupNames) of
                true  -> io:format("SUCCESS: Group '~s' found~n", [?GROUP_NAME]);
                false -> erlang:error({assertion_failed, {group_not_found, ?GROUP_NAME}, {in, GroupNames}})
            end,
            lists:foreach(
                fun(#group{group_name = GroupName}) ->
                    io:format("  - ~s~n", [GroupName])
                end,
                NewUserGroups
            );
        {error, ListGroupsForNewUserError} ->
            erlang:error({list_groups_for_user_failed, ListGroupsForNewUserError})
    end,
    io:format("~n"),

    %% 9. Remove user from group
    io:format("--- RemoveUserFromGroup ---~n"),
    RemoveUserFromGroupInput = #remove_user_from_group_input{
        user_name = ?NEW_USER_NAME,
        group_name = ?GROUP_NAME
    },
    case iam_client:remove_user_from_group(Config, RemoveUserFromGroupInput) of
        {ok, _} ->
            io:format("SUCCESS: Removed '~s' from group '~s'~n", [?NEW_USER_NAME, ?GROUP_NAME]);
        {error, RemoveUserError} ->
            erlang:error({remove_user_from_group_failed, RemoveUserError})
    end,
    io:format("~n"),

    %% 10. Delete the new user
    io:format("--- DeleteUser ---~n"),
    DeleteUserInput = #delete_user_input{user_name = ?NEW_USER_NAME},
    case iam_client:delete_user(Config, DeleteUserInput) of
        {ok, _} ->
            io:format("SUCCESS: Deleted user '~s'~n", [?NEW_USER_NAME]);
        {error, DeleteUserError} ->
            erlang:error({delete_user_failed, DeleteUserError})
    end,
    io:format("~n"),

    %% 11. Verify deletion
    io:format("--- GetUser (verify deletion) ---~n"),
    GetDeletedUserInput = #get_user_input{user_name = ?NEW_USER_NAME},
    case iam_client:get_user(Config, GetDeletedUserInput) of
        {ok, _} ->
            erlang:error({assertion_failed, user_not_deleted, ?NEW_USER_NAME});
        {error, {<<"NoSuchEntity">>, _Message}} ->
            io:format("SUCCESS: User '~s' confirmed deleted~n", [?NEW_USER_NAME]);
        {error, VerifyError} ->
            erlang:error({get_user_failed, VerifyError})
    end,
    io:format("~n"),

    delete_demo_infrastructure(Config),

    io:format("=== IAM Client Application Complete ===~n"),
    ok.

is_entity_already_exists({error, {<<"EntityAlreadyExists">>, _Message}}) ->
    true;
is_entity_already_exists(_) ->
    false.

create_demo_group(Config) ->
    io:format("--- CreateGroup ---~n"),
    Input = #create_group_input{
        group_name = ?GROUP_NAME,
        path = ?DEMO_PATH
    },
    case iam_client:create_group(Config, Input) of
        {ok, #create_group_output{group = #group{group_name = GroupName}}} ->
            io:format("SUCCESS: Created group '~s'~n~n", [GroupName]);
        {error, Reason} ->
            case is_entity_already_exists({error, Reason}) of
                true ->
                    io:format("SUCCESS: Group '~s' already exists~n~n", [?GROUP_NAME]);
                false ->
                    erlang:error({create_group_failed, Reason})
            end
    end,
    ?GROUP_NAME.

create_demo_user(Config) ->
    io:format("--- CreateUser (demo user) ---~n"),
    Input = #create_user_input{
        user_name = ?USER_NAME,
        path = ?DEMO_PATH,
        tags = [
            #tag{key = <<"Name">>, value = ?USER_NAME},
            #tag{key = <<"Environment">>, value = <<"demo">>}
        ]
    },
    case iam_client:create_user(Config, Input) of
        {ok, #create_user_output{user = #user{user_name = UserName, arn = UserArn}}} ->
            io:format("SUCCESS: Created user '~s'~n", [UserName]),
            io:format("  ARN: ~s~n~n", [UserArn]);
        {error, Reason} ->
            case is_entity_already_exists({error, Reason}) of
                true ->
                    io:format("SUCCESS: User '~s' already exists~n~n", [?USER_NAME]);
                false ->
                    erlang:error({create_user_failed, Reason})
            end
    end,
    ?USER_NAME.

add_demo_user_to_group(Config) ->
    io:format("--- AddUserToGroup (demo user) ---~n"),
    Input = #add_user_to_group_input{
        user_name = ?USER_NAME,
        group_name = ?GROUP_NAME
    },
    case iam_client:add_user_to_group(Config, Input) of
        {ok, _} ->
            io:format("SUCCESS: Added '~s' to group '~s'~n~n", [?USER_NAME, ?GROUP_NAME]);
        {error, Reason} ->
            erlang:error({add_user_to_group_failed, Reason})
    end,
    ok.

setup_infrastructure(Config) ->
    io:format("--- Setup infrastructure ---~n"),
    create_demo_group(Config),
    create_demo_user(Config),
    add_demo_user_to_group(Config),
    io:format("Infrastructure ready: User=~s Group=~s~n~n", [?USER_NAME, ?GROUP_NAME]),
    ok.

delete_demo_infrastructure(Config) ->
    io:format("--- RemoveUserFromGroup (demo user) ---~n"),
    RemoveInput = #remove_user_from_group_input{
        user_name = ?USER_NAME,
        group_name = ?GROUP_NAME
    },
    case iam_client:remove_user_from_group(Config, RemoveInput) of
        {ok, _} ->
            io:format("SUCCESS: Removed '~s' from group '~s'~n", [?USER_NAME, ?GROUP_NAME]);
        {error, RemoveReason} ->
            erlang:error({remove_user_from_group_failed, RemoveReason})
    end,
    io:format("--- DeleteUser (demo user) ---~n"),
    DeleteUserInput = #delete_user_input{user_name = ?USER_NAME},
    case iam_client:delete_user(Config, DeleteUserInput) of
        {ok, _} ->
            io:format("SUCCESS: Deleted user '~s'~n", [?USER_NAME]);
        {error, DeleteUserReason} ->
            erlang:error({delete_user_failed, DeleteUserReason})
    end,
    io:format("--- DeleteGroup ---~n"),
    DeleteGroupInput = #delete_group_input{group_name = ?GROUP_NAME},
    case iam_client:delete_group(Config, DeleteGroupInput) of
        {ok, _} ->
            io:format("SUCCESS: Deleted group '~s'~n~n", [?GROUP_NAME]);
        {error, DeleteGroupReason} ->
            erlang:error({delete_group_failed, DeleteGroupReason})
    end,
    ok.

client_config() ->
    #{
        region => <<"us-east-1">>,
        endpoint_prefix => <<"iam">>,
        signing_name => <<"iam">>,
        base_url => unicode:characters_to_binary(os:getenv("AWS_ENDPOINT")),
        credentials => #{
            access_key_id => <<"dummy">>,
            secret_access_key => <<"dummy">>
        }
    }.
