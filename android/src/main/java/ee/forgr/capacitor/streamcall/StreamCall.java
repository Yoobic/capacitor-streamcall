package ee.forgr.capacitor.streamcall;

import android.util.Log;

public class StreamCall {

    public String echo(String value) {
        Log.i("Echo", value);
        return value;
    }
}
