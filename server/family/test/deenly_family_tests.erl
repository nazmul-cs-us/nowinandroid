-module(deenly_family_tests).
-include_lib("eunit/include/eunit.hrl").
-include("jlib.hrl").
-define(D, <<"xmpp.test">>).
-define(A, <<"alice@xmpp.test">>).
-define(B, <<"bob@xmpp.test">>).
-define(AF, <<"alice@xmpp.test/android">>).
-define(BF, <<"bob@xmpp.test/ios">>).
-define(ID, <<"11111111-2222-3333-4444-555555555555">>).

family_test_() -> {setup, fun setup/0, fun cleanup/1, [
    ?_test(chat_persists_across_restart()),
    ?_test(call_history_answered_is_durable_and_idempotent()),
    ?_test(call_history_cancel_decline_and_expiry()),
    ?_test(call_history_is_private_and_removed_with_membership()),
    ?_test(call_history_cannot_be_forged_by_text_messages()),
    ?_test(reading_call_history_clears_its_missed_alert()),
    ?_test(chat_requires_accepted_family()), ?_test(chat_is_idempotent()),
    ?_test(chat_history_and_read_cursor()), ?_test(chat_validates_utf8_and_rate()),
    ?_test(chat_removal_revokes_history()), ?_test(inbox_records_invitations_and_missed_calls()),
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
    ?_test(raw_peer_presence_is_rejected()), ?_test(web_pairing_and_logout_are_scoped()),
    ?_test(web_call_outbox_is_idempotent()),
    ?_test(voice_sized_offer_passes_full_iq_path()),
    ?_test(video_sized_offer_passes_full_iq_path()),
    ?_test(video_sized_answer_passes_full_iq_path()),
    ?_test(signal_size_limit_still_applies()),
    ?_test(overall_request_limit_still_applies())]}.

