import SwiftUI
import Combine
import UIKit
import Shared
import WebRTC

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
    #if DEBUG
    @State private var account = FamilyEndpoint().iosTestAccount
    @State private var password = "1234567812345678"
    #else
    @State private var account = ""
    @State private var password = ""
    #endif
    @State private var invitation = ""
    @State private var search = ""
    @State private var searching = false
    @State private var passwordVisible = false
    @State private var creatingAccount = false
    @State private var confirmation = ""
    @State private var invitations = false
    @State private var inviting = false
    @State private var inbox = false
    @State private var removing: FamilyMemberRow?
    @State private var selected: FamilyMemberRow?
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
            ScrollView {
                VStack(spacing: 0) {
                    if store.connected { contactHeader.frame(height: 128) }
                    else { FamilyLandingIllustration().frame(height: 184) }
                    VStack(alignment: .leading, spacing: 0) {
                        if store.connected { contactList } else { loginFields }
                        if store.connected, let message = store.pushRegistrationMessage {
                            Text(message).font(.system(size: 12)).foregroundStyle(colors.muted).padding(.horizontal, 24).padding(.vertical, 12)
                        }
                        if !creatingAccount || store.connected, let message = store.message {
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
                    .padding(.top, 24).padding(.bottom, 24)
                    .frame(minHeight: max(0, geometry.size.height - (store.connected ? 128 : 184)), alignment: .top)
                    .frame(maxWidth: .infinity).background(colors.panel.opacity(0.78))
                    .clipShape(FamilyTopCorners(radius: 30))
                }
            }.background(LinearGradient(colors: colors.canvas, startPoint: .top, endPoint: .bottom).ignoresSafeArea())
        }
        .navigationTitle("Family").navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)

        .toolbar {
            if store.connected {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button { inbox = true; store.acknowledgeNotifications() } label: {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "bell")
                            if store.unreadCount > 0 {
                                Text("\(min(99, store.unreadCount))").font(.system(size: 9, weight: .bold)).foregroundStyle(colors.onButton)
                                    .padding(3).background(colors.accent, in: Capsule()).offset(x: 9, y: -8)
                            }
                        }
                    }.accessibilityLabel("Family notifications, \(store.unreadCount) unread")
                    Button { searching.toggle(); if !searching { search = "" } } label: { Image(systemName: "magnifyingglass") }
                        .accessibilityLabel("Search family")
                    Menu {
                        Button { store.requestWebHelper() } label: { Label("Temporary call alerts", systemImage: "bell.badge") }
                        Button { store.refresh() } label: { Label("Refresh family", systemImage: "arrow.clockwise") }
                        Button(role: .destructive) { store.disconnect() } label: { Label("Sign out", systemImage: "rectangle.portrait.and.arrow.right") }
                    } label: { Image(systemName: "ellipsis") }.accessibilityLabel("Family settings")
                }
            }
        }
        .sheet(item: Binding(get: { store.webPairing }, set: { _ in store.dismissWebHelper() })) { pairing in
            FamilyCallAlertsSetup(pairing: pairing)
        }
        .navigationDestination(isPresented: Binding(get: { store.chatPeer != nil }, set: { if !$0 { store.closeChat() } })) {
            FamilyChatView()
        }
        .sheet(isPresented: $inbox) { notificationInbox }
        .refreshable { store.refresh() }
        .onAppear { suggestPeer() }
        .onChange(of: store.me) { _ in suggestPeer() }
        .onChange(of: store.connected) { connected in
            if connected { password = ""; confirmation = ""; creatingAccount = false }
        }
        .sheet(isPresented: $inviting) { invitationSheet }
        .sheet(item: $selected) { member in contactSheet(member) }
        .confirmationDialog("Remove from family?", isPresented: Binding(
            get: { removing != nil }, set: { if !$0 { removing = nil } })) {
            if let member = removing { Button("Remove", role: .destructive) { store.removeMember(member.jid); removing = nil } }
        } message: { Text("Removing this member also deletes your shared chat history. You’ll need a new accepted invitation to connect again.") }
    }
    private var contactHeader: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(alignment: .top, spacing: 20) {
                Button { inviting = true } label: {
                    VStack(spacing: 8) {
                        Image(systemName: "plus").font(.system(size: 23, weight: .light)).frame(width: 60, height: 60)
                            .overlay(Circle().strokeBorder(colors.ink.opacity(0.5), lineWidth: 1))
                        Text("Invite").font(.system(size: 11))
                    }.foregroundStyle(colors.ink.opacity(0.9))
                }.disabled(!store.serviceReady)
                ForEach(family) { member in
                    Button { selected = member } label: {
                        VStack(spacing: 8) {
                            FamilyContactAvatar(jid: member.jid, size: 60, ring: true)
                            Text(familyName(member.jid).components(separatedBy: " ")[0]).font(.system(size: 11)).foregroundStyle(colors.ink.opacity(0.9)).lineLimit(1)
                        }.frame(width: 60)
                    }
                }
            }.padding(.horizontal, 24).padding(.top, 8).padding(.bottom, 24)
        }.buttonStyle(.plain)
    }
    private var contactList: some View {
        VStack(spacing: 0) {
            HStack(spacing: 4) {
                segment("Family", active: !invitations) { invitations = false }
                segment("Invites", active: invitations, count: requests.count) { invitations = true }
            }.padding(4).frame(height: 44).background(colors.field, in: Capsule()).padding(.horizontal, 24).padding(.bottom, 16)
            if searching {
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(colors.muted)
                    TextField("Search your family", text: $search).autocorrectionDisabled().font(.system(size: 14))
                    Button { search = ""; searching = false } label: { Image(systemName: "xmark").frame(width: 28, height: 28) }.accessibilityLabel("Close search")
                }.padding(12).background(colors.field, in: RoundedRectangle(cornerRadius: 16)).padding(.horizontal, 24).padding(.bottom, 16)
            }
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
    private func segment(_ title: String, active: Bool, count: Int = 0, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 7) {
                Text(title).font(.system(size: 13, weight: active ? .semibold : .regular))
                if count > 0 {
                    Text("\(count)").font(.system(size: 10, weight: .semibold)).padding(.horizontal, 6).padding(.vertical, 2)
                        .background(colors.accent.opacity(0.12), in: Capsule()).foregroundStyle(colors.accent)
                }
            }.foregroundStyle(active ? colors.ink : colors.muted).frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(active ? colors.panel : .clear, in: Capsule())
                .shadow(color: .black.opacity(active ? 0.04 : 0), radius: 2, y: 1)
        }.buttonStyle(.plain).accessibilityAddTraits(active ? .isSelected : [])
    }
    private func memberRow(_ member: FamilyMemberRow) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 14) {
                Button { if member.status == "accepted" { store.openChat(member.jid) } else { selected = member } } label: {
                    HStack(spacing: 14) {
                        FamilyContactAvatar(jid: member.jid, size: 50)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(familyName(member.jid)).font(.system(size: 16, weight: .medium)).foregroundStyle(colors.ink).lineLimit(1)
                            let presence = store.connected && store.serviceReady ? member.presence : "unknown"
                            HStack(spacing: 6) {
                                if member.status == "accepted" && presence == "online" {
                                    Circle().fill(Color(red: 46 / 255, green: 157 / 255, blue: 101 / 255)).frame(width: 6, height: 6).accessibilityHidden(true)
                                }
                                Text(store.conversations.first(where: { $0.peer == member.jid })?.lastMessage?.text ?? (member.status == "incoming" ? "Invited you to join" : member.status == "outgoing" ? "Waiting for acceptance" : presence == "online" ? "Online" : presence == "offline" ? "Offline" : "Status unavailable"))
                                    .font(.system(size: 12)).foregroundStyle(colors.muted).lineLimit(1)
                            }
                        }.frame(maxWidth: .infinity, alignment: .leading)
                    }.contentShape(Rectangle())
                }.buttonStyle(.plain)
                if let summary = store.conversations.first(where: { $0.peer == member.jid }) {
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
                if member.status == "accepted" {
                    Button { store.startCall(member.jid, video: false) } label: {
                        Image(systemName: "phone.fill").font(.system(size: 17)).foregroundStyle(colors.accent)
                            .frame(width: 44, height: 44).background(colors.field, in: Circle())
                    }.buttonStyle(.plain).disabled(!canCall).accessibilityLabel("Call \(familyName(member.jid))")
                } else {
                    Button { selected = member } label: { Image(systemName: "chevron.right").font(.system(size: 13)).foregroundStyle(colors.muted).frame(width: 32, height: 44) }
                        .accessibilityLabel("View invitation")
                }
            }.padding(.leading, 24).padding(.trailing, 20).padding(.vertical, 14)
            if member.status == "incoming" {
                HStack(spacing: 14) {
                    Button("Accept") { store.acceptMember(member.jid) }.font(.system(size: 12, weight: .semibold))
                        .buttonStyle(.borderedProminent).tint(colors.accent).disabled(!store.serviceReady)
                    Button("Decline") { removing = member }.font(.system(size: 12)).foregroundStyle(colors.muted)
                }.padding(.leading, 88).padding(.bottom, 14)
            }
        }
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
                            else if notice.kind == "message" { store.openChat(notice.peer) }
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
                    Text("Use a password with 12–128 characters.").font(.footnote).foregroundStyle(colors.muted)
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
                Image(systemName: "person.badge.plus").font(.title2).foregroundStyle(colors.accent)
                    .frame(width: 48, height: 48).background(colors.field, in: RoundedRectangle(cornerRadius: 16))
                Text("Bring your people closer").font(.system(size: 24, weight: .semibold)).foregroundStyle(colors.ink)
                Text("Send an invitation. Calls become available when they accept.").font(.system(size: 14)).foregroundStyle(colors.muted)
                accountField($invitation, title: "Family account", symbol: "person")
                Button { store.invite(invitation); invitation = ""; inviting = false; invitations = true } label: {
                    Text("Send invitation").font(.system(size: 15, weight: .semibold)).foregroundStyle(colors.onButton)
                        .frame(maxWidth: .infinity).frame(height: 54).background(colors.accent, in: RoundedRectangle(cornerRadius: 18))
                }.buttonStyle(.plain).disabled(!store.serviceReady || invitation.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                Spacer(minLength: 0)
            }.padding(28).background(colors.panel)
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { inviting = false }.tint(colors.accent) } }
        }.presentationDetents([.medium, .large]).presentationDragIndicator(.visible)
    }
    private func contactSheet(_ member: FamilyMemberRow) -> some View {
        VStack(spacing: 14) {
            FamilyContactAvatar(jid: member.jid, size: 80)
            Text(familyName(member.jid)).font(.system(size: 24, weight: .semibold)).foregroundStyle(colors.ink)
            Text(member.jid).font(.system(size: 12)).foregroundStyle(colors.muted)
            if member.status == "accepted" {
                Button("Message") { selected = nil; store.openChat(member.jid) }.buttonStyle(.borderedProminent).tint(colors.accent)
                HStack(spacing: 12) {
                    contactAction("Voice call", symbol: "phone.fill") { selected = nil; store.startCall(member.jid, video: false) }
                    contactAction("Video call", symbol: "video.fill") { selected = nil; store.startCall(member.jid, video: true) }
                }.padding(.vertical, 12)
            } else {
                Text(member.status == "incoming" ? "Accept this invitation to start calling." : "Your invitation is waiting for acceptance.")
                    .font(.system(size: 14)).foregroundStyle(colors.muted).multilineTextAlignment(.center)
            }
            if member.status == "incoming" { Button("Accept invitation") { store.acceptMember(member.jid); selected = nil }.buttonStyle(.borderedProminent).tint(colors.accent) }
            Button { selected = nil; removing = member } label: {
                Text(member.status == "accepted" ? "Remove from family" : member.status == "incoming" ? "Decline invitation" : "Cancel invitation")
                    .font(.system(size: 13)).foregroundStyle(colors.muted)
            }.padding(.top, 6)
        }.padding(28).frame(maxWidth: .infinity).background(colors.panel)
            .presentationDetents([.medium]).presentationDragIndicator(.visible)
    }
    private func contactAction(_ title: String, symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 8) { Image(systemName: symbol).font(.system(size: 23)).foregroundStyle(colors.accent); Text(title).font(.system(size: 13, weight: .medium)).foregroundStyle(colors.ink) }
                .frame(maxWidth: .infinity).padding(.vertical, 20).background(colors.field, in: RoundedRectangle(cornerRadius: 20))
        }.buttonStyle(.plain).disabled(!canCall)
    }
    private func suggestPeer() {
        #if DEBUG
        if invitation.isEmpty { invitation = FamilyEndpoint().testPeer(input: store.me) }
        #endif
    }
}

