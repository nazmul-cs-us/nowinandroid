import AudioToolbox
import SwiftUI
import Shared
import WebRTC

struct FamilyView: View {
    @ObservedObject private var store = FamilyStore.shared
    @State private var account = ""
    @State private var password = ""
    @State private var invitation = ""
    @State private var removing: FamilyMemberRow?

    var body: some View {
        List {
            Section {
                Text("Stay close to your family").font(.title2.bold())
                Text("Invite someone to your family. Voice and video calls become available after they accept.")
                    .foregroundStyle(.secondary)
            }
            if !store.connected {
                Section("Family account") {
                    TextField("name@xmpp.starception.com", text: $account)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                        .keyboardType(.emailAddress)
                    SecureField("Password", text: $password)
                    Button(store.busy ? "Signing in…" : "Sign in") {
                        store.login(account, password: password)
                        password = ""
                    }.disabled(store.busy)
                }
            } else {
                Section {
                    Text(store.me).font(.caption).foregroundStyle(.secondary)
                    TextField("Family member’s account", text: $invitation)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                        .keyboardType(.emailAddress)
                    Button("Send invitation") { store.invite(invitation); invitation = "" }
                        .disabled(invitation.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                Section("Your family") {
                    if store.members.isEmpty { Text("Your family list is empty. Send an invitation to get started.").foregroundStyle(.secondary) }
                    ForEach(store.members) { member in
                        VStack(alignment: .leading, spacing: 10) {
                            Text(member.jid).font(.headline)
                            Text(status(member.status)).font(.subheadline).foregroundStyle(.secondary)
                            HStack(spacing: 18) {
                                if FamilyPolicy().canCall(relationship: member.status) {
                                    Button { store.startCall(member.jid, video: false) } label: { Label("Voice", systemImage: "phone.fill") }
                                        .disabled(store.call != nil)
                                    Button { store.startCall(member.jid, video: true) } label: { Label("Video", systemImage: "video.fill") }
                                        .disabled(store.call != nil)
                                }
                                if member.status == "incoming" { Button("Accept") { store.acceptMember(member.jid) } }
                                Button(member.status == "incoming" ? "Decline" : member.status == "outgoing" ? "Cancel" : "Remove", role: .destructive) {
                                    removing = member
                                }
                            }.buttonStyle(.borderless)
                        }.padding(.vertical, 6)
                    }
                }
                Section { Button("Sign out", role: .destructive) { store.disconnect() } }
            }
            if let message = store.message {
                Section { Text(message).foregroundStyle(.red); Button("Dismiss") { store.message = nil } }
            }
        }
        .navigationTitle("Family")
        .toolbar { if store.connected { Button { store.refresh() } label: { Image(systemName: "arrow.clockwise") }.accessibilityLabel("Refresh family") } }
        .refreshable { store.refresh() }
        .confirmationDialog("Remove this family relationship?", isPresented: Binding(
            get: { removing != nil }, set: { if !$0 { removing = nil } })) {
            if let member = removing { Button("Remove", role: .destructive) { store.removeMember(member.jid); removing = nil } }
        } message: { Text("Calling access ends when the relationship is removed.") }
    }
    private func status(_ value: String) -> String {
        switch value { case "incoming": return "Invited you to join their family"
        case "outgoing": return "Waiting for acceptance"; default: return "Family member" }
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
    var body: some View {
        if let call = store.call {
            let incoming = call.stage == "ringing" && call.callee == store.me
            VStack(spacing: 18) {
                Text(call.peer(store.me)).font(.title2).multilineTextAlignment(.center)
                Text(incoming ? "Incoming \(call.kind) call" : call.stage == "ringing" ? "Calling…" : store.mediaConnected ? "Connected" : "Connecting…")
                    .foregroundStyle(.secondary)
                ZStack(alignment: .topTrailing) {
                    if call.kind == "video" && call.stage == "active" {
                        FamilyVideoRenderer(track: media.remoteVideo, mirror: false)
                            .background(.black).clipShape(RoundedRectangle(cornerRadius: 20))
                        FamilyVideoRenderer(track: media.localVideo, mirror: true)
                            .frame(width: 110, height: 150).background(.black)
                            .clipShape(RoundedRectangle(cornerRadius: 12)).padding(10)
                    } else {
                        Image(systemName: "person.2.fill").resizable().scaledToFit()
                            .frame(maxWidth: 140).frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }.frame(maxHeight: .infinity)
                if incoming { Button("Answer") { store.answer() }.buttonStyle(.borderedProminent).tint(.green) }
                if call.stage == "active" {
                    HStack {
                        Button(store.muted ? "Unmute" : "Mute") { store.mute() }
                        Button(store.speakerEnabled ? "Earpiece" : "Speaker") { store.speaker() }
                    }.buttonStyle(.bordered)
                    if call.kind == "video" {
                        HStack {
                            Button(store.cameraEnabled ? "Camera off" : "Camera on") { store.camera() }
                            Button("Switch camera") { store.switchCamera() }
                        }.buttonStyle(.bordered)
                    }
                }
                Button(incoming ? "Decline" : call.stage == "ringing" ? "Cancel" : "End call") { store.hangup() }
                    .buttonStyle(.borderedProminent).tint(.red)
            }
            .padding(24)
            .onAppear { if incoming { AudioServicesPlayAlertSound(1005) } }
        }
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
