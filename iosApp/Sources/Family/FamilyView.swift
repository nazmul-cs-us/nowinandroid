import SwiftUI
import Combine
import UIKit
import Shared
import WebRTC

private func familyPresenceText(_ member: FamilyMemberRow?, available: Bool) -> String {
    FamilyLastSeenPolicy().shortLabel(presence: member?.presence ?? "unknown", lastSeenAt: member?.lastSeenAt ?? 0,
        now: Int64(Date().timeIntervalSince1970 * 1000), available: available)
}

private func familyColor(_ argb: Int32) -> Color {
    let value = UInt32(bitPattern: argb)
    return Color(red: Double((value >> 16) & 255) / 255, green: Double((value >> 8) & 255) / 255,
                 blue: Double(value & 255) / 255)
}

private final class FamilyAppearance: ObservableObject {
    static let shared = FamilyAppearance()
    let objectWillChange = ObservableObjectPublisher()
    private var observer: NSObjectProtocol?
    private var cached: [Bool: FamilyColors] = [:]
    private init() {
        observer = NotificationCenter.default.addObserver(forName: UserDefaults.didChangeNotification, object: nil, queue: .main) { [weak self] _ in
            self?.cached.removeAll()
            self?.objectWillChange.send()
        }
    }
    func palette(dark: Bool) -> FamilyColors {
        if let value = cached[dark] { return value }
        let value = FamilyColors(dark: dark)
        cached[dark] = value
        return value
    }
}

private struct FamilyColors {
    private let raw: FamilyThemePalette
    init(dark: Bool) { raw = FamilyThemePalette(dark: dark) }
    var canvas: [Color] {
        raw.canvasCount == 3 ? [familyColor(raw.canvasTop), familyColor(raw.canvasMiddle), familyColor(raw.canvasBottom)] :
            [familyColor(raw.canvasTop), familyColor(raw.canvasMiddle), familyColor(raw.canvasLower), familyColor(raw.canvasBottom)]
    }
    var panel: Color { familyColor(raw.panel) }
    var field: Color { familyColor(raw.field) }
    var ink: Color { familyColor(raw.ink) }
    var muted: Color { familyColor(raw.muted) }
    var accent: Color { familyColor(raw.accent) }
    var header: Color { familyColor(raw.header) }
    var onHeader: Color { familyColor(raw.onHeader) }
    var brand: Color { familyColor(raw.brandHeader) }
    var onButton: Color { familyColor(raw.onButton) }
    var error: Color { familyColor(raw.error) }
    var onError: Color { familyColor(raw.onError) }
    var secondary: Color { familyColor(raw.secondaryContainer) }
    var onSecondary: Color { familyColor(raw.onSecondaryContainer) }
    var tertiary: Color { familyColor(raw.tertiaryContainer) }
    var onTertiary: Color { familyColor(raw.onTertiaryContainer) }
}

