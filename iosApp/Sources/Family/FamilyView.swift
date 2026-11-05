import SwiftUI
import Shared
import WebRTC

struct FamilyView: View {
    @ObservedObject private var store = FamilyStore.shared
    #if DEBUG
    @State private var account = FamilyEndpoint().iosTestAccount
    @State private var password = "1234567812345678"
    #else
    @State private var account = ""
    @State private var password = ""
    #endif
    @State private var invitation = ""
    @State private var search = ""
    @State private var invitations = false
    @State private var inviting = false
    @State private var removing: FamilyMemberRow?
    @State private var quickCall: FamilyMemberRow?
    private let green = Color(red: 0.09, green: 0.25, blue: 0.23)
    private var family: [FamilyMemberRow] { store.members.filter { $0.status == "accepted" } }
    private var requests: [FamilyMemberRow] { store.members.filter { $0.status != "accepted" } }
    private var shown: [FamilyMemberRow] {
        (invitations ? requests : family).filter {
            search.isEmpty || $0.jid.localizedCaseInsensitiveContains(search) || familyName($0.jid).localizedCaseInsensitiveContains(search)
        }
    }
    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                if store.connected { contactHeader }
                VStack(alignment: .leading, spacing: 18) {
                    if store.connected { contactList } else { loginFields }
                    if let message = store.message {
                        Text(message).font(.callout).foregroundStyle(.red)
                        Button("Dismiss") { store.message = nil }
                    }
                }.padding(22).frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(uiColor: .systemBackground), in: RoundedRectangle(cornerRadius: 28))
            }
        }
        .background(green)
        .navigationTitle("Family").navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(green, for: .navigationBar).toolbarBackground(.visible, for: .navigationBar)
        .toolbarColorScheme(.dark, for: .navigationBar)
        .toolbar {
            if store.connected {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button { inviting = true } label: { Image(systemName: "person.badge.plus") }
                        .accessibilityLabel("Invite family member").disabled(!store.serviceReady)
                    Menu {
                        Button { store.refresh() } label: { Label("Refresh", systemImage: "arrow.clockwise") }
                        Button(role: .destructive) { store.disconnect() } label: { Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right") }
                    } label: { Image(systemName: "ellipsis") }.accessibilityLabel("Family account options")
                }
            }
        }
        .refreshable { store.refresh() }
        .onAppear { suggestPeer() }
        .onChange(of: store.me) { _ in suggestPeer() }
        .sheet(isPresented: $inviting) { invitationSheet }
        .confirmationDialog("Remove this family relationship?", isPresented: Binding(
            get: { removing != nil }, set: { if !$0 { removing = nil } })) {
            if let member = removing {
                Button("Remove", role: .destructive) { store.removeMember(member.jid); removing = nil }
            }
        } message: { Text("Calling access requires an accepted family relationship.") }
        .confirmationDialog(quickCall.map { familyName($0.jid) } ?? "Family call", isPresented: Binding(
            get: { quickCall != nil }, set: { if !$0 { quickCall = nil } }), titleVisibility: .visible) {
            if let member = quickCall {
                Button("Voice call") { quickCall = nil; store.startCall(member.jid, video: false) }.disabled(!canCall)
                Button("Video call") { quickCall = nil; store.startCall(member.jid, video: true) }.disabled(!canCall)
            }
        }
    }
    private var canCall: Bool { store.serviceReady && store.call == nil }
    private var contactHeader: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 20) {
                Button { inviting = true } label: {
                    VStack(spacing: 6) {
                        Image(systemName: "plus").font(.title2).frame(width: 58, height: 58)
                            .background(.white.opacity(0.12), in: Circle())
                        Text("Invite").font(.caption2)
                    }.foregroundStyle(.white)
                }.disabled(!store.serviceReady)
                ForEach(family) { member in
                    Button { quickCall = member } label: {
                        VStack(spacing: 6) {
                            FamilyContactAvatar(jid: member.jid, size: 58)
                            Text(familyName(member.jid)).font(.caption2).foregroundStyle(.white).lineLimit(1)
                        }.frame(width: 64)
                    }
                }
            }.padding(.horizontal, 24).padding(.top, 10).padding(.bottom, 24)
        }.buttonStyle(.plain)
    }
    private var contactList: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(store.serviceReady ? "Ready to connect" : "Connecting to your family…")
                .font(.caption.weight(.medium)).foregroundStyle(.secondary)
            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("Search family", text: $search).autocorrectionDisabled()
            }.padding(14).background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))
            Picker("Contacts", selection: $invitations) {
                Text("Family · \(family.count)").tag(false)
                Text("Invitations · \(requests.count)").tag(true)
            }.pickerStyle(.segmented)
            if store.serviceReady && shown.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: invitations ? "envelope.open" : "person.2.circle").font(.system(size: 42)).foregroundStyle(.tint)
                    Text(search.isEmpty ? (invitations ? "No pending invitations" : "Start your family circle") : "No matches").font(.headline)
                    if search.isEmpty && !invitations { Button("Invite a family member") { inviting = true } }
                }.frame(maxWidth: .infinity).padding(.vertical, 32)
            }
            LazyVStack(spacing: 0) {
                ForEach(shown) { member in
                    memberRow(member)
                    Divider().padding(.leading, 62)
                }
            }
        }
    }
    private func memberRow(_ member: FamilyMemberRow) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                FamilyContactAvatar(jid: member.jid, size: 50)
                VStack(alignment: .leading, spacing: 4) {
                    Text(familyName(member.jid)).font(.headline).lineLimit(1)
                    Text(member.status == "incoming" ? "Wants to join your family" : member.status == "outgoing" ? "Invitation sent" : member.jid)
                        .font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }.frame(maxWidth: .infinity, alignment: .leading)
                if member.status == "accepted" {
                    Button { store.startCall(member.jid, video: false) } label: { Image(systemName: "phone.fill").frame(width: 44, height: 44) }
                        .accessibilityLabel("Voice call \(familyName(member.jid))").disabled(!canCall)
                    Button { store.startCall(member.jid, video: true) } label: { Image(systemName: "video.fill").frame(width: 44, height: 44) }
                        .accessibilityLabel("Video call \(familyName(member.jid))").disabled(!canCall)
                }
                Menu {
                    Button(member.status == "accepted" ? "Remove member" : member.status == "incoming" ? "Decline invitation" : "Cancel invitation", role: .destructive) { removing = member }
                } label: { Image(systemName: "ellipsis").frame(width: 32, height: 44) }.accessibilityLabel("Options for \(familyName(member.jid))")
            }.buttonStyle(.plain)
            if member.status == "incoming" {
                HStack {
                    Button("Accept") { store.acceptMember(member.jid) }.buttonStyle(.borderedProminent).disabled(!store.serviceReady)
                    Button("Decline", role: .destructive) { removing = member }
                }.padding(.leading, 62)
            }
        }.padding(.vertical, 12)
    }
    private var loginFields: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Your people, closer").font(.title2.weight(.semibold))
            Text("Sign in to connect with your family through voice and video.").foregroundStyle(.secondary)
            TextField("Family account", text: $account).textInputAutocapitalization(.never)
                .autocorrectionDisabled().keyboardType(.emailAddress).textFieldStyle(.roundedBorder)
            SecureField("Password", text: $password).textFieldStyle(.roundedBorder)
            Button {
                store.login(account, password: password); password = ""
            } label: { Text(store.busy ? "Signing in…" : "Sign in").frame(maxWidth: .infinity).padding(.vertical, 6) }
                .buttonStyle(.borderedProminent).disabled(store.busy || account.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || password.isEmpty)
            #if DEBUG
            Text("Test credentials are prefilled in this debug build.").font(.caption).foregroundStyle(.secondary)
            #endif
        }
    }
    private var invitationSheet: some View {
        NavigationStack {
            Form {
                Section {
                    Text("They’ll need to accept before you can call each other.")
                    TextField("name@xmpp.starception.com", text: $invitation)
                        .textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.emailAddress)
                }
                Section {
                    Button("Send invitation") { store.invite(invitation); invitation = ""; inviting = false }
                        .disabled(!store.serviceReady || invitation.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }.navigationTitle("Invite to your family").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { inviting = false } } }
        }
    }
    private func suggestPeer() {
        #if DEBUG
        if invitation.isEmpty { invitation = FamilyEndpoint().testPeer(input: store.me) }
        #endif
    }
}

