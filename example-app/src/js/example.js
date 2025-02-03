import { StreamCall } from 'stream-call';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    StreamCall.echo({ value: inputValue })
}
