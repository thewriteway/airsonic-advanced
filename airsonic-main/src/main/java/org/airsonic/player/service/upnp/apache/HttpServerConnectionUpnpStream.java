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

import org.apache.http.ConnectionClosedException;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpServerConnection;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.HttpRequestHandlerMapper;
import org.apache.http.protocol.HttpService;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.util.EntityUtils;
import org.jupnp.model.UnsupportedDataException;
import org.jupnp.model.message.Connection;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.transport.spi.UpnpStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Implementation for Apache HTTP Components API.
 *
 * @author Christian Bauer
 */
public abstract class HttpServerConnectionUpnpStream extends UpnpStream {

    private static final Logger LOG = LoggerFactory.getLogger(UpnpStream.class); // jupnp category, see logging.level.org.jupnp

    protected final HttpServerConnection connection;
    protected final HttpProcessor httpProcessor;
    protected final HttpService httpService;

    protected HttpServerConnectionUpnpStream(ProtocolFactory protocolFactory,
                                             HttpServerConnection connection) {
        super(protocolFactory);
        this.connection = connection;

        httpProcessor = HttpProcessorBuilder.create()
                // The Date header is recommended in UDA, need to document the requirement in StreamServer interface?
                .add(new ResponseDate())
                // The Server header is only required for Control so callers have to add it to UPnPMessage
                // .add(new ResponseServer())
                .add(new ResponseContent())
                .add(new ResponseConnControl())
                .build();

        httpService =
                new UpnpHttpService(
                        httpProcessor,
                        DefaultConnectionReuseStrategy.INSTANCE,
                        DefaultHttpResponseFactory.INSTANCE
                );
    }

    public HttpServerConnection getConnection() {
        return connection;
    }

    public void run() {

        try {
            while (!Thread.interrupted() && connection.isOpen()) {
                LOG.debug("Handling request on open connection...");
                HttpContext context = new BasicHttpContext(null);
                httpService.handleRequest(connection, context);
            }
        } catch (ConnectionClosedException ex) {
            LOG.debug("Client closed connection");
            responseException(ex);
        } catch (SocketTimeoutException ex) {
            LOG.debug("Server-side closed socket (this is 'normal' behavior of Apache HTTP Core!): {}", ex.getMessage());
        } catch (IOException ex) {
            LOG.warn("I/O exception during HTTP request processing: {}", ex.getMessage());
            responseException(ex);
        } catch (HttpException ex) {
            throw new UnsupportedDataException("Request malformed: " + ex.getMessage(), ex);
        } finally {
            try {
                connection.shutdown();
            } catch (IOException ex) {
                LOG.debug("Error closing connection: {}", ex.getMessage());
            }
        }
    }

    /**
     * A thread-safe custom service implementation that creates a UPnP message from the request,
     * then passes it to <tt>UpnpStream#process()</tt>, finally sends the response back to the
     * client.
     */
    protected class UpnpHttpService extends HttpService {

        public UpnpHttpService(HttpProcessor processor, ConnectionReuseStrategy reuse, HttpResponseFactory responseFactory) {
            // No handler mapper: requests are dispatched by the doService() override below
            super(processor, reuse, responseFactory, (HttpRequestHandlerMapper) null);
        }

        @Override
        protected void doService(HttpRequest httpRequest, HttpResponse httpResponse, HttpContext ctx)
                throws HttpException, IOException {

            LOG.debug("Processing HTTP request: {}", httpRequest.getRequestLine());

            // Extract what we need from the HTTP httpRequest
            String requestMethod = httpRequest.getRequestLine().getMethod();
            String requestURI = httpRequest.getRequestLine().getUri();

            StreamRequestMessage requestMessage;
            try {
                requestMessage =
                    new StreamRequestMessage(
                            UpnpRequest.Method.getByHttpName(requestMethod),
                            URI.create(requestURI)
                    );
            } catch (IllegalArgumentException e) {
                String msg = "Invalid request URI: " + requestURI + ": " + e.getMessage();
                LOG.warn(msg);
                throw new HttpException(msg, e);
            }

            if (requestMessage.getOperation().getMethod().equals(UpnpRequest.Method.UNKNOWN)) {
                LOG.debug("Method not supported by UPnP stack: {}", requestMethod);
                throw new MethodNotSupportedException("Method not supported: " + requestMethod);
            }

            LOG.debug("Created new request message: {}", requestMessage);

            // HTTP version
            int requestHttpMinorVersion = httpRequest.getProtocolVersion().getMinor();
            requestMessage.getOperation().setHttpMinorVersion(requestHttpMinorVersion);

            // Connection wrapper
            requestMessage.setConnection(createConnection());

            // Headers
            requestMessage.setHeaders(new UpnpHeaders(HeaderUtil.get(httpRequest)));

            // Body
            if (httpRequest instanceof HttpEntityEnclosingRequest entityEnclosingHttpRequest) {
                LOG.debug("Request contains entity body, setting on UPnP message");

                HttpEntity entity = entityEnclosingHttpRequest.getEntity();

                byte data[] = EntityUtils.toByteArray(entity);
                if (data != null) {
                    if (requestMessage.isContentTypeMissingOrText()) {
                        LOG.debug("HTTP request message contains text entity");
                        requestMessage.setBodyCharacters(data);
                    } else {
                        LOG.debug("HTTP request message contains binary entity");
                        requestMessage.setBody(UpnpMessage.BodyType.BYTES, data);
                    }
                } else {
                    LOG.debug("HTTP request message has no entity");
                }


            } else {
                LOG.debug("Request did not contain entity body");
            }

            // Finally process it
            StreamResponseMessage responseMsg;
            try {
                responseMsg = process(requestMessage);
            } catch (RuntimeException ex) {
                LOG.debug("Exception occurred during UPnP stream processing: {}", ex.toString());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cause: {}", unwrap(ex), unwrap(ex));
                }

                LOG.debug("Sending HTTP response: {}", HttpStatus.SC_INTERNAL_SERVER_ERROR);
                httpResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);

                responseException(ex);
                return;
            }

            if (responseMsg != null) {
                LOG.debug("Sending HTTP response message: {}", responseMsg);

                // Status line
                httpResponse.setStatusLine(
                        new BasicStatusLine(
                                new ProtocolVersion("HTTP", 1, responseMsg.getOperation().getHttpMinorVersion()),
                                responseMsg.getOperation().getStatusCode(),
                                responseMsg.getOperation().getStatusMessage()
                        )
                );

                LOG.debug("Response status line: {}", httpResponse.getStatusLine());

                // Headers
                HeaderUtil.add(httpResponse, responseMsg.getHeaders());

                // Entity
                if (responseMsg.hasBody() && responseMsg.getBodyType().equals(UpnpMessage.BodyType.BYTES)) {
                    httpResponse.setEntity(new ByteArrayEntity(responseMsg.getBodyBytes()));
                } else if (responseMsg.hasBody() && responseMsg.getBodyType().equals(UpnpMessage.BodyType.STRING)) {
                    StringEntity responseEntity = new StringEntity(responseMsg.getBodyString(), StandardCharsets.UTF_8);
                    httpResponse.setEntity(responseEntity);
                }

            } else {
                // If it's null, it's 404, everything else needs a proper httpResponse
                LOG.debug("Sending HTTP response: {}", HttpStatus.SC_NOT_FOUND);
                httpResponse.setStatusCode(HttpStatus.SC_NOT_FOUND);
            }

            responseSent(responseMsg);
        }

    }

    abstract protected Connection createConnection();

    /** Returns the root cause of the given throwable (replacement for org.seamless.util.Exceptions.unwrap). */
    private static Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

}