%% Durable, server-authorized text chat and a coalesced Family inbox.
%% The caller holds the domain policy lock before entering any exported operation.
-module(mod_deenly_family_chat).
-compile({no_auto_import, [now/0]}).
-export([init/0, execute/4, inbox/2, invitation/3, clear_invitation/3,
         missed/2, revoke/3, purge/2, execute_ack/2]).
-define(M, deenly_family_messages).
-define(I, deenly_family_inbox).
init() ->
    table(?M, [{type, ordered_set}, {attributes, [key, conversation, value]}, {index, [conversation]}]),
    table(?I, [{attributes, [key, account, value]}, {index, [account]}]),
    mnesia:wait_for_tables([?M, ?I], 10000).
table(T, Opts) ->
    case mnesia:create_table(T, [{disc_copies, [node()]} | Opts]) of
        {atomic, ok} -> ok;
        {aborted, {already_exists, T}} -> ok;
        Error -> error({family_chat_storage_unavailable, Error})
    end.
execute(D, U, P, #{<<"action">> := <<"send_message">>, <<"id">> := Id, <<"text">> := Text}) ->
    valid(is_binary(Id) andalso re:run(Id, <<"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$">>, [{capture, none}]) =:= match),
    valid(valid_text(Text)), Pair = pair(D, U, P),
    Message = case get(?M, {Pair, Id}) of
        undefined ->
            limit(D, U),
            require(mnesia:table_info(?M, size) < 200000, <<"chat_storage_full">>),
            Seq = counter({sequence, Pair}, Pair),
            A = summary(D, U, P), B = summary(D, P, U),
            Public = #{<<"id">> => Id, <<"from">> => U, <<"to">> => P, <<"text">> => Text,
                       <<"sequence">> => Seq, <<"createdAt">> => now()},
            Stored = Public#{ordinals => #{U => maps:get(total, A), P => maps:get(total, B) + 1}},
            mnesia:write({?M, {Pair, Seq}, Pair, Stored}),
            mnesia:write({?M, {Pair, Id}, Pair, Seq}),
            save_summary(D, U, P, A#{<<"lastMessage">> => preview(Public)}),
            Total = maps:get(total, B) + 1,
            save_summary(D, P, U, B#{total => Total, <<"unread">> => Total - maps:get(read, B), <<"lastMessage">> => preview(Public)}),
            notice(D, P, <<"message">>, U, Total - maps:get(read, B), Id), Stored;
        Seq ->
            Existing = get(?M, {Pair, Seq}),
            require(maps:get(<<"from">>, Existing) =:= U andalso maps:get(<<"text">>, Existing) =:= Text, <<"message_conflict">>), Existing
    end,
    PublicMessage = maps:remove(ordinals, Message),
    {#{<<"message">> => PublicMessage},
     [{X, (inbox(D, X))#{<<"event">> => <<"chat">>, <<"message">> => PublicMessage}} || X <- [U, P]]};
execute(D, U, P, #{<<"action">> := <<"chat_history">>} = R) ->
    Before = maps:get(<<"before">>, R, 9007199254740991),
    valid(is_integer(Before) andalso Before > 0 andalso Before =< 9007199254740991),
    Messages = history(pair(D, U, P), Before, 10, []),
    Next = case Messages of [First | _] when length(Messages) =:= 10 -> maps:get(<<"sequence">>, First); _ -> 0 end,
    {#{<<"messages">> => Messages, <<"nextBefore">> => Next}, []};
execute(D, U, P, #{<<"action">> := <<"read_messages">>, <<"through">> := Through}) ->
    valid(is_integer(Through) andalso Through > 0),
    Message = get(?M, {pair(D, U, P), Through}),
    require(is_map(Message), <<"message_not_found">>),
    S = summary(D, U, P), Read = max(maps:get(read, S), maps:get(U, maps:get(ordinals, Message))),
    Unread = maps:get(total, S) - Read,
    save_summary(D, U, P, S#{read => Read, <<"unread">> => Unread}),
    case get(?I, {notification, D, U, <<"message">>, P}) of
        undefined -> ok;
        N -> put_i({notification, D, U, <<"message">>, P}, {D, U}, N#{<<"unread">> => Unread > 0, <<"count">> => Unread})
    end,
    {inbox(D, U), [{U, (inbox(D, U))#{<<"event">> => <<"inbox">>}}]};
execute(_, _, _, _) -> mnesia:abort({denied, <<"invalid_request">>}).
inbox(D, U) ->
    Rows = mnesia:index_read(?I, {D, U}, account),
    Conversations = [maps:without([total, read], S) || {?I, {conversation, _, _, _}, _, S} <- Rows],
    Notices = [N || {?I, {notification, _, _, _, _}, _, N} <- Rows],
    Count = lists:sum([maps:get(<<"unread">>, S) || S <- Conversations]) +
        lists:sum([maps:get(<<"count">>, N) || N <- Notices, maps:get(<<"kind">>, N) =/= <<"message">>, maps:get(<<"unread">>, N)]),
    Revision = counter({inbox_revision, D, U}, {D, U}),
    #{<<"inboxVersion">> => Revision, <<"conversations">> => Conversations, <<"notifications">> => lists:sublist(lists:sort(fun(A, B) -> maps:get(<<"createdAt">>, A) > maps:get(<<"createdAt">>, B) end, Notices), 50), <<"unreadCount">> => Count}.
invitation(D, U, P) ->
    K = {notification, D, U, <<"invitation">>, P},
    case get(?I, K) of undefined -> notice(D, U, <<"invitation">>, P, 1, <<>>); _ -> ok end.
clear_invitation(D, U, P) -> mnesia:delete({?I, {notification, D, U, <<"invitation">>, P}}).
missed(D, #{<<"stage">> := <<"ringing">>, <<"caller">> := A, <<"callee">> := B, <<"id">> := Id}) ->
    K = {notification, D, B, <<"missed_call">>, A}, Old = get(?I, K),
    case Old of
        #{<<"reference">> := Id} -> [];
        _ -> Count = case Old of #{<<"unread">> := true, <<"count">> := N} -> N + 1; _ -> 1 end,
             notice(D, B, <<"missed_call">>, A, Count, Id), [{B, (inbox(D, B))#{<<"event">> => <<"inbox">>}}]
    end;
missed(_, _) -> [].
revoke(D, U, P) ->
    [begin
        mnesia:delete({?I, {conversation, D, A, B}}),
        [mnesia:delete({?I, {notification, D, A, Kind, B}}) || Kind <- [<<"message">>, <<"invitation">>, <<"missed_call">>]]
     end || {A, B} <- [{U, P}, {P, U}]],
    %% Removal deletes the conversation for both accounts; a fresh invitation starts afresh.
    Pair = pair(D, U, P),
    [mnesia:delete({?M, K}) || {?M, K, _, _} <- mnesia:index_read(?M, Pair, conversation)],
    mnesia:delete({?I, {sequence, Pair}}), ok.
purge(D, U) ->
    Rows = mnesia:index_read(?I, {D, U}, account),
    [revoke(D, U, P) || {?I, {conversation, _, _, P}, _, _} <- Rows],
    [mnesia:delete({?I, K}) || {?I, K, _, _} <- Rows], ok.
%% Acknowledging an inbox does not mark chat messages read.
execute_ack(D, U) ->
    [put_i(K, A, N#{<<"unread">> => false}) || {?I, K = {notification, _, _, Kind, _}, A, N} <- mnesia:index_read(?I, {D, U}, account), Kind =/= <<"message">>],
    {inbox(D, U), [{U, (inbox(D, U))#{<<"event">> => <<"inbox">>}}]}.
summary(D, U, P) ->
    case get(?I, {conversation, D, U, P}) of
        undefined -> #{<<"peer">> => P, <<"unread">> => 0, total => 0, read => 0}; S -> S
    end.
save_summary(D, U, P, S) -> put_i({conversation, D, U, P}, {D, U}, S).
notice(D, U, Kind, P, Count, Ref) ->
    Id = binary:encode_hex(crypto:hash(sha256, <<Kind/binary, ":", P/binary>>)),
    put_i({notification, D, U, Kind, P}, {D, U},
        #{<<"id">> => Id, <<"kind">> => Kind, <<"peer">> => P, <<"count">> => Count,
          <<"unread">> => true, <<"createdAt">> => now(), <<"reference">> => Ref}).
preview(M) ->
    Chars = unicode:characters_to_list(maps:get(<<"text">>, M)),
    M#{<<"text">> => unicode:characters_to_binary(lists:sublist(Chars, 30))}.
history(_, _, 0, Acc) -> Acc;
history(Pair, Before, Left, Acc) ->
    case mnesia:prev(?M, {Pair, Before}) of
        {Pair, Seq} when is_integer(Seq) ->
            M = get(?M, {Pair, Seq}), history(Pair, Seq, Left - 1, [maps:remove(ordinals, M) | Acc]);
        _ -> Acc
    end.
pair(D, U, P) -> {D, lists:sort([U, P])}.
get(T, K) -> case mnesia:read(T, K, write) of [{T, K, _, V}] -> V; [] -> undefined end.
put_i(K, A, V) -> mnesia:write({?I, K, A, V}).
counter(K, A) -> V = case get(?I, K) of undefined -> 1; N -> N + 1 end, put_i(K, A, V), V.
limit(D, U) ->
    K = {rate, D, U}, Second = erlang:system_time(second), Minute = Second div 60,
    Old = case get(?I, K) of undefined -> #{}; V -> V end,
    S = case maps:get(second, Old, 0) of Second -> maps:get(seconds, Old, 0); _ -> 0 end,
    M = case maps:get(minute, Old, 0) of Minute -> maps:get(minutes, Old, 0); _ -> 0 end,
    require(S < 10 andalso M < 300, <<"message_rate_limited">>),
    put_i(K, {D, U}, #{second => Second, seconds => S + 1, minute => Minute, minutes => M + 1}).
valid_text(T) when is_binary(T), byte_size(T) > 0, byte_size(T) =< 4096 ->
    case unicode:characters_to_list(T) of
        C when is_list(C) -> string:trim(C) =/= [] andalso lists:all(fun(X) -> X >= 32 andalso X =/= 127 orelse lists:member(X, [9, 10, 13]) end, C);
        _ -> false
    end;
valid_text(_) -> false.
valid(B) -> require(B, <<"invalid_message">>).
require(true, _) -> ok;
require(false, Code) -> mnesia:abort({denied, Code}).
now() -> erlang:system_time(millisecond).