struct FamilyView: View {
    @ObservedObject private var store = FamilyStore.shared
    @ObservedObject private var appearance = FamilyAppearance.shared
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss
    #if DEBUG
    @State private var account = FamilyEndpoint().iosTestAccount
    @State private var password = "1234567812345678"
    #else
    @State private var account = ""
    @State private var password = ""
    #endif
    @State private var invitation = ""
    @State private var search = ""
    @State private var passwordVisible = false
    @State private var creatingAccount = false
    @State private var confirmation = ""
    @State private var directory = false
    @State private var settings = false
    @State private var invitations = false
    @State private var inviting = false
    @State private var inbox = false
    @State private var removing: FamilyMemberRow?
    @State private var selected: FamilyMemberRow?
    @State private var pendingProfileAction: (() -> Void)?
    private var colors: FamilyColors { appearance.palette(dark: colorScheme == .dark) }
    private var family: [FamilyMemberRow] {
        store.members.filter { $0.status == "accepted" }.sorted { a, b in
            let first = store.conversations.first { $0.peer == a.jid }?.lastMessage?.createdAt ?? 0
            let second = store.conversations.first { $0.peer == b.jid }?.lastMessage?.createdAt ?? 0
            return first == second ? familyName(a.jid) < familyName(b.jid) : first > second
        }
    }
    private var requests: [FamilyMemberRow] { store.members.filter { $0.status != "accepted" } }
    private var canCall: Bool { store.serviceReady && store.call == nil }
    private var shown: [FamilyMemberRow] {
        (invitations ? requests : family).filter {
            search.isEmpty || $0.jid.localizedCaseInsensitiveContains(search) || familyName($0.jid).localizedCaseInsensitiveContains(search)
        }
    }
    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 0) {
                if store.signedIn { contactHeader }
                else { FamilyLandingIllustration().frame(height: 184) }
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        if store.signedIn { contactList } else { loginFields }
                        if store.signedIn, let message = store.pushRegistrationMessage {
                            Text(message).font(.system(size: 12)).foregroundStyle(colors.muted).padding(.horizontal, 24).padding(.vertical, 12)
                        }
                        if !creatingAccount || store.signedIn, let message = store.message {
                            HStack(spacing: 10) {
                                Image(systemName: "info.circle").foregroundStyle(colors.accent)
                                Text(message).font(.system(size: 12)).foregroundStyle(colors.ink).frame(maxWidth: .infinity, alignment: .leading)
                                Button { store.message = nil } label: { Image(systemName: "xmark").frame(width: 36, height: 40) }
                                    .accessibilityLabel("Dismiss message").foregroundStyle(colors.muted)
                            }.padding(.leading, 14).background(colors.field, in: RoundedRectangle(cornerRadius: 16))
                                .padding(.horizontal, 24).padding(.vertical, 16)
                        }
                        Spacer(minLength: 32)
                    }
                    .padding(.top, store.signedIn ? 12 : 24).padding(.bottom, 100)
                    .frame(minHeight: max(0, geometry.size.height - (store.signedIn ? 228 : 184)), alignment: .top)
                    .frame(maxWidth: .infinity).background(store.signedIn ? colors.panel : colors.panel.opacity(0.78))
                    .clipShape(FamilyTopCorners(radius: 32, topRight: store.signedIn ? 0 : 32))
                }
            }.background {
                if store.signedIn { colors.brand.ignoresSafeArea() }
                else { LinearGradient(colors: colors.canvas, startPoint: .top, endPoint: .bottom).ignoresSafeArea() }
            }
            .overlay(alignment: .bottom) {
                if store.signedIn {
                    FamilyFeatureBar(directory: directory, unread: store.unreadCount,
                        contacts: { directory = true; invitations = false }, chats: { directory = false; invitations = false },
                        invite: { inviting = true }, alerts: { inbox = true; store.acknowledgeNotifications() }, settings: { settings = true })
                }
            }
        }
        .navigationTitle(store.signedIn ? "" : "Family").navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)

        .toolbar(store.signedIn ? .hidden : .visible, for: .navigationBar)
        .toolbar(store.signedIn ? .hidden : .visible, for: .tabBar)
        .sheet(item: Binding(get: { store.webPairing }, set: { _ in store.dismissWebHelper() })) { pairing in
            FamilyCallAlertsSetup(pairing: pairing)
        }
        .navigationDestination(isPresented: Binding(get: { store.chatPeer != nil }, set: { if !$0 { store.closeChat() } })) {
            FamilyChatView()
        }
        .sheet(isPresented: $inbox) { notificationInbox }
        .sheet(isPresented: $settings) { familySettings }
        .refreshable { store.refresh() }
        .onAppear { suggestPeer(); NativeAppearanceBridge.shared.setCallAppearance(lightIcons: store.signedIn || colorScheme == .dark) }
        .onDisappear { if store.call == nil { NativeAppearanceBridge.shared.setCallAppearance(lightIcons: nil) } }
        .onChange(of: store.me) { _ in suggestPeer() }
        .onChange(of: store.signedIn) { connected in
            NativeAppearanceBridge.shared.setCallAppearance(lightIcons: connected || colorScheme == .dark)
            if connected { password = ""; confirmation = ""; creatingAccount = false }
        }
        .sheet(isPresented: $inviting) { invitationSheet }
        .navigationDestination(isPresented: Binding(get: { selected != nil }, set: { if !$0 { selected = nil } })) {
            if let member = selected {
                contactProfile(member).toolbar(.hidden, for: .navigationBar)
                    .onDisappear { let action = pendingProfileAction; pendingProfileAction = nil; action?() }
            }
        }
        .confirmationDialog("Remove from family?", isPresented: Binding(
            get: { removing != nil }, set: { if !$0 { removing = nil } })) {
            if let member = removing { Button("Remove", role: .destructive) { store.removeMember(member.jid); removing = nil } }
        } message: { Text("Removing this member also deletes your shared chat history. You’ll need a new accepted invitation to connect again.") }
    }
    private var contactHeader: some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Text(directory ? "Contacts" : "Chats").font(.system(size: 24, weight: .medium)).frame(maxWidth: .infinity, alignment: .leading)
                Menu {
                    Button(requests.isEmpty ? "Invitations" : "Invitations · \(requests.count)") { invitations = true }
                    Button { store.requestWebHelper() } label: { Label("Temporary call alerts", systemImage: "bell.badge") }
                    Button { store.refresh() } label: { Label("Refresh family", systemImage: "arrow.clockwise") }
                    Button("Back to app") { dismiss() }
                    Button(role: .destructive) { store.disconnect() } label: { Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right") }
                } label: { Image(systemName: "ellipsis").rotationEffect(.degrees(90)).frame(width: 32, height: 44) }.accessibilityLabel("Family menu")
            }.foregroundStyle(.white).frame(height: 64).padding(.horizontal, 20).padding(.top, 24)
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass").foregroundStyle(.white.opacity(0.7))
                TextField("", text: $search, prompt: Text("Search your family").foregroundColor(.white.opacity(0.65)))
                    .font(.system(size: 14)).foregroundStyle(.white).tint(.white).autocorrectionDisabled().accessibilityLabel("Search family")
                if !search.isEmpty { Button { search = "" } label: { Image(systemName: "xmark").font(.system(size: 12)).foregroundStyle(.white).frame(width: 30, height: 30) }.accessibilityLabel("Close search") }
            }.padding(.horizontal, 14).frame(height: 44).background(.white.opacity(0.1), in: Capsule()).padding(.horizontal, 24).padding(.bottom, 18)
            if !family.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 14) {
                        ForEach(family) { member in
                            Button { selected = member } label: {
                                FamilyContactAvatar(jid: member.jid, size: 44, online: store.serviceReady && member.presence == "online")
                                    .accessibilityElement(children: .ignore).accessibilityLabel("Contact details for \(familyName(member.jid))")
                            }
                        }
                    }.padding(.horizontal, 20).padding(.bottom, 24)
                }.buttonStyle(.plain)
            }
        }.background(colors.brand)
    }
    private var contactList: some View {
        VStack(spacing: 0) {
            if invitations { HStack {
                Text("Invitations").font(.system(size: 16, weight: .semibold)).foregroundStyle(colors.ink)
                Spacer()
                Button(invitations ? "Back to chats" : requests.isEmpty ? "Invites" : "Invites · \(requests.count)") { invitations.toggle() }
                    .font(.system(size: 12)).foregroundStyle(colors.accent)
            }.frame(height: 44).padding(.horizontal, 24) }
            if !store.serviceReady {
                HStack(spacing: 10) { ProgressView().controlSize(.small); Text("Connecting your family…").font(.system(size: 13)).foregroundStyle(colors.muted) }
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 28).padding(.vertical, 16)
            }
            if store.serviceReady && shown.isEmpty { emptyState }
            LazyVStack(spacing: 0) {
                ForEach(shown) { member in
                    memberRow(member)
                    Rectangle().fill(colors.field).frame(height: 1).padding(.leading, 88).padding(.trailing, 24)
                }
            }
        }
    }

    private func memberRow(_ member: FamilyMemberRow) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 14) {
                Button { if member.status == "accepted" && !directory { store.openChat(member.jid) } else { selected = member } } label: {
                    HStack(spacing: 14) {
                        FamilyContactAvatar(jid: member.jid, size: 46, online: member.status == "accepted" && store.serviceReady && member.presence == "online")
                        VStack(alignment: .leading, spacing: 4) {
                            Text(familyName(member.jid)).font(.system(size: 15, weight: .medium)).foregroundStyle(colors.ink).lineLimit(1)
                            let presence = store.signedIn && store.serviceReady ? member.presence : "unknown"
                            HStack(spacing: 6) {
                                Text((directory ? nil : store.conversations.first(where: { $0.peer == member.jid })?.lastMessage?.preview(me: store.me)) ?? (member.status == "incoming" ? "Invited you to join" : member.status == "outgoing" ? "Waiting for acceptance" : familyPresenceText(member, available: store.serviceReady)))
                                    .font(.system(size: 12)).foregroundStyle(colors.muted).lineLimit(1)
                            }
                        }.frame(maxWidth: .infinity, alignment: .leading)
                    }.contentShape(Rectangle())
                }.buttonStyle(.plain)
                if !directory, let summary = store.conversations.first(where: { $0.peer == member.jid }) {
                    VStack(alignment: .trailing, spacing: 6) {
                        if let last = summary.lastMessage {
                            Text(Date(timeIntervalSince1970: Double(last.createdAt) / 1000), style: .time).font(.system(size: 10)).foregroundStyle(colors.muted)
                        }
                        if summary.unread > 0 {
                            Text("\(min(99, summary.unread))").font(.system(size: 10, weight: .semibold)).foregroundStyle(colors.onButton)
                                .padding(6).background(colors.accent, in: Capsule())
                        }
                    }
                }
                if directory && member.status == "accepted" {
                    Button { store.startCall(member.jid, video: false) } label: { Image(systemName: "phone").foregroundStyle(colors.accent).frame(width: 44, height: 44) }
                        .disabled(!canCall).accessibilityLabel("Call \(familyName(member.jid))")
                }
                if member.status != "accepted" {
                    Button { selected = member } label: { Image(systemName: "chevron.right").font(.system(size: 13)).foregroundStyle(colors.muted).frame(width: 32, height: 44) }
                        .accessibilityLabel("View invitation")
                }
            }.padding(.leading, 24).padding(.trailing, 24).padding(.vertical, 12)
            if member.status == "incoming" {
                HStack(spacing: 14) {
                    Button("Accept") { store.acceptMember(member.jid) }.font(.system(size: 12, weight: .semibold))
                        .buttonStyle(.borderedProminent).tint(colors.accent).disabled(!store.serviceReady)
                    Button("Decline") { removing = member }.font(.system(size: 12)).foregroundStyle(colors.muted)
                }.padding(.leading, 88).padding(.bottom, 14)
            }
        }
    }
    private var familySettings: some View {
        NavigationStack {
            List {
                Section("Family account") { Text(store.me).textSelection(.enabled) }
                Button("Invitations") { settings = false; invitations = true }
                Button("Temporary call alerts") { settings = false; store.requestWebHelper() }
                Button("Refresh family") { settings = false; store.refresh() }
                Button("Back to app") { settings = false; dismiss() }
                Button("Sign out", role: .destructive) { settings = false; store.disconnect() }
            }.navigationTitle("Family settings").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { settings = false } } }
        }.tint(colors.accent).presentationDetents([.medium, .large])
    }
    private var notificationInbox: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 0) {
                    if store.notifications.isEmpty { Text("You’re all caught up.").foregroundStyle(colors.muted).padding(32) }
                    ForEach(store.notifications) { notice in
                        Button {
                            inbox = false
                            if notice.kind == "invitation" { invitations = true }
                            else if notice.kind == "message" || notice.kind == "missed_call" { store.openChat(notice.peer) }
                            else { selected = store.members.first { $0.jid == notice.peer } }
                        } label: {
                            HStack(spacing: 12) {
                                FamilyContactAvatar(jid: notice.peer, size: 44)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(familyName(notice.peer)).font(.system(size: 15, weight: .medium)).foregroundStyle(colors.ink)
                                    Text(notice.kind == "invitation" ? "Invited you to join their family" : notice.kind == "missed_call" ? "Missed call" : "\(notice.count) unread messages")
                                        .font(.system(size: 12)).foregroundStyle(colors.muted)
                                }.frame(maxWidth: .infinity, alignment: .leading)
                                Text(Date(timeIntervalSince1970: Double(notice.createdAt) / 1000), style: .time).font(.system(size: 10)).foregroundStyle(colors.muted)
                            }.padding(.vertical, 16).padding(.horizontal, 24)
                        }.buttonStyle(.plain)
                    }
                }
            }.background(colors.panel).navigationTitle("Family notifications").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { inbox = false } } }
        }.tint(colors.accent)
    }
    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: invitations ? "envelope.open" : "person.2")
                .font(.system(size: 28)).foregroundStyle(colors.accent).frame(width: 72, height: 72).background(colors.field, in: Circle())
            Text(search.isEmpty ? (invitations ? "All caught up" : "Make room for your people") : "No matches yet")
                .font(.system(size: 18, weight: .semibold)).foregroundStyle(colors.ink)
            Text(search.isEmpty ? (invitations ? "New family invitations will appear here." : "Invite someone close. Once they accept, you can call each other anytime.") : "Try another name or account.")
                .font(.system(size: 13)).foregroundStyle(colors.muted).multilineTextAlignment(.center).lineSpacing(4)
            if search.isEmpty && !invitations { Button("Invite family") { inviting = true }.buttonStyle(.borderedProminent).tint(colors.accent) }
        }.padding(.horizontal, 32).padding(.vertical, 40)
    }
    private var loginFields: some View {
        VStack(alignment: .leading, spacing: 20) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Your family,\ncloser.").font(.system(size: 30, weight: .semibold)).foregroundStyle(colors.ink)
                Text("A private space for your mahram family. Share a message, hear a familiar voice, and stay close.")
                    .font(.system(size: 14)).foregroundStyle(colors.muted).lineSpacing(5)
            }
            VStack(spacing: 12) {
                accountField($account, title: creatingAccount ? "Choose a username" : "Family account", symbol: "person")
                if creatingAccount {
                    Text("3–32 letters, numbers, dots, underscores or hyphens. Your account will use @\(FamilyStore.domain).")
                        .font(.footnote).foregroundStyle(colors.muted)
                }
                HStack(spacing: 12) {
                    Image(systemName: "lock").foregroundStyle(colors.muted).frame(width: 20)
                    Group {
                        if passwordVisible { TextField("Password", text: $password) }
                        else { SecureField("Password", text: $password) }
                    }.font(.system(size: 14)).textContentType(creatingAccount ? .newPassword : .password)
                    Button { passwordVisible.toggle() } label: { Image(systemName: passwordVisible ? "eye.slash" : "eye").foregroundStyle(colors.muted).frame(width: 30, height: 30) }
                        .accessibilityLabel(passwordVisible ? "Hide password" : "Show password")
                }.padding(14).background(colors.field, in: RoundedRectangle(cornerRadius: 16))
                if creatingAccount {
                    Text("Use a password with 8–128 characters.").font(.footnote).foregroundStyle(colors.muted)
                    Group {
                        if passwordVisible { TextField("Confirm password", text: $confirmation) }
                        else { SecureField("Confirm password", text: $confirmation) }
                    }.textContentType(.newPassword).font(.system(size: 14)).padding(16)
                        .background(colors.field, in: RoundedRectangle(cornerRadius: 16))
                }
                if creatingAccount, let message = store.message {
                    Text(message).font(.footnote).foregroundStyle(colors.error)
                }
                Button {
                    if creatingAccount { store.signUp(account, password: password, confirmation: confirmation) }
                    else { store.login(account, password: password); password = "" }
                } label: {
                    Group { if store.busy { ProgressView().tint(colors.onButton) } else { Text(creatingAccount ? "Create account" : "Connect with family").font(.system(size: 15, weight: .semibold)) } }
                        .foregroundStyle(colors.onButton).frame(maxWidth: .infinity).frame(height: 54)
                        .background(colors.accent, in: RoundedRectangle(cornerRadius: 18))
                }.buttonStyle(.plain).disabled(store.busy || account.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || password.isEmpty)
                Button(creatingAccount ? "Already have an account? Sign in" : "New to Family? Create an account") {
                    creatingAccount.toggle(); account = ""; password = ""; confirmation = ""; store.message = nil
                }.font(.subheadline).foregroundStyle(colors.accent).disabled(store.busy)
            }
            Label("Only people you invite and accept", systemImage: "heart")
                .font(.system(size: 11)).foregroundStyle(colors.muted).frame(maxWidth: .infinity)
        }.padding(.horizontal, 28)
    }
    private func accountField(_ value: Binding<String>, title: String, symbol: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol).foregroundStyle(colors.muted).frame(width: 20)
            TextField(title, text: value).font(.system(size: 14)).foregroundStyle(colors.ink)
                .textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.emailAddress)
        }.padding(16).background(colors.field, in: RoundedRectangle(cornerRadius: 16))
    }
    private var invitationSheet: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 18) {
                Text("Invite a mahram family member. You can message and call after they accept.")
                    .font(.system(size: 14)).foregroundStyle(colors.muted).lineSpacing(4)
                accountField($invitation, title: "Family account", symbol: "person")
                Button { store.invite(invitation); invitation = ""; inviting = false; invitations = true } label: {
                    Text("Send invitation").font(.system(size: 15, weight: .semibold)).foregroundStyle(colors.onButton)
                        .frame(maxWidth: .infinity).frame(height: 54).background(colors.accent, in: RoundedRectangle(cornerRadius: 18))
                }.buttonStyle(.plain).disabled(!store.serviceReady || invitation.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                Spacer(minLength: 0)
            }.padding(24).background(colors.panel)
                .navigationTitle("Invite family").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button { inviting = false } label: { Image(systemName: "xmark").foregroundStyle(colors.muted) }.accessibilityLabel("Close invitation") } }
        }.presentationDetents([.height(380), .large]).presentationDragIndicator(.visible)
    }
    private func contactProfile(_ member: FamilyMemberRow) -> some View {
        GeometryReader { geometry in
        ScrollView {
            VStack(spacing: 0) {
                ZStack(alignment: .bottomLeading) {
                    LinearGradient(colors: [colors.brand, colors.accent.opacity(0.5), colors.brand], startPoint: .topLeading, endPoint: .bottomTrailing).background(colors.brand)
                    FamilyContactAvatar(jid: member.jid, size: 160).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center).padding(.bottom, 32)
                    VStack(alignment: .leading, spacing: 8) {
                        Text(familyName(member.jid)).font(.system(size: 24, weight: .semibold)).foregroundStyle(.white)
                        Text(member.status == "accepted" ? familyPresenceText(store.members.first(where: { $0.jid == member.jid }), available: store.signedIn && store.serviceReady) : "Family invitation")
                            .font(.system(size: 12)).foregroundStyle(.white.opacity(0.8))
                    }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading).padding(24)
                    Button { selected = nil } label: {
                        Image(systemName: "arrow.left").font(.system(size: 20)).foregroundStyle(.white).frame(width: 44, height: 44)
                    }.accessibilityLabel("Back to family").frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading).padding(.top, 48).padding(.leading, 12)
                }.frame(height: min(420, max(260, geometry.size.height * 0.52)))
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("FAMILY ACCOUNT").font(.system(size: 10, weight: .medium)).tracking(1.5).foregroundStyle(colors.muted)
                        Text(member.jid).font(.system(size: 15)).foregroundStyle(colors.ink).textSelection(.enabled)
                    }
                    if member.status == "accepted" {
                        HStack {
                            Text("Keep in touch").font(.system(size: 13)).foregroundStyle(colors.muted)
                            Spacer()
                            Button { pendingProfileAction = { store.startCall(member.jid, video: false) }; selected = nil } label: { Image(systemName: "phone").frame(width: 44, height: 44) }.accessibilityLabel("Voice call").disabled(!canCall)
                            Button { pendingProfileAction = { store.startCall(member.jid, video: true) }; selected = nil } label: { Image(systemName: "video").frame(width: 44, height: 44) }.accessibilityLabel("Video call").disabled(!canCall)
                        }.foregroundStyle(colors.accent)
                        Button { pendingProfileAction = { store.openChat(member.jid) }; selected = nil } label: {
                            Label("Message", systemImage: "bubble.left").font(.system(size: 15, weight: .medium))
                                .frame(maxWidth: .infinity).padding(16).foregroundStyle(.white).background(colors.brand, in: Capsule())
                        }.buttonStyle(.plain)
                    } else {
                        Text(member.status == "incoming" ? "Accept this invitation to start calling and messaging." : "Your invitation is waiting for acceptance.")
                            .font(.system(size: 14)).foregroundStyle(colors.muted)
                    }
                    if member.status == "incoming" { Button("Accept invitation") { store.acceptMember(member.jid); selected = nil }.buttonStyle(.borderedProminent).tint(colors.accent) }
                    Button { pendingProfileAction = { removing = member }; selected = nil } label: {
                        Text(member.status == "accepted" ? "Remove from family" : member.status == "incoming" ? "Decline invitation" : "Cancel invitation")
                            .font(.system(size: 13)).foregroundStyle(colors.muted).frame(maxWidth: .infinity)
                    }.padding(.vertical, 12)
                }.padding(24)
            }
        }.background(colors.panel).ignoresSafeArea(edges: .top)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                FamilyFeatureBar(directory: true, unread: store.unreadCount,
                    contacts: { selected = nil; directory = true },
                    chats: { pendingProfileAction = { store.openChat(member.jid) }; selected = nil },
                    invite: { pendingProfileAction = { inviting = true }; selected = nil },
                    alerts: { pendingProfileAction = { inbox = true; store.acknowledgeNotifications() }; selected = nil },
                    settings: { pendingProfileAction = { settings = true }; selected = nil })
            }
        }.onAppear { NativeAppearanceBridge.shared.setCallAppearance(lightIcons: true) }
    }

    private func suggestPeer() {
        #if DEBUG
        if invitation.isEmpty { invitation = FamilyEndpoint().testPeer(input: store.me) }
        #endif
    }
}

