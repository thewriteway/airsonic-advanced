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

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpMessage;
import org.jupnp.model.message.UpnpRequest;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.message.header.UpnpHeader;
import org.jupnp.transport.spi.AbstractStreamClient;
import org.jupnp.transport.spi.InitializationException;
import org.jupnp.transport.spi.StreamClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;

/**
 * Implementation based on <a href="http://hc.apache.org/">Apache HTTP Components 4.5</a>.
 * <p>
 * This implementation <em>DOES NOT WORK</em> on Android. Read the Cling manual for
 * alternatives on Android.
 * </p>
 *
 * @author Christian Bauer
 */
public class StreamClientImpl extends AbstractStreamClient<StreamClientConfigurationImpl, HttpUriRequest> {

    private static final Logger LOG = LoggerFactory.getLogger(StreamClient.class); // jupnp category, see logging.level.org.jupnp

    final protected StreamClientConfigurationImpl configuration;
    final protected PoolingHttpClientConnectionManager clientConnectionManager;
    final protected CloseableHttpClient httpClient;

    public StreamClientImpl(StreamClientConfigurationImpl configuration) throws InitializationException {
        this.configuration = configuration;

        // These are some safety settings, we should never run into these timeouts as we
        // do our own expiration checking
        int timeoutMillis = (getConfiguration().getTimeoutSeconds() + 5) * 1000;

        RequestConfig requestConfig = RequestConfig.custom()
            .setExpectContinueEnabled(false)
            .setConnectTimeout(timeoutMillis)
            .setSocketTimeout(timeoutMillis)
            .build();

        // Only register 80, not 443 and SSL
        clientConnectionManager = new PoolingHttpClientConnectionManager(
            RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .build()
        );
        clientConnectionManager.setMaxTotal(getConfiguration().getMaxTotalConnections());
        clientConnectionManager.setDefaultMaxPerRoute(getConfiguration().getMaxTotalPerRoute());
        clientConnectionManager.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(timeoutMillis).build());
        if (getConfiguration().getSocketBufferSize() != -1) {
            clientConnectionManager.setDefaultConnectionConfig(
                ConnectionConfig.custom().setBufferSize(getConfiguration().getSocketBufferSize()).build()
            );
        }
        // Replaces the stale connection check: when enabled, validate any pooled connection idle for 1ms or
        // more before reusing it; when disabled, never (the pool would otherwise validate after 2s idle)
        clientConnectionManager.setValidateAfterInactivity(getConfiguration().getStaleCheckingEnabled() ? 1 : 0);

        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
            .setConnectionManager(clientConnectionManager)
            .setDefaultRequestConfig(requestConfig)
            // DefaultHttpClient neither requested nor decoded compressed content
            .disableContentCompression()
            // The user agent is set per request, see createRequest()
            .disableDefaultUserAgent();
        if (getConfiguration().getRequestRetryCount() != -1) {
            clientBuilder.setRetryHandler(
                new DefaultHttpRequestRetryHandler(getConfiguration().getRequestRetryCount(), false)
            );
        }
        httpClient = clientBuilder.build();
    }

    @Override
    public StreamClientConfigurationImpl getConfiguration() {
        return configuration;
    }


    @Override
    protected HttpUriRequest createRequest(StreamRequestMessage requestMessage) {
        UpnpRequest requestOperation = requestMessage.getOperation();
        HttpRequestBase request = switch (requestOperation.getMethod()) {
            case GET -> new HttpGet(requestOperation.getURI());
            case SUBSCRIBE -> new HttpGet(requestOperation.getURI()) {
                @Override
                public String getMethod() {
                    return UpnpRequest.Method.SUBSCRIBE.getHttpName();
                }
            };
            case UNSUBSCRIBE -> new HttpGet(requestOperation.getURI()) {
                @Override
                public String getMethod() {
                    return UpnpRequest.Method.UNSUBSCRIBE.getHttpName();
                }
            };
            case POST -> {
                HttpPost post = new HttpPost(requestOperation.getURI());
                post.setEntity(createHttpRequestEntity(requestMessage));
                yield post;
            }
            case NOTIFY -> {
                HttpPost notify = new HttpPost(requestOperation.getURI()) {
                    @Override
                    public String getMethod() {
                        return UpnpRequest.Method.NOTIFY.getHttpName();
                    }
                };
                notify.setEntity(createHttpRequestEntity(requestMessage));
                yield notify;
            }
            default -> throw new RuntimeException("Unknown HTTP method: " + requestOperation.getHttpMethodName());
        };

        request.setProtocolVersion(
            requestOperation.getHttpMinorVersion() == 0 ? HttpVersion.HTTP_1_0 : HttpVersion.HTTP_1_1
        );

        // Headers
        HeaderUtil.add(request, requestMessage.getHeaders());

        // HttpClient adds HOST header automatically in its default processor

        // Add the default user agent if not already set on the message
        if (!requestMessage.getHeaders().containsKey(UpnpHeader.Type.USER_AGENT)
                && !request.containsHeader(HttpHeaders.USER_AGENT)) {
            request.addHeader(
                HttpHeaders.USER_AGENT,
                getConfiguration().getUserAgentValue(requestMessage.getUdaMajorVersion(), requestMessage.getUdaMinorVersion())
            );
        }

        return request;
    }

    @Override
    protected Callable<StreamResponseMessage> createCallable(final StreamRequestMessage requestMessage,
                                                             final HttpUriRequest request) {
        return new Callable<StreamResponseMessage>() {
            public StreamResponseMessage call() throws Exception {

                LOG.debug("Sending HTTP request: {}", requestMessage);

                return httpClient.execute(request, createResponseHandler());
            }
        };
    }

    @Override
    protected void abort(HttpUriRequest request) {
        request.abort();
    }

    @Override
    protected boolean logExecutionException(Throwable t) {
        if (t instanceof IllegalStateException) {
            // HttpClient throws this when the connection manager has been shut down or the request was
            // aborted (e.g. by a timeout) mid-flight; both are expected during stop/timeout, so only log at debug
            LOG.debug("Illegal state: {}", t.getMessage());
            return true;
        }
        return false;
    }

    @Override
    public void stop() {
        LOG.debug("Shutting down HTTP client connection manager/pool");
        clientConnectionManager.shutdown();
    }

    protected HttpEntity createHttpRequestEntity(UpnpMessage upnpMessage) {
        if (upnpMessage.getBodyType().equals(UpnpMessage.BodyType.BYTES)) {
            LOG.debug("Preparing HTTP request entity as byte[]");
            return new ByteArrayEntity(upnpMessage.getBodyBytes());
        } else {
            LOG.debug("Preparing HTTP request entity as string");
            try {
                Charset charset = upnpMessage.getContentTypeCharset();
                return new StringEntity(upnpMessage.getBodyString(), charset != null ? charset.name() : "UTF-8");
            } catch (Exception ex) {
                // WTF else am I supposed to do with this exception?
                throw new RuntimeException(ex);
            }
        }
    }

    protected ResponseHandler<StreamResponseMessage> createResponseHandler() {
        return new ResponseHandler<StreamResponseMessage>() {
            public StreamResponseMessage handleResponse(final HttpResponse httpResponse) throws IOException {

                StatusLine statusLine = httpResponse.getStatusLine();
                LOG.debug("Received HTTP response: {}", statusLine);

                // Status
                UpnpResponse responseOperation =
                    new UpnpResponse(statusLine.getStatusCode(), statusLine.getReasonPhrase());

                // Message
                StreamResponseMessage responseMessage = new StreamResponseMessage(responseOperation);

                // Headers
                responseMessage.setHeaders(new UpnpHeaders(HeaderUtil.get(httpResponse)));

                // Body
                HttpEntity entity = httpResponse.getEntity();
                if (entity == null || entity.getContentLength() == 0) {
                    LOG.debug("HTTP response message has no entity");
                    return responseMessage;
                }

                byte data[] = EntityUtils.toByteArray(entity);
                if (data != null) {
                    if (responseMessage.isContentTypeMissingOrText()) {
                        LOG.debug("HTTP response message contains text entity");
                        responseMessage.setBodyCharacters(data);
                    } else {
                        LOG.debug("HTTP response message contains binary entity");
                        responseMessage.setBody(UpnpMessage.BodyType.BYTES, data);
                    }
                } else {
                    LOG.debug("HTTP response message has no entity");
                }

                return responseMessage;
            }
        };
    }
}
