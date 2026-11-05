-module(deenly_family_tests).
-include_lib("eunit/include/eunit.hrl").
-define(D, <<"xmpp.test">>).
-define(A, <<"alice@xmpp.test">>).
-define(B, <<"bob@xmpp.test">>).
-define(AF, <<"alice@xmpp.test/android">>).
-define(BF, <<"bob@xmpp.test/ios">>).
-define(ID, <<"11111111-2222-3333-4444-555555555555">>).

family_test_() -> {setup, fun setup/0, fun cleanup/1, [
    ?_test(pending_cannot_call()), ?_test(only_invitee_accepts()),
    ?_test(mutual_family_can_call()), ?_test(only_one_device_answers()),
    ?_test(cancelled_cannot_accept()), ?_test(removal_ends_call()),
    ?_test(nonparticipant_cannot_signal()), ?_test(expiry_prevents_late_answer()),
    ?_test(relay_credentials_scoped()), ?_test(disconnected_call_lease_expires()),
    ?_test(raw_peer_messages_are_rejected()), ?_test(simultaneous_answers()),
    ?_test(restart_preserves_family()), ?_test(push_requires_family()),
    ?_test(push_registration_ownership()), ?_test(push_logout_is_scoped()),
    ?_test(push_is_durable_and_idempotent()), ?_test(push_cancelled_is_pruned()),
    ?_test(push_invalid_device_rejected()), ?_test(call_id_requires_uuid()),
    ?_test(presence_is_family_private()), ?_test(presence_combines_devices()),
    ?_test(stale_activity_cannot_restore_online()), ?_test(presence_disconnect_and_expiry()),
    ?_test(presence_removal_revokes_updates()), ?_test(presence_heartbeats_are_coalesced()),
    ?_test(raw_peer_presence_is_rejected())]}.
setup() ->
    Dir = "/tmp/deenly-family-eunit-" ++ integer_to_list(erlang:unique_integer([positive])),
    application:set_env(mnesia, dir, Dir),
    mnesia:create_schema([node()]), application:start(mnesia),
    {atomic, ok} = mnesia:create_table(deenly_family, [{attributes, [key, value]}, {disc_copies, [node()]}]),
    ok = mnesia:wait_for_tables([deenly_family], 10000),
    ok = mod_deenly_family_presence:init(), Dir.
cleanup(Dir) -> application:stop(mnesia), file:del_dir_r(Dir).
reset() -> {atomic, ok} = mnesia:clear_table(deenly_family),
    {atomic, ok} = mnesia:clear_table(deenly_family_presence),
    ok = mod_deenly_family_presence:init().