private struct FamilyTopCorners: Shape {
    let radius: CGFloat
    var topRight: CGFloat? = nil
    func path(in rect: CGRect) -> Path {
        let left = min(radius, min(rect.width, rect.height) / 2)
        let right = min(topRight ?? radius, min(rect.width, rect.height) / 2)
        var p = Path()
        p.move(to: CGPoint(x: 0, y: rect.height)); p.addLine(to: CGPoint(x: 0, y: left))
        p.addQuadCurve(to: CGPoint(x: left, y: 0), control: .zero)
        p.addLine(to: CGPoint(x: rect.width - right, y: 0))
        p.addQuadCurve(to: CGPoint(x: rect.width, y: right), control: CGPoint(x: rect.width, y: 0))
        p.addLine(to: CGPoint(x: rect.width, y: rect.height)); p.closeSubpath(); return p
    }
}

private struct FamilyLandingIllustration: View {
    var body: some View {
        Image("FamilyLanding").resizable().scaledToFit()
            .accessibilityLabel("Three family homes sheltered by an arch and an olive branch")
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
    var ring = false
    var online = false
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject private var appearance = FamilyAppearance.shared
    private var colors: FamilyColors { appearance.palette(dark: colorScheme == .dark) }
    private var shade: Color {
        [colors.header, colors.secondary, colors.tertiary, colors.field][jid.utf8.reduce(0) { ($0 * 31 + Int($1)) % 4 }]
    }
    var body: some View {
        Text(familyName(jid).split(separator: " ").prefix(2).compactMap { $0.first }.map(String.init).joined())
            .font(.system(size: size * 0.30, weight: .medium)).foregroundStyle(colors.ink)
            .frame(width: ring ? size - 8 : size, height: ring ? size - 8 : size).background(shade, in: Circle())
            .padding(ring ? 4 : 0).overlay(Circle().strokeBorder(ring ? colors.accent : .clear, lineWidth: 1.5))
            .accessibilityHidden(true)
            .overlay(alignment: .topTrailing) { if online { Circle().fill(colors.accent).frame(width: 9, height: 9).overlay(Circle().stroke(colors.panel, lineWidth: 2)) } }
    }
}

private struct FamilyCallAlertsSetup: View {
    let pairing: FamilyWebPairing
    @ObservedObject private var store = FamilyStore.shared
    @ObservedObject private var appearance = FamilyAppearance.shared
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.openURL) private var openURL
    @State private var copied = false
    private var colors: FamilyColors { appearance.palette(dark: colorScheme == .dark) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Image(systemName: "bell.badge").font(.system(size: 30)).foregroundStyle(colors.accent)
                Text("Call alerts on iPhone").font(.title2.weight(.semibold))
                Text("Get an alert when Deenly is closed. Tap the alert, then Open Deenly to answer before the call ends.")
                    .foregroundStyle(colors.muted)
                VStack(alignment: .leading, spacing: 12) {
                    Text("1. Copy your pairing code below.")
                    Text("2. Open setup in Safari. Choose Share → Add to Home Screen, then open the new Call Alerts icon.")
                    Text("3. Paste the code in Call Alerts, tap Enable call alerts, and allow notifications.")
                }.font(.subheadline)
                Text("Already installed? Copy the code, then open your Call Alerts Home Screen icon.")
                    .font(.footnote).foregroundStyle(colors.muted)
                TimelineView(.periodic(from: .now, by: 1)) { context in
                    let expired = context.date >= pairing.expires
                    VStack(alignment: .leading, spacing: 12) {
                        Button(copied ? "Code copied" : "Copy pairing code") {
                            UIPasteboard.general.string = pairing.code; copied = true
                        }.buttonStyle(.borderedProminent).foregroundStyle(colors.onButton).disabled(expired)
                        Button("Open Call Alerts setup") {
                            UIPasteboard.general.string = pairing.code; copied = true
                            openURL(pairing.url)
                        }.buttonStyle(.bordered).disabled(expired)
                        Text(expired ? "This code expired. Get a new code below." : "This code expires at \(pairing.expires.formatted(date: .omitted, time: .shortened)).")
                            .font(.footnote).foregroundStyle(colors.muted)
                    }
                }
                Button("Get a new code") { copied = false; store.requestWebHelper() }
                if let message = store.message {
                    Text(message).font(.footnote).foregroundStyle(colors.error)
                }
                Text("Requires iOS 16.4+. Alerts stay enabled for 24 hours; pair again to renew. An alert opens Deenly—it does not answer automatically.")
                    .font(.footnote).foregroundStyle(colors.muted)
                Button("Done") { store.dismissWebHelper() }
            }.padding(28).frame(maxWidth: .infinity, alignment: .leading)
        }.background(colors.panel).foregroundStyle(colors.ink).tint(colors.accent)
            .presentationDetents([.large])
            .onChange(of: pairing.id) { _ in copied = false }
    }
}

