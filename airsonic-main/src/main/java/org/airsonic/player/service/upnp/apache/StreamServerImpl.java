/*
  This file is part of Airsonic.

  Airsonic is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  Airsonic is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

  Forked from https://github.com/4thline/cling version 2.0.1, then
  modified to run with jupnp.

   Copyright (C) 2013 4th Line GmbH, Switzerland
 */
package org.airsonic.player.service.upnp.apache;

import org.apache.http.impl.DefaultBHttpServerConnection;
import org.apache.http.impl.io.DefaultHttpRequestParserFactory;
import org.jupnp.model.message.Connection;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.StreamServer;
import org.jupnp.transport.spi.UpnpStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Implementation based on <a href="http://hc.apache.org/">Apache HTTP Components 4.4</a>.
 * <p>
 * This implementation <em>DOES NOT WORK</em> on Android. Read the Cling manual for
 * alternatives on Android.
 * </p>
 *
 * @author Christian Bauer
 */
public class StreamServerImpl implements StreamServer<StreamServerConfigurationImpl> {

    private static final Logger LOG = LoggerFactory.getLogger(StreamServer.class); // jupnp category, see logging.level.org.jupnp

    /**
     * Smallest chunk written straight through to the socket instead of being buffered; matches the
     * <code>http.connection.min-chunk-limit</code> default of the former DefaultHttpServerConnection.
     */
    private static final int FRAGMENT_SIZE_HINT = 512;

    final protected StreamServerConfigurationImpl configuration;

    protected Router router;
    protected ServerSocket serverSocket;
    private volatile boolean stopped = false;

    public StreamServerImpl(StreamServerConfigurationImpl configuration) {
        this.configuration = configuration;
    }

    public StreamServerConfigurationImpl getConfiguration() {
        return configuration;
    }

    synchronized public void init(InetAddress bindAddress, Router router) throws InitializationException {

        try {

            this.router = router;

            this.serverSocket =
                    new ServerSocket(
                            configuration.getListenPort(),
                            configuration.getTcpConnectionBacklog(),
                            bindAddress
                    );

            LOG.info("Created socket (for receiving TCP streams) on: {}", serverSocket.getLocalSocketAddress());

        } catch (Exception ex) {
            throw new InitializationException("Could not initialize " + getClass().getSimpleName() + ": " + ex.toString(), ex);
        }

    }

    synchronized public int getPort() {
        return this.serverSocket.getLocalPort();
    }

    synchronized public void stop() {
        stopped = true;
        try {
            serverSocket.close();
        } catch (IOException ex) {
            LOG.debug("Exception closing streaming server socket: {}", ex.toString());
        }
    }

    public void run() {

        LOG.debug("Entering blocking receiving loop, listening for HTTP stream requests on: {}", serverSocket.getLocalSocketAddress());
        while (!stopped) {

            try {

                // Block until we have a connection
                final Socket clientSocket = serverSocket.accept();

                // We have to force this fantastic library to accept HTTP methods which are not in the holy RFCs.
                final DefaultBHttpServerConnection httpServerConnection = new DefaultBHttpServerConnection(
                        // Same 1 KB floor the former SocketInputBuffer/SocketOutputBuffer applied
                        Math.max(configuration.getBufferSizeKilobytes() * 1024, 1024),
                        FRAGMENT_SIZE_HINT,
                        null, null, null, null, null,
                        new DefaultHttpRequestParserFactory(null, new UpnpHttpRequestFactory()),
                        null
                );

                LOG.debug("Incoming connection from: {}", clientSocket.getInetAddress());
                // The socket options DefaultHttpServerConnection.bind(Socket, HttpParams) used to set
                clientSocket.setTcpNoDelay(configuration.isTcpNoDelay());
                clientSocket.setSoTimeout(configuration.getDataWaitTimeoutSeconds() * 1000);
                clientSocket.setKeepAlive(false);
                httpServerConnection.bind(clientSocket);

                // Wrap the processing of the request in a UpnpStream
                UpnpStream connectionStream =
                        new HttpServerConnectionUpnpStream(
                                router.getProtocolFactory(),
                                httpServerConnection
                        ) {
                            @Override
                            protected Connection createConnection() {
                                return new ApacheServerConnection(
                                    clientSocket, httpServerConnection
                                );

                            }
                        };

                router.received(connectionStream);

            } catch (InterruptedIOException ex) {
                LOG.debug("I/O has been interrupted, stopping receiving loop, bytes transfered: {}", ex.bytesTransferred);
                break;
            } catch (SocketException ex) {
                if (!stopped) {
                    // That's not good, could be anything
                    LOG.debug("Exception using server socket: {}", ex.getMessage());
                } else {
                    // Well, it's just been stopped so that's totally fine and expected
                }
                break;
            } catch (IOException ex) {
                LOG.debug("Exception initializing receiving loop: {}", ex.getMessage());
                break;
            }
        }

        try {
            LOG.debug("Receiving loop stopped");
            if (!serverSocket.isClosed()) {
                LOG.debug("Closing streaming server socket");
                serverSocket.close();
            }
        } catch (Exception ex) {
            LOG.info("Exception closing streaming server socket: {}", ex.getMessage());
        }

    }

    /**
     * Writes a space character to the output stream of the socket.
     * <p>
     * This space character might confuse the HTTP client. The Cling transports for Jetty Client and
     * Apache HttpClient have been tested to work with space characters. Unfortunately, Sun JDK's
     * HttpURLConnection does not gracefully handle any garbage in the HTTP request!
     * </p>
     */
    protected boolean isConnectionOpen(Socket socket) {
        return isConnectionOpen(socket, " ".getBytes(StandardCharsets.UTF_8));
    }

    protected boolean isConnectionOpen(Socket socket, byte[] heartbeat) {
        LOG.debug("Checking if client connection is still open on: {}", socket.getRemoteSocketAddress());
        try {
            socket.getOutputStream().write(heartbeat);
            socket.getOutputStream().flush();
            return true;
        } catch (IOException ex) {
            LOG.debug("Client connection has been closed: {}", socket.getRemoteSocketAddress());
            return false;
        }
    }

    protected class ApacheServerConnection implements Connection {

        protected Socket socket;
        protected DefaultBHttpServerConnection connection;

        public ApacheServerConnection(Socket socket, DefaultBHttpServerConnection connection) {
            this.socket = socket;
            this.connection = connection;
        }

        @Override
        public boolean isOpen() {
            return isConnectionOpen(socket);
        }

        @Override
        public InetAddress getRemoteAddress() {
            return connection.getRemoteAddress();
        }

        @Override
        public InetAddress getLocalAddress() {
            return connection.getLocalAddress();
        }
    }

}
