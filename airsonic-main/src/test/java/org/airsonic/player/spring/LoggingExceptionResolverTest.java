package org.airsonic.player.spring;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.service.StatusService;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoggingExceptionResolverTest {

    @Mock
    private StatusService statusService;

    private final List<Runnable> deferred = new ArrayList<>();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingExceptionResolver.class);
    private Level originalLevel;

    private LoggingExceptionResolver resolver;
    private MockHttpServletRequest request;
    private TransferStatus status;

    @BeforeEach
    void setUp() {
        resolver = new LoggingExceptionResolver(statusService, deferred::add);
        request = new MockHttpServletRequest("GET", "/stream");
        request.setQueryString("player=17&id=1718148");
        status = new TransferStatus(new Player());

        originalLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }

    private ModelAndView resolveAbort() {
        Exception abort = new AsyncRequestNotUsableException("Response not usable", new ClientAbortException());
        return resolver.resolveException(request, new MockHttpServletResponse(), null, abort);
    }

    private void assertSingleLog(Level level, String text) {
        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(level, event.getLevel());
        assertTrue(event.getFormattedMessage().contains(text), event.getFormattedMessage());
    }

    @Test
    void abortWithoutStreamStatusLogsClosedAndStopsOtherResolvers() {
        ModelAndView result = resolveAbort();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertSingleLog(Level.INFO, "Client closed connection");
        verify(statusService, never()).isStreamSuperseded(status);
    }

    @Test
    void streamAlreadyReplacedLogsSupersededImmediately() {
        request.setAttribute(TransferStatus.REQUEST_ATTRIBUTE, status);
        when(statusService.isStreamSuperseded(status)).thenReturn(true);

        resolveAbort();

        assertTrue(deferred.isEmpty());
        assertSingleLog(Level.DEBUG, "replaced stream");
    }

    @Test
    void streamReplacedDuringGracePeriodLogsSuperseded() {
        request.setAttribute(TransferStatus.REQUEST_ATTRIBUTE, status);
        when(statusService.isStreamSuperseded(status)).thenReturn(false, true);

        resolveAbort();
        assertTrue(appender.list.isEmpty());
        assertEquals(1, deferred.size());

        deferred.get(0).run();
        assertSingleLog(Level.DEBUG, "replaced stream");
    }

    @Test
    void streamNotReplacedLogsClosedAfterGracePeriod() {
        request.setAttribute(TransferStatus.REQUEST_ATTRIBUTE, status);
        when(statusService.isStreamSuperseded(status)).thenReturn(false);

        resolveAbort();
        deferred.get(0).run();

        assertSingleLog(Level.INFO, "Client closed connection while loading");
        assertTrue(appender.list.get(0).getFormattedMessage().contains("/stream?player=17&id=1718148"));
    }
}