struct FamilyCallPresentation: ViewModifier {
    var enabled = true
    @ObservedObject private var store = FamilyStore.shared
    @Environment(\.scenePhase) private var scenePhase
    func body(content: Content) -> some View {
        content.fullScreenCover(item: Binding(get: { enabled ? store.call : nil }, set: { _ in })) { call in
            FamilyCallView(presentedCall: call).interactiveDismissDisabled()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .background { store.backgrounded() }
            if phase == .active { Task { await store.previewCamera() } }
        }
    }
}

private struct FamilyCallView: View {
    let presentedCall: FamilyCallInfo
    @State private var lastCall: FamilyCallInfo?
    @State private var videoPreviewPosition = CGPoint(x: 1, y: 0)
    @State private var videoControlsHeight: CGFloat = 116
    @ObservedObject private var store = FamilyStore.shared
    @ObservedObject private var media = FamilyStore.shared.media
    @ObservedObject private var appearance = FamilyAppearance.shared
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scenePhase) private var scenePhase
    private var colors: FamilyColors { appearance.palette(dark: colorScheme == .dark) }
    private var call: FamilyCallInfo {
        if let current = store.call, current.id == presentedCall.id { return current }
        return lastCall ?? presentedCall
    }
    private var video: Bool { call.kind == "video" && call.stage == "active" }
    private var preview: Bool { call.kind == "video" && call.stage == "ringing" && call.caller == store.me && media.localVideo != nil }
    private var immersive: Bool { video || preview }
    private var ink: Color { immersive ? .white : colors.ink }
    private var muted: Color { immersive ? .white.opacity(0.75) : colors.muted }
    private var lightStatusIcons: Bool { video && UIScreen.main.bounds.height >= 500 ? colorScheme == .dark : immersive || colorScheme == .dark }
    var body: some View {
        let call = self.call
        return Group {
            let incoming = call.stage == "ringing" && call.callee == store.me
            GeometryReader { geometry in
                let compact = geometry.size.height < 500
                if video && !compact { videoScene(call) }
                else {
                ZStack {
                    backdrop(call)
                    VStack(spacing: 0) {
                        HStack(spacing: 8) {
                            Image(systemName: "heart").font(.system(size: 15)).foregroundStyle(muted)
                            Text(video ? familyName(call.peer(store.me)) : "Family").font(.system(size: 14, weight: .medium)).foregroundStyle(ink).lineLimit(1)
                            Spacer()
                            if video { callStatus(incoming: incoming, stage: call.stage).padding(.horizontal, 12).padding(.vertical, 6).background(.black.opacity(0.25), in: Capsule()) }
                        }.frame(height: 40)
                        if call.stage == "active" {
                            Text(FamilyConnectionRoute().label(route: store.connectionRoute))
                                .font(.system(size: 11, weight: .medium)).foregroundStyle(muted)
                                .padding(.horizontal, 12).padding(.vertical, 6)
                                .background(immersive ? Color.black.opacity(0.25) : colors.field, in: Capsule())
                                .padding(.top, 4)
                        }
                        VStack(spacing: compact ? 8 : 24) {
                            if preview { Spacer().frame(height: 28) }
                            if video {
                                FamilyDraggableVideoPreview(position: $videoPreviewPosition) {
                                    selfPreview
                                }.padding(.bottom, 18)
                            }
                            if !video {
                                FamilyContactAvatar(jid: call.peer(store.me), size: preview ? 64 : compact ? 48 : 128)
                                VStack(spacing: 8) {
                                    Text(familyName(call.peer(store.me))).font(.system(size: preview ? 20 : compact ? 20 : 30, weight: .medium))
                                        .foregroundStyle(ink).multilineTextAlignment(.center).lineLimit(2)
                                    callStatus(incoming: incoming, stage: call.stage)
                                }
                            }
                        }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: preview ? .top : .center)
                        if let message = store.callMessage {
                            Text(message).font(.system(size: 12)).foregroundStyle(colors.error).multilineTextAlignment(.center).padding(.bottom, 12)
                        }
                        if call.stage == "ringing" && call.kind == "video" && call.caller == store.me {
                            FamilyCallControl(symbol: "arrow.triangle.2.circlepath.camera", title: "Flip", onVideo: preview) { store.switchCamera() }
                                .disabled(!preview).padding(.bottom, 24)
                        }
                        if call.stage == "active" {
                            controls(call, compact: compact).padding(.vertical, 16).padding(.horizontal, 4)
                                .background((video ? Color.white : colors.accent).opacity(video ? 0.13 : 0.055), in: RoundedRectangle(cornerRadius: 28))
                                .padding(.bottom, compact ? 0 : 24)
                        }
                        if (!compact && !video) || call.stage != "active" {
                            HStack {
                                if incoming {
                                    FamilyCallControl(symbol: call.kind == "video" ? "video.fill" : "phone.fill", title: "Accept",
                                                      color: colors.accent, large: true, onVideo: video) { store.answer() }
                                }
                                FamilyCallControl(symbol: "phone.down.fill", title: incoming ? "Decline" : call.stage == "ringing" ? "Cancel" : "End call",
                                                  color: colors.error, large: incoming, onVideo: immersive) { store.hangup() }
                            }
                        }
                    }.padding(.horizontal, 24).padding(.top, 12).padding(.bottom, 24)
                }
                }
            }
            .disabled(store.call?.id != presentedCall.id)
            .onChange(of: store.call?.version) { _ in
                if let current = store.call, current.id == presentedCall.id { lastCall = current }
            }
            .task(id: call.id) { await store.previewCamera() }
            .task(id: scenePhase) { if scenePhase == .active { await store.previewCamera() } }
            .onAppear { NativeAppearanceBridge.shared.setCallAppearance(lightIcons: lightStatusIcons) }
            .onChange(of: immersive) { _ in NativeAppearanceBridge.shared.setCallAppearance(lightIcons: lightStatusIcons) }
            .onChange(of: colorScheme) { _ in NativeAppearanceBridge.shared.setCallAppearance(lightIcons: lightStatusIcons) }
            .onDisappear {
                if store.call?.id == call.id && store.call?.stage == "ringing" { media.close() }
                NativeAppearanceBridge.shared.setCallAppearance(lightIcons: nil)
            }
        }
    }
    private func videoScene(_ call: FamilyCallInfo) -> some View {
        ZStack(alignment: .bottom) {
            LinearGradient(colors: colors.canvas, startPoint: .top, endPoint: .bottom).ignoresSafeArea()
            VStack(spacing: 0) {
                ZStack(alignment: .top) {
                    colors.field
                    FamilyVideoRenderer(track: media.remoteVideo, mirror: false)
                    if media.remoteVideo == nil {
                        VStack(spacing: 12) {
                            FamilyContactAvatar(jid: call.peer(store.me), size: 80)
                            Text("Waiting for video…").font(.system(size: 13)).foregroundStyle(colors.muted)
                        }.frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    LinearGradient(colors: [.black.opacity(0.55), .clear], startPoint: .top, endPoint: .bottom).frame(height: 160)
                    VStack(spacing: 6) {
                        Text(familyName(call.peer(store.me))).font(.system(size: 23, weight: .semibold)).foregroundStyle(.white)
                        callStatus(incoming: false, stage: call.stage)
                        Text(FamilyConnectionRoute().label(route: store.connectionRoute)).font(.system(size: 10)).foregroundStyle(.white.opacity(0.72))
                    }.padding(.top, 24)
                }.overlay {
                    FamilyDraggableVideoPreview(position: $videoPreviewPosition) { selfPreview }
                        .padding(EdgeInsets(top: 116, leading: 12, bottom: 12, trailing: 12))
                }.clipShape(RoundedRectangle(cornerRadius: 30))
                Spacer().frame(height: videoControlsHeight)
            }
            ZStack(alignment: .topTrailing) {
                VStack(spacing: 10) {
                    HStack {
                        videoButton(store.muted ? "mic.slash" : "mic", title: store.muted ? "Unmute" : "Mute", selected: store.muted) { store.mute() }
                        Spacer(minLength: 0)
                        videoButton(store.cameraEnabled ? "video" : "video.slash", title: "Camera", selected: !store.cameraEnabled) { store.camera() }
                        Spacer(minLength: 0)
                        videoButton("arrow.triangle.2.circlepath.camera", title: "Flip", enabled: store.cameraEnabled) { store.switchCamera() }
                        Spacer(minLength: 0)
                        videoButton("speaker.wave.2", title: "Speaker", selected: store.speakerEnabled) { store.speaker() }
                    }.padding(.trailing, 60)
                    if let message = store.callMessage { Text(message).font(.system(size: 11)).foregroundStyle(colors.error) }
                }.padding(12).background(colors.panel.opacity(0.65)).background(.ultraThinMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 26)).shadow(color: .black.opacity(0.1), radius: 8, y: 4).padding(.top, 28)
                Button { store.hangup() } label: {
                    Image(systemName: "phone.down.fill").font(.system(size: 23)).foregroundStyle(colors.onError)
                        .frame(width: 56, height: 56).background(colors.error, in: Circle())
                }.accessibilityLabel("End call").padding(.trailing, 10)
            }.padding(.horizontal, 12).padding(.bottom, 8)
                .background(GeometryReader { geometry in
                    Color.clear.preference(key: FamilyVideoControlsHeightKey.self, value: geometry.size.height)
                })
        }.padding(12)
            .onPreferenceChange(FamilyVideoControlsHeightKey.self) { height in
                if height > 0 { videoControlsHeight = height }
            }
    }
    private var selfPreview: some View {
        ZStack(alignment: .bottomLeading) {
            colors.field
            if store.cameraEnabled { FamilyVideoRenderer(track: media.localVideo, mirror: true) }
            else { Image(systemName: "video.slash").foregroundStyle(colors.muted).frame(maxWidth: .infinity, maxHeight: .infinity) }
            Text("You").font(.system(size: 10)).foregroundStyle(.white).padding(6)
                .background(.black.opacity(0.4), in: Capsule()).padding(8)
        }
    }
    private func videoButton(_ symbol: String, title: String, selected: Bool = false, enabled: Bool = true, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: 22)).foregroundStyle(colors.accent.opacity(enabled ? 1 : 0.4))
                .frame(width: 44, height: 44).background(selected ? colors.header : .clear, in: Circle())
        }.disabled(!enabled).accessibilityLabel(title).accessibilityAddTraits(selected ? .isSelected : [])
    }
    private func callStatus(incoming: Bool, stage: String) -> some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            Text(status(incoming: incoming, stage: stage, now: context.date))
                .font(.system(size: video ? 12 : 14).monospacedDigit()).foregroundStyle(muted)
        }
    }
    private func controls(_ call: FamilyCallInfo, compact: Bool) -> some View {
        HStack(alignment: .top, spacing: 4) {
            FamilyCallControl(symbol: store.muted ? "mic.slash.fill" : "mic.fill", title: store.muted ? "Unmute" : "Mute", selected: store.muted, onVideo: video) { store.mute() }
            FamilyCallControl(symbol: store.speakerEnabled ? "speaker.wave.2.fill" : "speaker.fill", title: "Speaker", selected: store.speakerEnabled, onVideo: video) { store.speaker() }
            if call.kind == "video" {
                FamilyCallControl(symbol: store.cameraEnabled ? "video.fill" : "video.slash.fill", title: "Camera", selected: !store.cameraEnabled, onVideo: video) { store.camera() }
                FamilyCallControl(symbol: "arrow.triangle.2.circlepath.camera", title: "Flip", onVideo: video) { store.switchCamera() }.disabled(!store.cameraEnabled)
            }
            if compact || video { FamilyCallControl(symbol: "phone.down.fill", title: "End call", color: colors.error, onVideo: video) { store.hangup() } }
        }
    }
    @ViewBuilder private func backdrop(_ call: FamilyCallInfo) -> some View {
        if preview {
            FamilyVideoRenderer(track: media.localVideo, mirror: true).ignoresSafeArea()
            LinearGradient(colors: [.black.opacity(0.5), .clear, .black.opacity(0.7)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
        } else if video {
            ZStack {
                colors.field
                FamilyVideoRenderer(track: media.remoteVideo, mirror: false)
                if media.remoteVideo == nil {
                    VStack(spacing: 10) {
                        FamilyContactAvatar(jid: call.peer(store.me), size: 72)
                        Text("Waiting for video…").font(.system(size: 12)).foregroundStyle(colors.muted)
                    }
                }
            }.clipShape(RoundedRectangle(cornerRadius: 28)).padding(8).ignoresSafeArea()
            LinearGradient(colors: [.black.opacity(0.55), .clear, .black.opacity(0.78)], startPoint: .top, endPoint: .bottom).ignoresSafeArea()
        } else {
            LinearGradient(colors: colors.canvas, startPoint: .top, endPoint: .bottom).ignoresSafeArea()
        }
    }
    private func status(incoming: Bool, stage: String, now: Date) -> String {
        if incoming { return "Incoming \(call.kind) call" }
        if stage == "ringing" { return "Calling…" }
        if store.mediaConnected, let start = store.connectedAt {
            let seconds = max(0, Int(now.timeIntervalSince(start)))
            return String(format: "%02d:%02d", seconds / 60, seconds % 60)
        }
        return store.connectedAt == nil ? "Connecting…" : "Reconnecting…"
    }
}

private struct FamilyVideoControlsHeightKey: PreferenceKey {
    static var defaultValue: CGFloat = 116
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

/** A stable renderer moves within its safe video region, independently of the call controls. */
private struct FamilyDraggableVideoPreview<Content: View>: View {
    @Binding var position: CGPoint
    @GestureState private var translation: CGSize = .zero
    @ViewBuilder var content: () -> Content

    var body: some View {
        GeometryReader { geometry in
            let width = min(104, max(0, geometry.size.width))
            let height = min(148, max(0, geometry.size.height))
            let travelX = max(0, geometry.size.width - width)
            let travelY = max(0, geometry.size.height - height)
            let x = min(travelX, max(0, position.x * travelX + translation.width))
            let y = min(travelY, max(0, position.y * travelY + translation.height))
            content().frame(width: width, height: height)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(.white.opacity(0.5), lineWidth: 1))
                .contentShape(Rectangle())
                .offset(x: x, y: y)
                .gesture(DragGesture(minimumDistance: 6)
                    .updating($translation) { value, current, _ in current = value.translation }
                    .onEnded { value in
                        if travelX > 0 {
                            let fraction = min(1, max(0, position.x + value.translation.width / travelX))
                            position.x = fraction < 0.5 ? 0 : 1
                        }
                        if travelY > 0 { position.y = min(1, max(0, position.y + value.translation.height / travelY)) }
                    })
                .accessibilityLabel("Your video preview")
                .accessibilityHint("Drag to move your preview")
                .accessibilityAction(named: Text("Move preview left")) { position.x = 0 }
                .accessibilityAction(named: Text("Move preview right")) { position.x = 1 }
                .accessibilityAction(named: Text("Move preview up")) { position.y = max(0, position.y - 0.25) }
                .accessibilityAction(named: Text("Move preview down")) { position.y = min(1, position.y + 0.25) }
        }
    }
}

private struct FamilyCallControl: View {
    let symbol: String
    let title: String
    var selected = false
    var color: Color?
    var large = false
    var onVideo = false
    let action: () -> Void
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject private var appearance = FamilyAppearance.shared
    private var colors: FamilyColors { appearance.palette(dark: colorScheme == .dark) }
    private var foreground: Color {
        if color == colors.error { return colors.onError }
        if color != nil { return colors.onButton }
        return onVideo ? .white : colors.ink
    }
    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: symbol).font(.system(size: large ? 30 : 23, weight: .medium))
                    .frame(width: large ? 72 : 52, height: large ? 72 : 52)
                    .background(color ?? (selected ? colors.header : onVideo ? .white.opacity(0.12) : colors.field), in: Circle())
                    .foregroundStyle(foreground)
                Text(title).font(.system(size: 11)).multilineTextAlignment(.center)
                    .foregroundStyle((onVideo ? Color.white : colors.ink).opacity(0.85))
            }.frame(maxWidth: .infinity)
        }.buttonStyle(.plain).accessibilityLabel(title).accessibilityAddTraits(selected ? .isSelected : [])
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

