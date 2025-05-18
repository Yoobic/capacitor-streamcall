package ee.forgr.capacitor.streamcall;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import io.getstream.video.android.core.Call;
import io.getstream.video.android.core.ParticipantState;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.flow.launchIn;
import kotlinx.coroutines.flow.onEach;
import kotlinx.coroutines.launch;
import io.getstream.webrtc.android.ui.VideoTextureViewRenderer;
import stream.video.sfu.models.TrackType;

class StreamCallFragment : Fragment() {
    private var call: Call? = null
    private var videoRenderer: VideoTextureViewRenderer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        videoRenderer = VideoTextureViewRenderer(requireContext())
        return videoRenderer!!
    }

    fun setCall(call: Call) {
        this.call = call
        videoRenderer?.let { renderer ->
            // Setup video rendering for local video
            call.initRenderer(renderer, call.sessionId, TrackType.TRACK_TYPE_VIDEO)
            // Setup listener for remote participants' video
            val scope = CoroutineScope(Dispatchers.Main)
            scope.launch {
                call.state.participants.onEach { participantStates ->
                    participantStates.forEach { participantState ->
                        participantState.videoTrack.onEach { videoTrack ->
                            videoTrack?.let { track ->
                                // Additional renderers might be needed for multiple participants
                                track.video.addSink(renderer)
                            }
                        }.launchIn(scope)
                    }
                }.launchIn(scope)
            }
        }
    }

    fun getCall(): Call? {
        return call
    }

    override fun onDestroyView() {
        super.onDestroyView()
        videoRenderer = null
    }
} 