private func familyName(_ jid: String) -> String {
    let name = jid.components(separatedBy: "@")[0].replacingOccurrences(of: ".", with: " ")
        .replacingOccurrences(of: "_", with: " ").replacingOccurrences(of: "-", with: " ").capitalized
    return name.isEmpty ? "Family member" : name
}

private struct FamilyContactAvatar: View {
    let jid: String
    let size: CGFloat
    var body: some View {
        Text(familyName(jid).split(separator: " ").prefix(2).compactMap { $0.first }.map(String.init).joined())
            .font(.headline).foregroundStyle(.white).frame(width: size, height: size)
            .background(Color(red: 0.19, green: 0.35, blue: 0.38), in: Circle())
            .overlay(Circle().strokeBorder(Color(red: 0.66, green: 0.85, blue: 0.76), lineWidth: 2))
            .accessibilityHidden(true)
    }
}

struct FamilyCallPresentation: ViewModifier {
    var enabled = true
    @ObservedObject private var store = FamilyStore.shared
    @Environment(\.scenePhase) private var scenePhase
    func body(content: Content) -> some View {
        content.fullScreenCover(item: Binding(get: { enabled ? store.call : nil }, set: { _ in })) { _ in
            FamilyCallView().interactiveDismissDisabled()
        }
        .onChange(of: scenePhase) { phase in if phase == .background { store.backgrounded() } }
    }
}

