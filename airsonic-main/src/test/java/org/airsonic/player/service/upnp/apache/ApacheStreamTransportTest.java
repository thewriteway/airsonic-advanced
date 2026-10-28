package org.airsonic.player.service.upnp.apache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jupnp.UpnpService;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.message.header.ContentTypeHeader;
import org.jupnp.model.message.header.UpnpHeader;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.protocol.ReceivingSync;
import org.jupnp.transport.Router;
import org.jupnp.transport.spi.UpnpStream;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Round-trips UPnP stream messages between {@link StreamClientImpl} and {@link StreamServerImpl} over loopback.
 */
public class ApacheStreamTransportTest {

    private final AtomicReference<StreamRequestMessage> received = new AtomicReference<>();
    private volatile Function<StreamRequestMessage, StreamResponseMessage> handler;

    private ExecutorService executor;
    private StreamServerImpl server;
    private Thread serverThread;
    private StreamClientImpl client;
    private String baseUri;

    @BeforeEach
    public void setUp() throws Exception {
        executor = Executors.newCachedThreadPool();

        UpnpService upnpService = mock(UpnpService.class);
        ProtocolFactory protocolFactory = mock(ProtocolFactory.class);
        when(protocolFactory.createReceivingSync(any())).thenAnswer(invocation ->
            new ReceivingSync<StreamRequestMessage, StreamResponseMessage>(upnpService, invocation.getArgument(0)) {
                @Override
                protected StreamResponseMessage executeSync() {
                    received.set(getInputMessage());
                    return handler.apply(getInputMessage());
                }
            });
        Router router = mock(Router.class);
        when(router.getProtocolFactory()).thenReturn(protocolFactory);
        doAnswer(invocation -> {
            executor.execute(invocation.<UpnpStream>getArgument(0));
            return null;
        }).when(router).received(any(UpnpStream.class));

        InetAddress loopback = InetAddress.getLoopbackAddress();
        server = new StreamServerImpl(new StreamServerConfigurationImpl());
        server.init(loopback, router);
        serverThread = new Thread(server, "upnp-stream-server-test");
        serverThread.start();
        baseUri = "http://" + loopback.getHostAddress() + ":" + server.getPort();

        client = new StreamClientImpl(new StreamClientConfigurationImpl(executor, 5));
    }

    @AfterEach
    public void tearDown() throws Exception {
        client.stop();
        server.stop();
        serverThread.join(5000);
        executor.shutdownNow();
    }

    @Test
    public void getReturnsTextBodyAndSendsDefaultUserAgent() throws Exception {
        handler = request -> new StreamResponseMessage("<root/>", new ContentTypeHeader(ContentTypeHeader.DEFAULT_CONTENT_TYPE_UTF8));

        StreamRequestMessage request = new StreamRequestMessage(UpnpRequest.Method.GET, URI.create(baseUri + "/dev/desc.xml"));
        StreamResponseMessage response = client.sendRequest(request);

        assertNotNull(response);
        assertEquals(200, response.getOperation().getStatusCode());
        assertEquals("<root/>", response.getBodyString());
        assertNotNull(response.getHeaders().getFirstHeader("Date"));

        StreamRequestMessage seen = received.get();
        assertEquals(UpnpRequest.Method.GET, seen.getOperation().getMethod());
        assertEquals("/dev/desc.xml", seen.getUri().getPath());
        assertEquals(1, seen.getOperation().getHttpMinorVersion());
        String userAgent = client.getConfiguration().getUserAgentValue(request.getUdaMajorVersion(), request.getUdaMinorVersion());
        assertEquals(List.of(userAgent), seen.getHeaders().get("User-Agent"));
        assertNull(seen.getHeaders().get("Accept-Encoding"));
        assertNull(seen.getHeaders().get("Expect"));
    }

    @Test
    public void subscribeAndUnsubscribeUseUpnpMethods() throws Exception {
        handler = request -> {
            StreamResponseMessage response = new StreamResponseMessage(UpnpResponse.Status.OK);
            response.getHeaders().add("SID", "uuid:1234");
            return response;
        };

        for (UpnpRequest.Method method : List.of(UpnpRequest.Method.SUBSCRIBE, UpnpRequest.Method.UNSUBSCRIBE)) {
            StreamRequestMessage request = new StreamRequestMessage(method, URI.create(baseUri + "/evt"));
            request.getHeaders().add("SID", "uuid:1234");

            StreamResponseMessage response = client.sendRequest(request);

            assertNotNull(response, method.name());
            assertEquals(200, response.getOperation().getStatusCode());
            assertEquals("uuid:1234", response.getHeaders().getFirstHeader("SID"));
            assertEquals(method, received.get().getOperation().getMethod());
            assertEquals("uuid:1234", received.get().getHeaders().getFirstHeader("SID"));
        }
    }