run(U, F, Req) -> mnesia:transaction(fun() ->
    mnesia:read(deenly_family, {lock, ?D}, write),
    mod_deenly_family:expire(?D), mod_deenly_family:execute(?D, U, F, Req)
end).
act(U, F, Action, Fields) -> run(U, F, Fields#{<<"action">> => Action}).
invite() -> {atomic, _} = act(?A, ?AF, <<"invite">>, #{<<"peer">> => ?B}).
family() -> invite(), {atomic, _} = act(?B, ?BF, <<"accept_member">>, #{<<"peer">> => ?A}).
call() -> act(?A, ?AF, <<"invite_call">>, #{<<"peer">> => ?B, <<"callId">> => ?ID, <<"kind">> => <<"voice">>}).
answer() -> act(?B, ?BF, <<"accept_call">>, #{<<"callId">> => ?ID}).
active() -> family(), {atomic, _} = call(), {atomic, _} = answer().
pending_cannot_call() -> reset(), invite(), ?assertEqual({aborted, {denied, <<"not_family">>}}, call()).
only_invitee_accepts() -> reset(), invite(),
    ?assertEqual({aborted, {denied, <<"invitation_not_found">>}}, act(?A, ?AF, <<"accept_member">>, #{<<"peer">> => ?B})).
mutual_family_can_call() -> reset(), family(),
    {atomic, {#{<<"call">> := C}, _}} = call(), ?assertEqual(<<"ringing">>, maps:get(<<"stage">>, C)),
    {atomic, {#{<<"call">> := C2}, _}} = call(), ?assertEqual(1, maps:get(<<"version">>, C2)),
    {atomic, {#{<<"call">> := C3}, _}} = answer(), ?assertEqual(<<"active">>, maps:get(<<"stage">>, C3)).
only_one_device_answers() -> reset(), active(),
    ?assertEqual({aborted, {denied, <<"answered_elsewhere">>}},
      act(?B, <<"bob@xmpp.test/other">>, <<"accept_call">>, #{<<"callId">> => ?ID})).
cancelled_cannot_accept() -> reset(), family(), {atomic, _} = call(),
    {atomic, _} = act(?A, ?AF, <<"end_call">>, #{<<"callId">> => ?ID}),
    ?assertEqual({aborted, {denied, <<"call_ended">>}}, answer()).
removal_ends_call() -> reset(), active(),
    {atomic, _} = act(?B, ?BF, <<"remove_member">>, #{<<"peer">> => ?A}),
    {atomic, {#{<<"calls">> := Calls, <<"members">> := Members}, _}} = act(?A, ?AF, <<"snapshot">>, #{}),
    ?assertEqual([], Calls), ?assertEqual([], Members),
    ?assertEqual({aborted, {denied, <<"not_active">>}}, act(?A, ?AF, <<"turn">>, #{<<"callId">> => ?ID})).
nonparticipant_cannot_signal() -> reset(), active(),
    ?assertEqual({aborted, {denied, <<"call_not_found">>}},
      act(<<"eve@xmpp.test">>, <<"eve@xmpp.test/web">>, <<"signal">>,
          #{<<"callId">> => ?ID, <<"payload">> => #{<<"type">> => <<"offer">>}})).
expiry_prevents_late_answer() -> reset(), family(), {atomic, _} = call(),
    change_call(<<"expires">>, 0), ?assertEqual({aborted, {denied, <<"call_ended">>}}, answer()).
relay_credentials_scoped() -> reset(), family(), {atomic, _} = call(),
    ?assertEqual({aborted, {denied, <<"not_active">>}}, act(?A, ?AF, <<"turn">>, #{<<"callId">> => ?ID})),
    {atomic, _} = answer(),
    File = "/tmp/deenly-family-test-secret", ok = file:write_file(File, <<"synthetic-test-secret">>),
    os:putenv("FAMILY_TURN_SECRET_FILE", File),
    try
        {atomic, {#{<<"iceServers">> := [Server]}, _}} = act(?A, ?AF, <<"turn">>, #{<<"callId">> => ?ID}),
        Username = maps:get(<<"username">>, Server),
        ?assertEqual(base64:encode(crypto:mac(hmac, sha, <<"synthetic-test-secret">>, Username)), maps:get(<<"credential">>, Server)),
        ?assertEqual(3, length(maps:get(<<"urls">>, Server)))
    after file:delete(File), os:unsetenv("FAMILY_TURN_SECRET_FILE") end.
disconnected_call_lease_expires() -> reset(), active(), change_call(<<"callerLease">>, 0),
    {atomic, {#{<<"calls">> := Calls}, _}} = act(?B, ?BF, <<"snapshot">>, #{}), ?assertEqual([], Calls).
change_call(Key, Value) -> {atomic, ok} = mnesia:transaction(fun() ->
    K = {call, ?D, ?ID}, [{deenly_family, K, C}] = mnesia:read(deenly_family, K, write),
    mnesia:write({deenly_family, K, C#{Key => Value}}) end).
raw_peer_messages_are_rejected() ->
    From = jid:from_binary(?A), To = jid:from_binary(?B),
    ?assertEqual({stop, drop}, mod_deenly_family:filter_packet({From, To, #{}, {xmlel, <<"message">>, #{}, []}}, #{}, #{})).

simultaneous_answers() -> reset(), family(), {atomic, _} = call(), Parent = self(),
    [spawn(fun() -> Parent ! {answer_result, act(?B, F, <<"accept_call">>, #{<<"callId">> => ?ID})} end)
     || F <- [?BF, <<"bob@xmpp.test/second">>]],
    Results = [receive {answer_result, Result} -> Result after 3000 -> error(answer_timeout) end || _ <- [1, 2]],
    ?assertEqual(1, length([ok || {atomic, _} <- Results])),
    ?assertEqual(1, length([ok || {aborted, {denied, <<"answered_elsewhere">>}} <- Results])).
restart_preserves_family() -> reset(), family(),
    {atomic, {#{<<"presenceEpoch">> := Epoch}, [_]}} = presence(?A, ?AF, true, 1),
    stopped = mnesia:stop(), ok = mnesia:start(), ok = mnesia:wait_for_tables([deenly_family], 10000),
    ok = mod_deenly_family_presence:init(),
    {atomic, {#{<<"members">> := [Member], <<"presenceEpoch">> := NewEpoch}, _}} = act(?B, ?BF, <<"snapshot">>, #{}),
    ?assertEqual(<<"accepted">>, maps:get(<<"status">>, Member)),
    ?assertEqual(<<"unknown">>, maps:get(<<"presence">>, Member)),
    ?assertNotEqual(Epoch, NewEpoch),
    %% The RAM subscription index is rebuilt from durable accepted relationships.
    {atomic, {_, [{?B, _}]}} = presence(?A, ?AF, true, 1).

register(U, F, Device, Token) -> act(U, F, <<"register_device">>, #{<<"deviceId">> => Device,
    <<"platform">> => <<"android">>, <<"token">> => Token}).
device() -> <<"00000000-1111-2222-3333-444444444444">>.
token() -> <<"synthetic-fcm-token-012345678901234567890">>.
push_rows() -> {atomic, Rows} = mnesia:transaction(fun() ->
    mnesia:foldl(fun({deenly_family, K = {push, _, _, _}, V}, A) -> [{K,V}|A]; (_, A) -> A end, [], deenly_family)
end), Rows.
push_requires_family() -> reset(), {atomic, _} = register(?B, ?BF, device(), token()),
    ?assertMatch({aborted, _}, call()), ?assertEqual([], push_rows()).
push_registration_ownership() -> reset(), {atomic, _} = register(?A, ?AF, device(), token()),
    {atomic, _} = register(?B, ?BF, device(), token()),
    {atomic, Old} = mnesia:transaction(fun() -> mnesia:read(deenly_family, {device, ?D, ?A, device()}) end),
    ?assertEqual([], Old).
push_logout_is_scoped() -> reset(), {atomic, _} = register(?B, ?BF, device(), token()),
    {atomic, _} = act(?A, ?AF, <<"unregister_device">>, #{<<"deviceId">> => device()}),
    family(), {atomic, _} = call(), ?assertEqual(1, length(push_rows())).
push_is_durable_and_idempotent() -> reset(), family(), {atomic, _} = register(?B, ?BF, device(), token()),
    {atomic, _} = call(), {atomic, _} = call(),
    ?assertEqual(1, length(push_rows())),
    mnesia:dump_log(), application:stop(mnesia), application:start(mnesia),
    ok = mnesia:wait_for_tables([deenly_family], 10000),
    ?assertEqual(1, length(push_rows())).
push_cancelled_is_pruned() -> reset(), family(), {atomic, _} = register(?B, ?BF, device(), token()),
    {atomic, _} = call(), {atomic, _} = act(?A, ?AF, <<"end_call">>, #{<<"callId">> => ?ID}),
    mod_deenly_family_push:drain(), ?assertEqual([], push_rows()).
push_invalid_device_rejected() -> reset(),
    ?assertEqual({aborted, {denied, <<"invalid_device">>}}, register(?B, ?BF, <<"short">>, token())).
call_id_requires_uuid() -> reset(), family(),
    ?assertEqual({aborted, {denied, <<"invalid_call">>}}, act(?A, ?AF, <<"invite_call">>,
        #{<<"peer">> => ?B, <<"callId">> => <<"not-a-native-call-uuid">>, <<"kind">> => <<"voice">>})).


presence(U, F, Active, Sequence) -> act(U, F, <<"presence">>, #{<<"active">> => Active, <<"activityVersion">> => Sequence}).
presence_state(U) -> {atomic, S} = mnesia:transaction(fun() -> mod_deenly_family_presence:state(?D, U) end), S.
presence_is_family_private() ->
    reset(), invite(),
    {atomic, {_, []}} = presence(?A, ?AF, true, 1),
    {atomic, {#{<<"members">> := [Pending]}, _}} = act(?B, ?BF, <<"snapshot">>, #{}),
    ?assertNot(maps:is_key(<<"presence">>, Pending)),
    {atomic, _} = act(?B, ?BF, <<"accept_member">>, #{<<"peer">> => ?A}),
    {atomic, {#{<<"members">> := [Member]}, _}} = act(?B, ?BF, <<"snapshot">>, #{}),
    ?assertEqual(<<"online">>, maps:get(<<"presence">>, Member)),
    {atomic, {_, [{?B, Event}]}} = presence(?A, ?AF, false, 2),
    ?assertEqual(<<"offline">>, maps:get(<<"presence">>, Event)),
    ?assertEqual(?A, maps:get(<<"jid">>, Event)),
    ?assert(is_binary(maps:get(<<"presenceEpoch">>, Event))),
    {atomic, {#{<<"members">> := []}, _}} = act(<<"eve@xmpp.test">>, <<"eve@xmpp.test/web">>, <<"snapshot">>, #{}),
    %% Availability must never restrict an authorized call.
    ?assertMatch({atomic, _}, call()).
presence_combines_devices() ->
    reset(), family(), Other = <<"alice@xmpp.test/second">>,
    {atomic, {_, [_]}} = presence(?A, ?AF, true, 1),
    {atomic, {_, []}} = presence(?A, Other, true, 1),
    {atomic, {_, []}} = presence(?A, ?AF, false, 2),
    ?assertEqual(<<"online">>, maps:get(<<"presence">>, presence_state(?A))),
    {atomic, {_, [_]}} = presence(?A, Other, false, 2),
    ?assertEqual(<<"offline">>, maps:get(<<"presence">>, presence_state(?A))).
stale_activity_cannot_restore_online() ->
    reset(), family(),
    {atomic, _} = presence(?A, ?AF, true, 1),
    {atomic, _} = presence(?A, ?AF, false, 3),
    {atomic, {_, []}} = presence(?A, ?AF, true, 2),
    {atomic, _} = act(?A, ?AF, <<"snapshot">>, #{<<"active">> => true, <<"activityVersion">> => 1}),
    ?assertEqual(<<"offline">>, maps:get(<<"presence">>, presence_state(?A))).
presence_disconnect_and_expiry() ->
    reset(), family(), {atomic, _} = presence(?A, ?AF, true, 1),
    {atomic, [_]} = mnesia:transaction(fun() -> mod_deenly_family_presence:remove(?D, ?A, ?AF) end),
    ?assertEqual(<<"offline">>, maps:get(<<"presence">>, presence_state(?A))),
    {atomic, _} = presence(?A, ?AF, true, 2),
    {atomic, ok} = mnesia:transaction(fun() ->
        K = {session, ?D, ?AF}, [{T, K, U, Value}] = mnesia:read(deenly_family_presence, K, write),
        mnesia:write({T, K, U, Value#{until => 0}})
    end),
    [{?D, [_]}] = mod_deenly_family_presence:sweep(),
    ?assertEqual(<<"offline">>, maps:get(<<"presence">>, presence_state(?A))).
presence_removal_revokes_updates() ->
    reset(), family(), {atomic, _} = presence(?A, ?AF, true, 1),
    {atomic, _} = act(?B, ?BF, <<"remove_member">>, #{<<"peer">> => ?A}),
    {atomic, {_, []}} = presence(?A, ?AF, false, 2),
    {atomic, {#{<<"members">> := []}, _}} = act(?B, ?BF, <<"snapshot">>, #{}).
presence_heartbeats_are_coalesced() ->
    reset(), family(), {atomic, _} = presence(?A, ?AF, true, 1),
    Before = mnesia:table_info(deenly_family_presence, size),
    State = presence_state(?A),
    [{atomic, {_, []}} = presence(?A, ?AF, true, 1) || _ <- lists:seq(1, 100)],
    ?assertEqual(Before, mnesia:table_info(deenly_family_presence, size)),
    ?assertEqual(State, presence_state(?A)).
raw_peer_presence_is_rejected() ->
    ?assertEqual({stop, drop}, mod_deenly_family:filter_packet(
        {jid:from_binary(?A), jid:from_binary(?B), #{}, {xmlel, <<"presence">>, #{<<"type">> => <<"probe">>}, []}}, #{}, #{})).
