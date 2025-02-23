//
//  ParticipantsView.swift
//  Pods
//
//  Created by Michał Tremblay on 05/02/2025.
//

import SwiftUI
import StreamVideo
import StreamVideoSwiftUI

// Data structure to hold a label and a frame.
struct ViewFramePreferenceData: Equatable {
    let label: String
    let frame: CGRect
}

// PreferenceKey to collect frames.
struct ViewFramePreferenceKey: PreferenceKey {
    static var defaultValue: [ViewFramePreferenceData] = []
    
    static func reduce(value: inout [ViewFramePreferenceData], nextValue: () -> [ViewFramePreferenceData]) {
        value.append(contentsOf: nextValue())
    }
}

// An extension to attach a label and record the view's frame.
extension View {
    func labelFrame(_ label: String) -> some View {
        self.background(
            GeometryReader { geo in
                Color.clear
                    .preference(key: ViewFramePreferenceKey.self,
                                value: [ViewFramePreferenceData(label: label,
                                                                frame: geo.frame(in: .global))])
                    .onAppear {
                        print("ParticipantsView - Collecting frame for label: \(label)")
                        print("Frame: \(geo.frame(in: .global))")
                    }
            }
        )
    }
}

struct ParticipantsView: View {

    var call: Call
    var participants: [CallParticipant]
    var onChangeTrackVisibility: (CallParticipant?, Bool) -> Void
    var localParticipant: CallParticipant
    @State private var labeledFrames: [ViewFramePreferenceData] = []
    
    private func findTouchInterceptView() -> TouchInterceptView? {
        // Find the TouchInterceptView by traversing up the view hierarchy
        var currentView = UIApplication.shared.windows.first?.rootViewController?.view
        while let view = currentView {
            if let touchInterceptView = view as? TouchInterceptView {
                return touchInterceptView
            }
            currentView = view.superview
        }
        return nil
    }

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
                                    .labelFrame("abc")
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
                .onPreferenceChange(ViewFramePreferenceKey.self) { frames in
                    print("ParticipantsView - Received frame updates:")
                    print("Number of frames: \(frames.count)")
                    frames.forEach { frame in
                        print("Label: \(frame.label), Frame: \(frame.frame)")
                    }
                    self.labeledFrames = frames
                    if let touchInterceptView = findTouchInterceptView() {
                        print("ParticipantsView - Found TouchInterceptView, updating frames")
                        touchInterceptView.updateLabeledFrames(frames)
                    } else {
                        print("ParticipantsView - Failed to find TouchInterceptView!")
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