private struct FamilyCallView: View {
    @ObservedObject private var store = FamilyStore.shared
    @ObservedObject private var media = FamilyStore.shared.media
    private var connectedAt: Date? { store.connectedAt }
    private let accent = Color(red: 0.57, green: 0.84, blue: 0.77)

    var body: some View {
        if let call = store.call {
            let incoming = call.stage == "ringing" && call.callee == store.me
            let peer = call.peer(store.me)
            let name = peer.components(separatedBy: "@")[0]
                .replacingOccurrences(of: ".", with: " ")
                .replacingOccurrences(of: "_", with: " ")
                .replacingOccurrences(of: "-", with: " ").capitalized
            VStack(spacing: 14) {
                Label(call.kind == "video" ? "FAMILY VIDEO CALL" : "FAMILY VOICE CALL",
                      systemImage: call.kind == "video" ? "video.fill" : "phone.fill")
                    .font(.caption.weight(.medium)).foregroundStyle(accent)
                Text(name).font((call.kind == "video" && call.stage == "active" ? Font.title2 : Font.largeTitle).weight(.semibold)).multilineTextAlignment(.center).lineLimit(2)
                Text(peer).font(.caption).foregroundStyle(.white.opacity(0.65)).lineLimit(1)
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    Text(status(incoming: incoming, stage: call.stage, now: context.date))
                        .font(.body.monospacedDigit()).foregroundStyle(accent)
                }
                Group {
                    if call.kind == "video" && call.stage == "active" {
                        Spacer(minLength: 0)
                    } else {
                        ZStack {
                            Circle().fill(.white.opacity(0.04)).frame(width: 176, height: 176)
                            Circle().fill(Color(red: 0.19, green: 0.35, blue: 0.38)).frame(width: 140, height: 140)
                            Text(name.split(separator: " ").prefix(2).compactMap { $0.first }.map(String.init).joined())
                                .font(.system(size: 48, weight: .medium)).foregroundStyle(accent)
                        }.frame(maxWidth: .infinity, maxHeight: .infinity).accessibilityHidden(true)
                    }
                }
                if let message = store.message {
                    Text(message).font(.caption).foregroundStyle(Color(red: 1, green: 0.71, blue: 0.67))
                        .multilineTextAlignment(.center)
                }
                if call.stage == "active" {
                    HStack(alignment: .top, spacing: 8) {
                        FamilyCallControl(symbol: store.muted ? "mic.slash.fill" : "mic.fill",
                                          title: store.muted ? "Unmute" : "Mute", selected: store.muted) { store.mute() }
                        FamilyCallControl(symbol: store.speakerEnabled ? "speaker.wave.2.fill" : "speaker.fill",
                                          title: "Speaker", selected: store.speakerEnabled) { store.speaker() }
                        if call.kind == "video" {
                            FamilyCallControl(symbol: store.cameraEnabled ? "video.fill" : "video.slash.fill",
                                              title: "Camera", selected: !store.cameraEnabled) { store.camera() }
                            FamilyCallControl(symbol: "arrow.triangle.2.circlepath.camera", title: "Flip") { store.switchCamera() }
                                .disabled(!store.cameraEnabled)
                        }
                    }
                }
                HStack(spacing: 40) {
                    FamilyCallControl(symbol: "phone.down.fill", title: incoming ? "Decline" : call.stage == "ringing" ? "Cancel" : "End call",
                                      color: Color(red: 0.85, green: 0.29, blue: 0.35)) { store.hangup() }
                    if incoming {
                        FamilyCallControl(symbol: call.kind == "video" ? "video.fill" : "phone.fill", title: "Answer",
                                          color: Color(red: 0.14, green: 0.52, blue: 0.42)) { store.answer() }
                    }
                }.padding(.top, 12).padding(.bottom, 8)
            }
            .padding(.horizontal, 24).padding(.vertical, 16)
            .foregroundStyle(.white)
            .background {
                if call.kind == "video" && call.stage == "active" {
                    GeometryReader { geometry in
                        VStack(spacing: 1) {
                            ZStack {
                                Color.black
                                FamilyVideoRenderer(track: media.remoteVideo, mirror: false)
                                if media.remoteVideo == nil { Text("Waiting for video…").foregroundStyle(.white.opacity(0.7)) }
                            }.frame(height: geometry.size.height / 2).clipped()
                            ZStack(alignment: .topLeading) {
                                Color(red: 0.13, green: 0.20, blue: 0.24)
                                if store.cameraEnabled { FamilyVideoRenderer(track: media.localVideo, mirror: true) }
                                else {
                                    Label("Your camera is off", systemImage: "video.slash.fill")
                                        .foregroundStyle(.white.opacity(0.7)).frame(maxWidth: .infinity, maxHeight: .infinity)
                                }
                                Text("You").font(.caption.weight(.medium)).foregroundStyle(.white)
                                    .padding(.horizontal, 10).padding(.vertical, 5)
                                    .background(.black.opacity(0.35), in: Capsule()).padding(16)
                            }.frame(height: geometry.size.height / 2).clipped()
                        }
                    }.ignoresSafeArea()
                    LinearGradient(colors: [.black.opacity(0.6), .clear, .black.opacity(0.8)],
                                   startPoint: .top, endPoint: .bottom).ignoresSafeArea()
                } else {
                    LinearGradient(colors: [Color(red: 0.10, green: 0.23, blue: 0.26), Color(red: 0.06, green: 0.11, blue: 0.15)],
                                   startPoint: .top, endPoint: .bottom).ignoresSafeArea()
                }
            }
            .preferredColorScheme(.dark)
        }
    }
    private func status(incoming: Bool, stage: String, now: Date) -> String {
        if incoming { return "Incoming \(store.call?.kind ?? "voice") call" }
        if stage == "ringing" { return "Calling…" }
        if store.mediaConnected, let start = connectedAt {
            let seconds = max(0, Int(now.timeIntervalSince(start)))
            return String(format: "%02d:%02d", seconds / 60, seconds % 60)
        }
        return connectedAt == nil ? "Connecting…" : "Reconnecting…"
    }
}