private struct FamilyTopCorners: Shape {
    let radius: CGFloat
    func path(in rect: CGRect) -> Path {
        Path(UIBezierPath(roundedRect: rect, byRoundingCorners: [.topLeft, .topRight], cornerRadii: CGSize(width: radius, height: radius)).cgPath)
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
    var body: some View {
        let call = self.call
        return Group {
            let incoming = call.stage == "ringing" && call.callee == store.me
            GeometryReader { geometry in
                let compact = geometry.size.height < 500
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
                                VStack {
                                    Spacer()
                                    HStack {
                                        Spacer()
                                        ZStack(alignment: .bottomLeading) {
                                            colors.field
                                            if store.cameraEnabled { FamilyVideoRenderer(track: media.localVideo, mirror: true) }
                                            else { Image(systemName: "video.slash").foregroundStyle(colors.muted).frame(maxWidth: .infinity, maxHeight: .infinity) }
                                            Text("You").font(.system(size: 10)).foregroundStyle(.white).padding(6)
                                                .background(.black.opacity(0.4), in: Capsule()).padding(8)
                                        }.frame(width: 104, height: 148).clipShape(RoundedRectangle(cornerRadius: 22))
                                    }
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
            .disabled(store.call?.id != presentedCall.id)
            .onChange(of: store.call?.version) { _ in
                if let current = store.call, current.id == presentedCall.id { lastCall = current }
            }
            .task(id: call.id) { await store.previewCamera() }
            .task(id: scenePhase) { if scenePhase == .active { await store.previewCamera() } }
            .onAppear { NativeAppearanceBridge.shared.setCallAppearance(lightIcons: immersive || colorScheme == .dark) }
            .onChange(of: immersive) { _ in NativeAppearanceBridge.shared.setCallAppearance(lightIcons: immersive || colorScheme == .dark) }
            .onChange(of: colorScheme) { _ in NativeAppearanceBridge.shared.setCallAppearance(lightIcons: immersive || colorScheme == .dark) }
            .onDisappear {
                if store.call?.id == call.id && store.call?.stage == "ringing" { media.close() }
                NativeAppearanceBridge.shared.setCallAppearance(lightIcons: nil)
            }
        }
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
    private var peer: String { store.chatPeer ?? "" }
    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 10) {
                        Text("Your private family conversation").font(.system(size: 11)).foregroundStyle(colors.muted).padding(12)
                        if store.chatLoading { ProgressView().padding(12) }
                        else if store.nextBefore > 0 { Button("Earlier messages") { store.loadMessages(older: true) }.font(.footnote) }
                        else if store.messages.isEmpty { Text("Say salaam to \(familyName(peer))").font(.footnote).foregroundStyle(colors.muted).padding(24) }
                        ForEach(store.messages) { message in
                            messageBubble(message).id(message.id)
                        }
                    }.padding(18)
                }.background(colors.panel.opacity(0.84), in: FamilyTopCorners(radius: 30))
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
                TextField("Message", text: $store.chatDraft, axis: .vertical).lineLimit(1...5)
                    .font(.system(size: 15)).padding(14).background(colors.field, in: RoundedRectangle(cornerRadius: 24))
                Button { store.sendMessage() } label: {
                    Group {
                        if store.chatSending { ProgressView().tint(colors.onButton) }
                        else { Image(systemName: "arrow.up").font(.system(size: 20, weight: .semibold)) }
                    }.foregroundStyle(colors.onButton).frame(width: 48, height: 48).background(colors.accent, in: Circle())
                }.disabled(!store.connected || store.chatSending || store.chatDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .accessibilityLabel("Send message")
            }.padding(16).background(colors.panel)
        }
        .background(LinearGradient(colors: colors.canvas, startPoint: .top, endPoint: .bottom).ignoresSafeArea())
        .navigationTitle(familyName(peer)).navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Button { store.startCall(peer, video: false) } label: { Image(systemName: "phone") }.accessibilityLabel("Voice call")
                Button { store.startCall(peer, video: true) } label: { Image(systemName: "video") }.accessibilityLabel("Video call")
            }
        }.tint(colors.accent)
        .onAppear { readVisible() }
        .onChange(of: scenePhase) { _ in readVisible() }
        .onChange(of: store.call?.id) { _ in readVisible() }
    }
    private func readVisible() { if scenePhase == .active && store.call == nil { store.markChatRead() } }
    private func messageBubble(_ message: FamilyChatMessage) -> some View {
        let mine = message.from == store.me
        return HStack {
            if mine { Spacer(minLength: 48) }
            VStack(alignment: .trailing, spacing: 5) {
                Text(message.text).font(.system(size: 15)).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading)
                Text(Date(timeIntervalSince1970: Double(message.createdAt) / 1000), style: .time)
                    .font(.system(size: 10)).foregroundStyle(colors.muted)
            }.padding(.horizontal, 14).padding(.vertical, 10)
                .foregroundStyle(mine ? colors.onHeader : colors.ink)
                .background(mine ? colors.header : colors.field, in: RoundedRectangle(cornerRadius: 20))
            if !mine { Spacer(minLength: 48) }
        }
    }
}
