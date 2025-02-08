import {
  Call,
  CallControls,
  CallingState,
  SpeakerLayout,
  StreamCall,
  StreamTheme,
  StreamVideo,
  StreamVideoClient,
  useCallStateHooks,
} from '@stream-io/video-react-sdk';

import '@stream-io/video-react-sdk/dist/css/styles.css';
import './style.css';
import { useState } from 'react';

export default function App() {
  const apiKey = 'n8wv8vjmucdw';
  const API_URL = 'https://magic-login-srv-35.localcan.dev';
  const [isCallActive, setIsCallActive] = useState(false);
  const [client, setClient] = useState<StreamVideoClient | null>(null);
  const [call, setCall] = useState<Call | null>(null);
  
  async function joinCall() {
    if (!client) {
      // Fetch user credentials from backend
      const response = await fetch(`${API_URL}/user?user_id=Shmi_Skywalker`);
      const userData = await response.json();
      
      if (!userData) {
        throw new Error('No response from server');
      }
  
      // Create a new client instance with the fetched credentials
      const newClient = new StreamVideoClient({ 
        apiKey, 
        token: userData.token,
        user: {
          id: userData.userId,
          name: userData.name,
          image: userData.imageURL
        }
      });
  
      // Store client in state
      setClient(newClient);
    }
  
    if (!call && client) {
      // Create and store call in state
      const uuid = Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15); // Generate random UUID using Math.random()
      const newCall = client.call('default', uuid);
      await newCall.getOrCreate({
        ring: true,
        data: {
          members: [
            { user_id: 'user1' },
            { user_id: 'Shmi_Skywalker' },
          ]
        }
      });
      setCall(newCall);
      setIsCallActive(true);
      return;
    }
  }

  return (
    <>
      {isCallActive && client && call ? (
        <StreamVideo client={client}>
          <StreamCall call={call}>
            <MyUILayout />
          </StreamCall>
        </StreamVideo>
      ) : (
        <button onClick={() => joinCall()}>Join Call</button>
      )}
    </>
  );
}

export const MyUILayout = () => {
  const { useCallCallingState } = useCallStateHooks();
  const callingState = useCallCallingState();

  if (callingState !== CallingState.JOINED) {
    return <div>Loading...</div>;
  }

  return (
    <StreamTheme>
      <SpeakerLayout participantsBarPosition='bottom' />
      <CallControls />
    </StreamTheme>
  );
};