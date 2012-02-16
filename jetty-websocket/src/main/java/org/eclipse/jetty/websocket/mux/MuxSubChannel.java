package org.eclipse.jetty.websocket.mux;

import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocket.OnBinaryMessage;
import org.eclipse.jetty.websocket.WebSocket.OnControl;
import org.eclipse.jetty.websocket.WebSocket.OnTextMessage;
import org.eclipse.jetty.websocket.WebSocketConnectionRFC6455;

/**
 * MuxSubChannel is a {@link MuxChannel} for communications, with id > 0.
 * 
 * @see <a href="http://tools.ietf.org/html/draft-tamplin-hybi-google-mux-02">draft-tamplin-hybi-google-mux-02</a>
 */
public class MuxSubChannel implements MuxChannel
{
    private WebSocket _webSocket;

    // TODO: determine how many of these are valid under mux conditions.
    // private WebSocket.OnFrame _onFrame; // not MUX possible
    private WebSocket.OnTextMessage _onTextMessage;
    private WebSocket.OnBinaryMessage _onBinaryMessage;
    private WebSocket.OnControl _onControl;

    public MuxSubChannel(WebSocket websocket)
    {
        _webSocket = websocket;
        // _onFrame = _webSocket instanceof OnFrame?(OnFrame)_webSocket:null;
        _onTextMessage = _webSocket instanceof OnTextMessage?(OnTextMessage)_webSocket:null;
        _onBinaryMessage = _webSocket instanceof OnBinaryMessage?(OnBinaryMessage)_webSocket:null;
        _onControl = _webSocket instanceof OnControl?(OnControl)_webSocket:null;
    }

    public void onMuxData(int channelId, byte[] data, int offset, int length)
    {
        // Send to underlying websocket impl
        _onBinaryMessage.onMessage(data,offset,length);
    }

    public void onMuxOpen(int channelId, MuxConnection connection)
    {
        // Notify MUX of open connection
        _onControl.onOpen(connection);
    }

    public void onMuxClose(int channelId, MuxConnection connection)
    {
        // Notify MUX of close connection
        _onControl.onClose(WebSocketConnectionRFC6455.CLOSE_NORMAL,"channel closed normally");
    }
}