video_payload(Type, Size) -> #{<<"type">> => Type, <<"sdp">> => binary:copy(<<"a">>, Size)}.
signal_iq(U, Payload) ->
    Request = #{<<"v">> => 1, <<"action">> => <<"signal">>, <<"callId">> => ?ID,
                <<"payload">> => Payload},
    Encoded = iolist_to_binary(jiffy:encode(Request)),
    El = #xmlel{name = <<"family">>, attrs = #{<<"xmlns">> => <<"urn:starception:family:1">>},
        children = [#xmlcdata{content = Encoded}]},
    {_, #iq{sub_el = [Response]}} = mod_deenly_family:process_iq(#{}, jid:from_binary(U),
        jid:from_binary(?D), #iq{type = set, sub_el = El}, #{}),
    ?assertMatch([#xmlcdata{content = Content}] when is_binary(Content), Response#xmlel.children),
    jiffy:decode(exml_query:cdata(Response), [return_maps]).
assert_routed_signal(To, Payload) ->
    receive
        {family_test_route, #{to := Target, packet := Packet}} ->
            ?assertEqual(To, jid:to_binary(Target)),
            [Body] = Packet#xmlel.children,
            ?assertMatch([#xmlcdata{content = Content}] when is_binary(Content), Body#xmlel.children),
            Event = jiffy:decode(exml_query:cdata(Body), [return_maps]),
            ?assertEqual(<<"signal">>, maps:get(<<"event">>, Event)),
            ?assertEqual(Payload, maps:get(<<"payload">>, Event))
    after 1000 -> error(expected_routed_signal)
    end.
video_sized_offer_passes_full_iq_path() ->
    reset(), active(), Payload = video_payload(<<"offer">>, 3950),
    ?assertEqual(true, maps:get(<<"ok">>, signal_iq(?AF, Payload))),
    assert_routed_signal(?BF, Payload).
voice_sized_offer_passes_full_iq_path() ->
    reset(), active(), Payload = video_payload(<<"offer">>, 1000),
    ?assertEqual(true, maps:get(<<"ok">>, signal_iq(?AF, Payload))),
    assert_routed_signal(?BF, Payload).
video_sized_answer_passes_full_iq_path() ->
    reset(), active(), Payload = video_payload(<<"answer">>, 8000),
    ?assertEqual(true, maps:get(<<"ok">>, signal_iq(?BF, Payload))),
    assert_routed_signal(?AF, Payload).
signal_size_limit_still_applies() ->
    reset(), active(), Response = signal_iq(?AF, video_payload(<<"offer">>, 35000)),
    ?assertEqual(<<"invalid_signal">>, maps:get(<<"error">>, Response)),
    receive {family_test_route, _} -> error(oversized_signal_was_forwarded) after 0 -> ok end.
overall_request_limit_still_applies() ->
    reset(), active(), Response = signal_iq(?AF, video_payload(<<"offer">>, 40000)),
    ?assertEqual(<<"invalid_request">>, maps:get(<<"error">>, Response)),
    receive {family_test_route, _} -> error(oversized_request_was_forwarded) after 0 -> ok end.
setup() ->
    Dir = "/tmp/deenly-family-eunit-" ++ integer_to_list(erlang:unique_integer([positive])),
    application:set_env(mnesia, dir, Dir),
    mnesia:create_schema([node()]), application:start(mnesia),
    {atomic, ok} = mnesia:create_table(deenly_family, [{attributes, [key, value]}, {disc_copies, [node()]}]),
    ok = mnesia:wait_for_tables([deenly_family], 10000),
    ok = mod_deenly_family_presence:init(), ok = mod_deenly_family_chat:init(), Dir.
cleanup(Dir) -> application:stop(mnesia), file:del_dir_r(Dir).
reset() -> flush_routes(), {atomic, ok} = mnesia:clear_table(deenly_family_messages),
    {atomic, ok} = mnesia:clear_table(deenly_family_inbox),
    {atomic, ok} = mnesia:clear_table(deenly_family),
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
    stopped = mnesia:stop(), ok = mnesia:start(), ok = mnesia:wait_for_tables([deenly_family, deenly_family_messages, deenly_family_inbox], 10000),
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
    ok = mnesia:wait_for_tables([deenly_family, deenly_family_messages, deenly_family_inbox], 10000),
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

with_web(Fun) ->
    Base = "/tmp/deenly-web-test-" ++ integer_to_list(erlang:unique_integer([positive])),
    Config = Base ++ ".json", Secret = Base ++ ".secret",
    ok = file:write_file(Config, <<"{\"url\":\"https://xmpp.starception.com:8443/family-helper/\"}">>),
    ok = file:write_file(Secret, <<"synthetic-web-pair-secret-01234567890123456789">>),
    os:putenv("FAMILY_WEB_CONFIG_FILE", Config), os:putenv("FAMILY_PUSH_SECRET_FILE", Secret),
    try Fun() after os:unsetenv("FAMILY_WEB_CONFIG_FILE"), os:unsetenv("FAMILY_PUSH_SECRET_FILE"), file:delete(Config), file:delete(Secret) end.
web_pairing_and_logout_are_scoped() -> reset(), with_web(fun() ->
    {atomic, {#{<<"webPairing">> := Pair}, []}} = act(?B, ?BF, <<"pair_web_helper">>, #{<<"deviceId">> => device(), <<"account">> => ?A}),
    ?assert(byte_size(maps:get(<<"code">>, Pair)) > 64),
    [Encoded, _] = binary:split(maps:get(<<"code">>, Pair), <<".">>),
    Standard = binary:replace(binary:replace(Encoded, <<"-">>, <<"+">>, [global]), <<"_">>, <<"/">>, [global]),
    Padding = binary:copy(<<"=">>, (4 - byte_size(Standard) rem 4) rem 4),
    Claim = jiffy:decode(base64:decode(<<Standard/binary, Padding/binary>>), [return_maps]),
    ?assertEqual(?B, maps:get(<<"account">>, Claim)),
    ?assertEqual(device(), maps:get(<<"owner">>, Claim)),
    ?assert(maps:get(<<"expires">>, Pair) > erlang:system_time(millisecond)),
    {atomic, Gen} = mnesia:transaction(fun() -> mnesia:read(deenly_family, {web_generation, ?D, ?B, device()}) end),
    ?assertMatch([{_, _, 0}], Gen),
    {atomic, _} = act(?A, ?AF, <<"unregister_device">>, #{<<"deviceId">> => device()}),
    {atomic, Gen2} = mnesia:transaction(fun() -> mnesia:read(deenly_family, {web_generation, ?D, ?B, device()}) end),
    ?assertEqual(Gen, Gen2),
    {atomic, _} = act(?B, ?BF, <<"unregister_device">>, #{<<"deviceId">> => device()}),
    {atomic, [{_, _, 1}]} = mnesia:transaction(fun() -> mnesia:read(deenly_family, {web_generation, ?D, ?B, device()}) end)
end).
web_call_outbox_is_idempotent() -> reset(), with_web(fun() ->
    family(),
    {atomic, _} = act(?B, ?BF, <<"pair_web_helper">>, #{<<"deviceId">> => device()}),
    {atomic, _} = call(), {atomic, _} = call(),
    [{_, #{registration := Registration}}] = push_rows(),
    ?assertEqual(<<"web">>, maps:get(platform, Registration)),
    {atomic, true} = mnesia:transaction(fun() -> mod_deenly_family_web:current(?D, ?B, Registration) end),
    {atomic, _} = act(?B, ?BF, <<"unregister_device">>, #{<<"deviceId">> => device()}),
    ?assertEqual([], push_rows()),
    {atomic, false} = mnesia:transaction(fun() -> mod_deenly_family_web:current(?D, ?B, Registration) end)
end).


chat_send(U, F, P, N, Text) ->
    Id = iolist_to_binary(io_lib:format("00000000-0000-0000-0000-~12.10.0b", [N])),
    act(U, F, <<"send_message">>, #{<<"peer">> => P, <<"id">> => Id, <<"text">> => Text}).
chat_history(U, F, P, Before) -> act(U, F, <<"chat_history">>, #{<<"peer">> => P, <<"before">> => Before}).
chat_requires_accepted_family() ->
    reset(), invite(),
    ?assertEqual({aborted, {denied, <<"not_family">>}}, chat_send(?A, ?AF, ?B, 1, <<"hello">>)),
    ?assertEqual({aborted, {denied, <<"not_family">>}}, chat_history(?B, ?BF, ?A, 999)),
    ?assertEqual({atomic, set}, mnesia:transaction(fun() -> mnesia:table_info(deenly_family_inbox, type) end)),
    ?assertEqual([node()], mnesia:table_info(deenly_family_messages, disc_copies)).
chat_is_idempotent() ->
    reset(), family(),
    {atomic, {#{<<"message">> := M}, Events}} = chat_send(?A, ?AF, ?B, 1, <<"Salaam">>),
    ?assertEqual(lists:sort([?A, ?B]), lists:sort([Target || {Target, _} <- Events])),
    {atomic, {#{<<"message">> := M}, _}} = chat_send(?A, ?AF, ?B, 1, <<"Salaam">>),
    ?assertEqual({aborted, {denied, <<"message_conflict">>}}, chat_send(?A, ?AF, ?B, 1, <<"changed">>)),
    {atomic, {Inbox, _}} = act(?B, ?BF, <<"snapshot">>, #{}),
    [S] = maps:get(<<"conversations">>, Inbox), ?assertEqual(1, maps:get(<<"unread">>, S)),
    ?assertEqual(1, maps:get(<<"unreadCount">>, Inbox)),
    {atomic, {Other, _}} = act(<<"other@xmpp.test">>, <<"other@xmpp.test/device">>, <<"snapshot">>, #{}),
    ?assertEqual([], maps:get(<<"conversations">>, Other)).
chat_history_and_read_cursor() ->
    reset(), family(),
    [begin mnesia:dirty_delete(deenly_family_inbox, {rate, ?D, ?A}),
        {atomic, _} = chat_send(?A, ?AF, ?B, N, <<"Salaam">>) end || N <- lists:seq(1, 12)],
    {atomic, {Page, _}} = chat_history(?B, ?BF, ?A, 999),
    ?assertEqual(lists:seq(3, 12), [maps:get(<<"sequence">>, M) || M <- maps:get(<<"messages">>, Page)]),
    ?assertEqual(3, maps:get(<<"nextBefore">>, Page)),
    {atomic, {First, _}} = chat_history(?B, ?BF, ?A, 3),
    ?assertEqual([1, 2], [maps:get(<<"sequence">>, M) || M <- maps:get(<<"messages">>, First)]),
    {atomic, {Read, _}} = act(?B, ?BF, <<"read_messages">>, #{<<"peer">> => ?A, <<"through">> => 10}),
    ?assertEqual(2, maps:get(<<"unreadCount">>, Read)),
    {atomic, {Old, _}} = act(?B, ?BF, <<"read_messages">>, #{<<"peer">> => ?A, <<"through">> => 2}),
    ?assertEqual(2, maps:get(<<"unreadCount">>, Old)),
    ?assert(maps:get(<<"inboxVersion">>, Old) > maps:get(<<"inboxVersion">>, Read)),
    {atomic, {Ack, _}} = act(?B, ?BF, <<"ack_notifications">>, #{}),
    ?assertEqual(2, maps:get(<<"unreadCount">>, Ack)).
chat_validates_utf8_and_rate() ->
    reset(), family(),
    [ ?assertEqual({aborted, {denied, <<"invalid_message">>}}, chat_send(?A, ?AF, ?B, 1, Text))
      || Text <- [<<>>, <<"   ">>, <<0>>, <<255>>, binary:copy(<<"a">>, 4097)] ],
    Text = unicode:characters_to_binary([16#0633, 16#0644, 16#0627, 16#0645, 32, 16#1F44B]),
    {atomic, {#{<<"message">> := Msg}, _}} = chat_send(?A, ?AF, ?B, 1, Text),
    ?assertEqual(Text, maps:get(<<"text">>, Msg)),
    Now = erlang:system_time(second),
    mnesia:dirty_write({deenly_family_inbox, {rate, ?D, ?A}, {?D, ?A}, #{second => Now, seconds => 10, minute => Now div 60, minutes => 300}}),
    ?assertEqual({aborted, {denied, <<"message_rate_limited">>}}, chat_send(?A, ?AF, ?B, 2, <<"hello">>)),
    {atomic, _} = chat_send(?A, ?AF, ?B, 1, Text).
chat_removal_revokes_history() ->
    reset(), family(), {atomic, _} = chat_send(?A, ?AF, ?B, 1, <<"private">>),
    {atomic, _} = act(?A, ?AF, <<"remove_member">>, #{<<"peer">> => ?B}),
    ?assertEqual({aborted, {denied, <<"not_family">>}}, chat_history(?B, ?BF, ?A, 999)),
    family(), {atomic, {Page, _}} = chat_history(?B, ?BF, ?A, 999),
    ?assertEqual([], maps:get(<<"messages">>, Page)),
    {ok, ok} = mod_deenly_family:remove_user(ok, #{jid => jid:from_binary(?A)}, #{}),
    ?assertEqual({aborted, {denied, <<"not_family">>}}, chat_history(?B, ?BF, ?A, 999)).
inbox_records_invitations_and_missed_calls() ->
    reset(), invite(),
    {atomic, {Inbox, _}} = act(?B, ?BF, <<"snapshot">>, #{}),
    ?assertEqual(1, maps:get(<<"unreadCount">>, Inbox)),
    {atomic, {Ack, _}} = act(?B, ?BF, <<"ack_notifications">>, #{}),
    ?assertEqual(0, maps:get(<<"unreadCount">>, Ack)),
    {atomic, _} = act(?B, ?BF, <<"accept_member">>, #{<<"peer">> => ?A}),
    {atomic, _} = call(), {atomic, _} = act(?A, ?AF, <<"end_call">>, #{<<"callId">> => ?ID}),
    {atomic, {Missed, _}} = act(?B, ?BF, <<"snapshot">>, #{}),
    [N] = maps:get(<<"notifications">>, Missed),
    ?assertEqual(<<"missed_call">>, maps:get(<<"kind">>, N)), ?assertEqual(1, maps:get(<<"count">>, N)),
    {atomic, _} = act(?A, ?AF, <<"end_call">>, #{<<"callId">> => ?ID}),
    {atomic, {Again, _}} = act(?B, ?BF, <<"snapshot">>, #{}),
    ?assertEqual(1, maps:get(<<"unreadCount">>, Again)).

flush_routes() -> receive {family_test_route, _} -> flush_routes() after 0 -> ok end.

chat_persists_across_restart() ->
    reset(), family(), {atomic, _} = chat_send(?A, ?AF, ?B, 1, <<"durable">>),
    stopped = mnesia:stop(), ok = mnesia:start(),
    ok = mnesia:wait_for_tables([deenly_family, deenly_family_messages, deenly_family_inbox], 10000),
    ok = mod_deenly_family_presence:init(),
    {atomic, {#{<<"messages">> := [Message]}, _}} = chat_history(?B, ?BF, ?A, 999),
    ?assertEqual(<<"durable">>, maps:get(<<"text">>, Message)),
    {atomic, {Inbox, _}} = act(?B, ?BF, <<"snapshot">>, #{}),
    ?assertEqual(1, maps:get(<<"unreadCount">>, Inbox)).

call_history_answered_is_durable_and_idempotent() ->
    reset(), active(),
    [{deenly_family, Key, C}] = mnesia:dirty_read(deenly_family, {call, ?D, ?ID}),
    Now = erlang:system_time(millisecond),
    mnesia:dirty_write({deenly_family, Key, C#{<<"answeredAt">> => Now - 94000, <<"startedAt">> => Now - 99000}}),
    {atomic, {_, Events}} = act(?A, ?AF, <<"end_call">>, #{<<"callId">> => ?ID}),
    ?assertEqual(2, length([E || {_, E} <- Events, maps:get(<<"event">>, E) =:= <<"chat">>])),
    {atomic, {#{<<"messages">> := [Message]}, _}} = chat_history(?A, ?AF, ?B, 999),
    ?assertEqual(<<"call:", ?ID/binary>>, maps:get(<<"id">>, Message)),
    ?assertEqual(<<"call">>, maps:get(<<"type">>, Message)),
    Detail = maps:get(<<"call">>, Message),
    ?assertEqual(<<"answered">>, maps:get(<<"outcome">>, Detail)),
    ?assert(maps:get(<<"durationSeconds">>, Detail) >= 94),
    ?assert(maps:get(<<"durationSeconds">>, Detail) =< 95),
    {atomic, _} = act(?A, ?AF, <<"end_call">>, #{<<"callId">> => ?ID}),
    stopped = mnesia:stop(), ok = mnesia:start(),
    ok = mnesia:wait_for_tables([deenly_family, deenly_family_messages, deenly_family_inbox], 10000),
    ok = mod_deenly_family_presence:init(),
    {atomic, {#{<<"messages">> := [Message]}, _}} = chat_history(?B, ?BF, ?A, 999),
    {atomic, {Inbox, _}} = act(?B, ?BF, <<"snapshot">>, #{}),
    ?assertEqual(0, maps:get(<<"unreadCount">>, Inbox)).

call_history_cancel_decline_and_expiry() ->
    reset(), family(), {atomic, _} = call(),
    {atomic, _} = act(?A, ?AF, <<"end_call">>, #{<<"callId">> => ?ID}),
    ?assertEqual(<<"cancelled">>, history_outcome()),
    reset(), family(), {atomic, _} = call(),
    {atomic, _} = act(?B, ?BF, <<"end_call">>, #{<<"callId">> => ?ID}),
    ?assertEqual(<<"declined">>, history_outcome()),
    reset(), family(), {atomic, _} = call(),
    [{deenly_family, Key, C}] = mnesia:dirty_read(deenly_family, {call, ?D, ?ID}),
    mnesia:dirty_write({deenly_family, Key, C#{<<"expires">> => 0}}),
    ?assertEqual(<<"missed">>, history_outcome()).

history_outcome() ->
    {atomic, {#{<<"messages">> := [Message]}, _}} = chat_history(?B, ?BF, ?A, 999),
    maps:get(<<"outcome">>, maps:get(<<"call">>, Message)).

call_history_is_private_and_removed_with_membership() ->
    reset(), active(), {atomic, _} = act(?A, ?AF, <<"end_call">>, #{<<"callId">> => ?ID}),
    ?assertEqual({aborted, {denied, <<"not_family">>}}, chat_history(<<"other@xmpp.test">>, <<"other@xmpp.test/device">>, ?A, 999)),
    {atomic, _} = act(?A, ?AF, <<"remove_member">>, #{<<"peer">> => ?B}),
    family(), {atomic, {#{<<"messages">> := []}, _}} = chat_history(?B, ?BF, ?A, 999).

call_history_cannot_be_forged_by_text_messages() ->
    reset(), family(),
    ?assertEqual({aborted, {denied, <<"invalid_message">>}}, act(?A, ?AF, <<"send_message">>,
        #{<<"peer">> => ?B, <<"id">> => <<"call:", ?ID/binary>>, <<"text">> => <<"fake call">>})).

reading_call_history_clears_its_missed_alert() ->
    reset(), family(), {atomic, _} = call(),
    {atomic, _} = act(?A, ?AF, <<"end_call">>, #{<<"callId">> => ?ID}),
    {atomic, {#{<<"messages">> := [Message]}, _}} = chat_history(?B, ?BF, ?A, 999),
    {atomic, {Inbox, _}} = act(?B, ?BF, <<"read_messages">>, #{<<"peer">> => ?A, <<"through">> => maps:get(<<"sequence">>, Message)}),
    ?assertEqual(0, maps:get(<<"unreadCount">>, Inbox)).
