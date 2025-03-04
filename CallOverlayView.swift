import SwiftUI

struct CallOverlayView: View {
    var body: some View {
        Color.red
            .opacity(0.5)
            .edgesIgnoringSafeArea(.all)
    }
}

#if DEBUG
struct CallOverlayView_Previews: PreviewProvider {
    static var previews: some View {
        CallOverlayView()
    }
}
#endif