    @Test
    public void notifyKeepsBodyAndExplicitUserAgent() throws Exception {
        handler = request -> new StreamResponseMessage(UpnpResponse.Status.OK);

        StreamRequestMessage request = new StreamRequestMessage(UpnpRequest.Method.NOTIFY, URI.create(baseUri + "/cb"), "<e:propertyset/>");
        request.getHeaders().add(UpnpHeader.Type.CONTENT_TYPE, new ContentTypeHeader(ContentTypeHeader.DEFAULT_CONTENT_TYPE_UTF8));
        request.getHeaders().add("User-Agent", "TestAgent/1.0");

        StreamResponseMessage response = client.sendRequest(request);

        assertNotNull(response);
        assertEquals(200, response.getOperation().getStatusCode());
        StreamRequestMessage seen = received.get();
        assertEquals(UpnpRequest.Method.NOTIFY, seen.getOperation().getMethod());
        assertEquals("<e:propertyset/>", seen.getBodyString());
        assertEquals(List.of("TestAgent/1.0"), seen.getHeaders().get("User-Agent"));
    }

    @Test
    public void unhandledRequestIsNotFound() throws Exception {
        handler = request -> null;

        StreamResponseMessage response = client.sendRequest(new StreamRequestMessage(UpnpRequest.Method.GET, URI.create(baseUri + "/missing")));

        assertNotNull(response);
        assertEquals(404, response.getOperation().getStatusCode());
    }

    @Test
    public void timeoutAbortsRequestAndReleasesConnection() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        handler = request -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new StreamResponseMessage(UpnpResponse.Status.OK);
        };
        StreamClientImpl slowClient = new StreamClientImpl(new StreamClientConfigurationImpl(executor, 1));
        try {
            long start = System.nanoTime();
            StreamResponseMessage response = slowClient.sendRequest(new StreamRequestMessage(UpnpRequest.Method.GET, URI.create(baseUri + "/slow")));

            assertNull(response);
            assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 5);
            // The aborted request must hand its connection back while the server is still stalled
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (slowClient.clientConnectionManager.getTotalStats().getLeased() > 0 && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertEquals(0, slowClient.clientConnectionManager.getTotalStats().getLeased());
        } finally {
            release.countDown();
            slowClient.stop();
        }
    }

    @Test
    public void requestAfterStopIsSwallowed() throws Exception {
        handler = request -> new StreamResponseMessage(UpnpResponse.Status.OK);
        client.stop();

        // The shut down pool throws IllegalStateException, which logExecutionException() handles
        assertNull(client.sendRequest(new StreamRequestMessage(UpnpRequest.Method.GET, URI.create(baseUri + "/stopped"))));
        assertTrue(client.logExecutionException(new IllegalStateException("Connection pool shut down")));
    }

    @Test
    public void clientSendsRequestedHttpVersionWithoutCompressionOrExpectContinue() throws Exception {
        try (ServerSocket rawServer = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            Future<List<String>> head = executor.submit(() -> {
                try (Socket socket = rawServer.accept()) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    List<String> lines = new ArrayList<>();
                    for (String line = reader.readLine(); line != null && !line.isEmpty(); line = reader.readLine()) {
                        lines.add(line);
                    }
                    socket.getOutputStream().write("HTTP/1.0 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    return lines;
                }
            });
            StreamRequestMessage request = new StreamRequestMessage(UpnpRequest.Method.NOTIFY,
                    URI.create("http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + rawServer.getLocalPort() + "/cb"), "<e:propertyset/>");
            request.getOperation().setHttpMinorVersion(0);

            StreamResponseMessage response = client.sendRequest(request);

            assertNotNull(response);
            assertEquals(200, response.getOperation().getStatusCode());
            List<String> lines = head.get(5, TimeUnit.SECONDS);
            assertEquals("NOTIFY /cb HTTP/1.0", lines.get(0));
            assertTrue(lines.stream().noneMatch(line -> line.regionMatches(true, 0, "Accept-Encoding:", 0, 16)), lines.toString());
            assertTrue(lines.stream().noneMatch(line -> line.regionMatches(true, 0, "Expect:", 0, 7)), lines.toString());
            assertEquals(1, lines.stream().filter(line -> line.regionMatches(true, 0, "User-Agent:", 0, 11)).count(), lines.toString());
        }
    }
}
