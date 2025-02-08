import SwiftUI
import StreamVideo
import StreamVideoSwiftUI
import Combine

class CallOverlayViewModel: ObservableObject {
    @Published var streamVideo: StreamVideo?
    @Published var call: Call?
    @Published var callState: CallState?
    @Published var participants: [CallParticipant] = []
    
    private var participantsSubscription: AnyCancellable?
    
    init(streamVideo: StreamVideo?) {
        self.streamVideo = streamVideo
    }
    
    @MainActor
    func updateCall(_ call: Call?) {
        self.call = call
        // Clean up previous subscription if any
        participantsSubscription?.cancel()
        
        if let call = call {
            participantsSubscription = call.state.$participants.sink { [weak self] participants in
                if self?.participants.map({ $0.id }).sorted() != participants.map({ $0.id }).sorted() {
                    print("Participants update \(participants.map { $0.name })")
                }
                
                self?.participants = participants
            }
            self.callState = call.state
        } else {
            // Clear participants when call ends
            self.participants = []
            self.callState = nil
        }
    }
    
    @MainActor
    func updateStreamVideo(_ streamVideo: StreamVideo?) {
        self.streamVideo = streamVideo
        if streamVideo == nil {
            self.call = nil
            self.callState = nil
        }
    }
}

struct CallOverlayView: View {
    @ObservedObject var viewModel: CallOverlayViewModel
    
    init(viewModel: CallOverlayViewModel) {
        self.viewModel = viewModel
    }
    
    var body: some View {
        VStack {
            if let streamVideo = viewModel.streamVideo {
                if let call = viewModel.call, let callState = viewModel.callState {
                    ZStack {
                        ParticipantsView(
                            call: call,
                            participants: viewModel.participants,
                            onChangeTrackVisibility: changeTrackVisibility(_:isVisible:)
                        )
                    }
                } else {
                    Text("Waiting for call...")
                }
            } else {
                Text("Please login to start a call")
            }
        }
    }
    
    private func changeTrackVisibility(_ participant: CallParticipant?, isVisible: Bool) {
        guard let participant = participant,
              let call = viewModel.call else { return }
        Task {
            await call.changeTrackVisibility(for: participant, isVisible: isVisible)
        }
    }
}

extension CallOverlayView {
    static func create(streamVideo: StreamVideo?) -> (UIHostingController<CallOverlayView>, CallOverlayViewModel) {
        let viewModel = CallOverlayViewModel(streamVideo: streamVideo)
        let view = CallOverlayView(viewModel: viewModel)
        let hostingController = UIHostingController(rootView: view)
        return (hostingController, viewModel)
    }
}

#if DEBUG
struct CallOverlayView_Previews: PreviewProvider {
    static var previews: some View {
        CallOverlayView(viewModel: CallOverlayViewModel(streamVideo: nil))
    }
}
#endif 