private struct FamilyChatView: View {
    @ObservedObject private var store = FamilyStore.shared
    @ObservedObject private var appearance = FamilyAppearance.shared
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scenePhase) private var scenePhase
    private var colors: FamilyColors { appearance.palette(dark: colorScheme == .dark) }
    @State private var details = false
    private var peer: String { store.chatPeer ?? "" }
    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 4) {
                        if store.chatLoading { ProgressView().padding(12) }
                        else if store.nextBefore > 0 { Button("Earlier messages") { store.loadMessages(older: true) }.font(.footnote) }
                        else if store.messages.isEmpty { Text("Say salaam to \(familyName(peer))").font(.footnote).foregroundStyle(colors.muted).padding(24) }
                        ForEach(Array(store.messages.enumerated()), id: \.element.id) { index, message in
                            messageRow(message, index: index).id(message.id)
                        }
                    }.padding(18)
                }.background(colors.panel)
                    .onChange(of: store.messages.last?.id) { id in
                        if let id { withAnimation { proxy.scrollTo(id, anchor: .bottom) } }
                        readVisible()
                    }
            }
            if let error = store.chatError {
                HStack {
                    Text(error).font(.system(size: 12)).foregroundStyle(colors.error)
                    Spacer()
                    Button("Refresh") { store.loadMessages() }.font(.footnote)
                }.padding(.horizontal, 18).padding(.vertical, 8).background(colors.panel)
            }
            HStack(alignment: .bottom, spacing: 10) {
                Menu {
                    Button("Voice call") { store.startCall(peer, video: false) }.disabled(!store.serviceReady || store.call != nil)
                    Button("Video call") { store.startCall(peer, video: true) }.disabled(!store.serviceReady || store.call != nil)
                    Button("Contact details") { details = true }
                } label: {
                    Image(systemName: "plus").font(.system(size: 16, weight: .medium)).foregroundStyle(colors.onTertiary)
                        .frame(width: 28, height: 28).background(colors.tertiary, in: Circle()).frame(width: 36, height: 40)
                }.accessibilityLabel("Conversation actions")
                TextField("Message", text: $store.chatDraft, axis: .vertical).lineLimit(1...5)
                    .font(.system(size: 15)).padding(14).background(colors.field, in: RoundedRectangle(cornerRadius: 24))
                Button { store.sendMessage() } label: {
                    Group {
                        if store.chatSending { ProgressView().tint(colors.onButton) }
                        else { Image(systemName: "paperplane").font(.system(size: 21, weight: .medium)) }
                    }.foregroundStyle(colors.accent).frame(width: 44, height: 48)
                }.disabled(!store.connected || store.chatSending || store.chatDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .accessibilityLabel("Send message")
            }.padding(16).background(colors.panel)
        }
        .background(colors.panel.ignoresSafeArea())
        .navigationTitle("").navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(colors.panel, for: .navigationBar)
        .toolbar(.hidden, for: .tabBar)
        .toolbar(.visible, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .principal) {
                HStack(spacing: 8) {
                    FamilyContactAvatar(jid: peer, size: 28)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(familyName(peer)).font(.system(size: 15, weight: .semibold)).foregroundStyle(colors.ink).lineLimit(1)
                        Text(familyPresenceText(store.members.first(where: { $0.jid == peer }), available: store.serviceReady))
                            .font(.system(size: 10)).foregroundStyle(colors.muted)
                    }
                }
            }
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Button { store.startCall(peer, video: false) } label: { Image(systemName: "phone") }.accessibilityLabel("Voice call")
                Button { store.startCall(peer, video: true) } label: { Image(systemName: "video") }.accessibilityLabel("Video call")
                Button { details = true } label: { Image(systemName: "info.circle") }.accessibilityLabel("Contact details")
            }
        }.tint(colors.accent)
        .alert(familyName(peer), isPresented: $details) { Button("Done", role: .cancel) {} } message: { Text(peer) }
        .onAppear { readVisible() }
        .onChange(of: scenePhase) { _ in readVisible() }
        .onChange(of: store.call?.id) { _ in readVisible() }
    }
    private func readVisible() { if scenePhase == .active && store.call == nil { store.markChatRead() } }
    private func messageRow(_ message: FamilyChatMessage, index: Int) -> some View {
        let previous = index > 0 ? store.messages[index - 1] : nil
        let date = Date(timeIntervalSince1970: Double(message.createdAt) / 1000)
        let newDay = previous == nil || !Calendar.current.isDate(date, inSameDayAs: Date(timeIntervalSince1970: Double(previous!.createdAt) / 1000))
        let newGroup = newDay || previous?.call != nil || message.call != nil || previous?.from != message.from || message.createdAt - (previous?.createdAt ?? 0) > 300000
        let mine = message.from == store.me
        return VStack(spacing: 4) {
            if newDay {
                Text(Calendar.current.isDateInToday(date) ? "Today" : Calendar.current.isDateInYesterday(date) ? "Yesterday" : date.formatted(date: .abbreviated, time: .omitted))
                    .font(.system(size: 10)).foregroundStyle(colors.muted).padding(.vertical, 20)
            }
            if let call = message.call {
                callHistoryRow(message, call: call, date: date)
            } else {
            HStack(alignment: .bottom, spacing: 7) {
                if mine { Spacer(minLength: 48) }
                else if newGroup { FamilyContactAvatar(jid: peer, size: 26).padding(.bottom, 14) }
                else { Color.clear.frame(width: 26, height: 1) }
                VStack(alignment: mine ? .trailing : .leading, spacing: 4) {
                    Text(message.text).font(.system(size: 14)).textSelection(.enabled).lineSpacing(3)
                        .padding(.horizontal, 13).padding(.vertical, 10)
                        .foregroundStyle(mine ? .white : colors.onHeader)
                        .background(mine ? colors.brand : colors.header, in: RoundedRectangle(cornerRadius: 18))
                    HStack(spacing: 4) {
                        Text(date, style: .time).font(.system(size: 9)).foregroundStyle(colors.muted)
                        if mine { Image(systemName: "checkmark").font(.system(size: 9)).foregroundStyle(colors.accent).accessibilityLabel("Saved to server") }
                    }.padding(.horizontal, 4)
                }.frame(maxWidth: 260, alignment: mine ? .trailing : .leading)
                if !mine { Spacer(minLength: 48) }
            }.padding(.top, newGroup && !newDay ? 12 : 0)
            }
        }.frame(maxWidth: .infinity)
    }
    private func callHistoryRow(_ message: FamilyChatMessage, call: FamilyChatCall, date: Date) -> some View {
        let policy = FamilyCallHistoryPolicy()
        let outgoing = message.from == store.me
        let missed = policy.missed(outcome: call.outcome, outgoing: outgoing)
        return HStack(spacing: 12) {
            Image(systemName: call.kind == "video" ? "video" : "phone")
                .foregroundStyle(missed ? colors.error : colors.accent)
                .frame(width: 44, height: 44).background(colors.header, in: Circle())
            VStack(alignment: .leading, spacing: 4) {
                Text(policy.title(kind: call.kind)).font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(missed ? colors.error : colors.ink)
                Text("\(policy.direction(outgoing: outgoing)) · \(policy.status(outcome: call.outcome, outgoing: outgoing))")
                    .font(.system(size: 12)).foregroundStyle(colors.muted)
                HStack(spacing: 4) {
                    Text(date, style: .time)
                    if let seconds = call.durationSeconds { Text("· \(policy.duration(seconds: seconds))") }
                }.font(.system(size: 11)).foregroundStyle(colors.muted)
            }
            Spacer(minLength: 0)
        }.padding(16).frame(maxWidth: 320, alignment: .leading)
            .background(colors.field, in: RoundedRectangle(cornerRadius: 16))
            .frame(maxWidth: .infinity).padding(.vertical, 10).accessibilityElement(children: .combine)
    }
}

