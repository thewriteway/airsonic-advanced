package org.airsonic.player.spring;

import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.service.StatusService;
import org.airsonic.player.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingExceptionResolver implements HandlerExceptionResolver, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingExceptionResolver.class);

    /**
     * How long to wait for a replacing stream request. Browsers drop the old connection
     * and open the new one at the same time, so the old one usually fails first.
     */
    private static final long SUPERSEDE_GRACE_MILLIS = 2000;

    private final StatusService statusService;
    private final Executor graceExecutor;

    @Autowired
    public LoggingExceptionResolver(StatusService statusService) {
        this(statusService, CompletableFuture.delayedExecutor(SUPERSEDE_GRACE_MILLIS, TimeUnit.MILLISECONDS));
    }

    LoggingExceptionResolver(StatusService statusService, Executor graceExecutor) {
        this.statusService = statusService;
        this.graceExecutor = graceExecutor;
    }

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request, HttpServletResponse response, Object o, Exception e
    ) {
        // This happens often and outside of the control of the server, so
        // we catch Tomcat/Jetty "connection aborted by client" exceptions
        // and display a short error message. There is no one left to send
        // an error response to, so stop other resolvers from trying.
        if (isClientAbortException(e)) {
            logClientAbort(request);
            return new ModelAndView();
        }

        // Requests for missing paths (browser probes, scanners) are ordinary 404s;
        // let Spring answer them without logging a stack trace.
        if (e instanceof NoResourceFoundException || e instanceof NoHandlerFoundException) {
            LOG.debug("{}: Not found: {}", request.getRemoteAddr(), Util.getAnonymizedURLForRequest(request));
            return null;
        }

        // Display a full stack trace in all other cases
        LOG.error("{}: An exception occurred while loading {}", request.getRemoteAddr(), Util.getAnonymizedURLForRequest(request), e);
        return null;
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }

    /**
     * Logs a dropped connection. A stream the same player replaced with a newer one
     * (track change, seek) is routine and logged at debug; anything else at info.
     */
    private void logClientAbort(HttpServletRequest request) {
        // Read everything now: the request is recycled once this thread returns
        String address = request.getRemoteAddr();
        String url = Util.getAnonymizedURLForRequest(request);
        if (!(request.getAttribute(TransferStatus.REQUEST_ATTRIBUTE) instanceof TransferStatus status)) {
            logClosed(address, url);
        } else if (statusService.isStreamSuperseded(status)) {
            logSuperseded(address, url);
        } else {
            graceExecutor.execute(() -> {
                if (statusService.isStreamSuperseded(status)) {
                    logSuperseded(address, url);
                } else {
                    logClosed(address, url);
                }
            });
        }
    }

    private static void logClosed(String address, String url) {
        LOG.info("{}: Client closed connection while loading {}", address, url);
    }

    private static void logSuperseded(String address, String url) {
        LOG.debug("{}: Client replaced stream {} with a newer request", address, url);
    }

    /**
     * Check if the exception or any of its causes is a "client abort" exception.
     *
     * @param e The exception to check
     * @return True if the exception or any of its causes is a "client abort" exception
     */
    private boolean isClientAbortException(Throwable e) {
        if (e == null) {
            return false;
        }
        if (Util.isInstanceOfClassName(e, "org.apache.catalina.connector.ClientAbortException")) {
            return true;
        }
        return isClientAbortException(e.getCause());
    }
}
