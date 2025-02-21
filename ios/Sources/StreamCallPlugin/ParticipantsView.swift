//
//  ParticipantsView.swift
//  Pods
//
//  Created by Michał Tremblay on 05/02/2025.
//

import SwiftUI
import StreamVideo
import StreamVideoSwiftUI

struct ParticipantsView: View {

    var call: Call
    var participants: [CallParticipant]
    var onChangeTrackVisibility: (CallParticipant?, Bool) -> Void
    var localParticipant: CallParticipant

    var body: some View {
        GeometryReader { proxy in
            if !participants.isEmpty {
                ZStack {
                    if participants.count >= 5 && participants.count <= 6 {
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 0), count: 3), spacing: 0) {
                            ForEach(participants.prefix(participants.count)) { participant in
                                makeCallParticipantView(participant, frame: proxy.frame(in: .global))
                                    .frame(width: proxy.size.width / 3, height: proxy.size.height / 2)
                            }
                            if participants.count == 5 {
                                Color.clear
                                    .frame(width: proxy.size.width / 3, height: proxy.size.height / 2)
                            }
                        }
                    } else {
                        ScrollView {
                            LazyVStack {
                                if participants.count == 1, let participant = participants.first {
                                    makeCallParticipantView(participant, frame: proxy.frame(in: .global))
                                        .frame(width: proxy.size.width, height: proxy.size.height)
                                } else {
                                    ForEach(participants.filter { $0.userId != localParticipant.userId }) { participant in
                                        makeCallParticipantView(participant, frame: proxy.frame(in: .global))
                                            .frame(width: proxy.size.width, height: participants.count == 4 ? proxy.size.height / 3 : (participants.count == 2 ? proxy.size.height : proxy.size.height / 2))
                                    }
                                }
                            }
                        }
                        
                        if participants.count >= 2 && participants.count <= 4 {
                            CornerDraggableView(
                                content: { availableFrame in
                                    LocalVideoView(
                                        viewFactory: DefaultViewFactory.shared,
                                        participant: localParticipant,
                                        callSettings: call.state.callSettings,
                                        call: call,
                                        availableFrame: CGRect(
                                            x: availableFrame.origin.x,
                                            y: availableFrame.origin.y,
                                            width: availableFrame.width,
                                            height: availableFrame.height * 0.8
                                        )
                                    )
                                    .frame(width: availableFrame.width, height: availableFrame.height * 0.8)
                                    .cornerRadius(12)
                                },
                                proxy: proxy
                            ) {
                                withAnimation {
                                    if participants.count == 1 {
                                        // call.state.callSettings.localVideoPrimary.toggle()
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Color.gray
            }
        }
        .edgesIgnoringSafeArea(.all)
    }

    @ViewBuilder
    private func makeCallParticipantView(_ participant: CallParticipant, frame: CGRect) -> some View {
        VideoCallParticipantView(
            participant: participant,
            availableFrame: frame,
            contentMode: .scaleAspectFit,
            customData: [:],
            call: call
        )
        .onAppear { onChangeTrackVisibility(participant, true) }
        .onDisappear{ onChangeTrackVisibility(participant, false) }
    }
}