private struct FamilyCallControl: View {
    let symbol: String
    let title: String
    var selected = false
    var color: Color?
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: symbol).font(.system(size: 23, weight: .medium))
                    .frame(width: 56, height: 56)
                    .background(color ?? (selected ? Color(red: 0.84, green: 0.96, blue: 0.92) : .white.opacity(0.12)), in: Circle())
                    .foregroundStyle(selected && color == nil ? Color(red: 0.09, green: 0.24, blue: 0.21) : .white)
                Text(title).font(.caption).multilineTextAlignment(.center).foregroundStyle(.white.opacity(0.9))
            }.frame(maxWidth: .infinity)
        }.buttonStyle(.plain).accessibilityLabel(title)
            .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

private struct FamilyVideoRenderer: UIViewRepresentable {
    let track: RTCVideoTrack?
    let mirror: Bool
    final class Coordinator { var track: RTCVideoTrack? }
    func makeCoordinator() -> Coordinator { Coordinator() }
    func makeUIView(context: Context) -> RTCMTLVideoView {
        let view = RTCMTLVideoView(frame: .zero)
        view.videoContentMode = .scaleAspectFill
        return view
    }
    func updateUIView(_ view: RTCMTLVideoView, context: Context) {
        view.transform = mirror ? CGAffineTransform(scaleX: -1, y: 1) : .identity
        if context.coordinator.track !== track {
            context.coordinator.track?.remove(view)
            context.coordinator.track = track
            track?.add(view)
        }
    }
    static func dismantleUIView(_ view: RTCMTLVideoView, coordinator: Coordinator) {
        coordinator.track?.remove(view); coordinator.track = nil
    }
}