private struct FamilyFeatureBar: View {
    let directory: Bool
    let unread: Int
    let contacts: () -> Void
    let chats: () -> Void
    let invite: () -> Void
    let alerts: () -> Void
    let settings: () -> Void
    @Environment(\.colorScheme) private var colorScheme
    @ObservedObject private var appearance = FamilyAppearance.shared
    private var colors: FamilyColors { appearance.palette(dark: colorScheme == .dark) }
    var body: some View {
        ZStack(alignment: .top) {
            HStack {
                barButton("person.2", title: "Family contacts", active: directory, action: contacts)
                Spacer(minLength: 0)
                barButton("bubble.left", title: "Family chats", active: !directory, action: chats)
                Spacer().frame(width: 80)
                ZStack(alignment: .topTrailing) {
                    barButton("bell", title: "Family notifications", active: false, action: alerts)
                    if unread > 0 { Text("\(min(unread, 99))").font(.system(size: 9, weight: .bold)).foregroundStyle(colors.onButton).padding(3).background(colors.accent, in: Capsule()) }
                }
                Spacer(minLength: 0)
                barButton("gearshape", title: "Family settings", active: false, action: settings)
            }.padding(.horizontal, 24).padding(.top, 24).frame(height: 92)
            Button(action: invite) {
                Image(systemName: "plus").font(.system(size: 26, weight: .medium)).foregroundStyle(colors.onTertiary)
                    .frame(width: 64, height: 64).background(colors.tertiary, in: Circle())
            }.accessibilityLabel("Invite a member")
        }.frame(maxWidth: .infinity).background(colors.panel.ignoresSafeArea(edges: .bottom))
    }
    private func barButton(_ symbol: String, title: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: symbol).font(.system(size: 22)).foregroundStyle(colors.accent.opacity(active ? 1 : 0.4)).frame(width: 44, height: 44) }
            .accessibilityLabel(title)
    }
}
