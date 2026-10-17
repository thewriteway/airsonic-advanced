package org.airsonic.player.spring;

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
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class LoggingExceptionResolverTest {

    @Mock
    private StatusService statusService;

    private static final String LOGGER = LoggingExceptionResolver.class.getName();

    private final List<Runnable> deferred = new ArrayList<>();
    private final LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

    private LoggingExceptionResolver resolver;
    private MockHttpServletRequest request;
    private TransferStatus status;

    @BeforeEach
    void setUp() {
        resolver = new LoggingExceptionResolver(statusService, deferred::add);
        request = new MockHttpServletRequest("GET", "/stream");
        request.setQueryString("player=17&id=1718148");
        status = new TransferStatus(new Player());
        loggingSystem.setLogLevel(LOGGER, LogLevel.DEBUG);
    }

    @AfterEach
    void tearDown() {
        loggingSystem.setLogLevel(LOGGER, null);
    }

    private ModelAndView resolveAbort() {
        Exception abort = new AsyncRequestNotUsableException("Response not usable", new ClientAbortException());
        return resolver.resolveException(request, new MockHttpServletResponse(), null, abort);
    }

    private static List<String> resolverLines(CapturedOutput output) {
        return output.getAll().lines()
                .filter(line -> line.contains("LoggingExceptionResolver"))
                .toList();
    }

    private static void assertSingleLog(CapturedOutput output, LogLevel level, String text) {
        List<String> lines = resolverLines(output);
        assertEquals(1, lines.size(), String.join(System.lineSeparator(), lines));
        assertTrue(lines.get(0).contains(level.name()), lines.get(0));
        assertTrue(lines.get(0).contains(text), lines.get(0));
    }

    @Test
    void abortWithoutStreamStatusLogsClosedAndStopsOtherResolvers(CapturedOutput output) {
        ModelAndView result = resolveAbort();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertSingleLog(output, LogLevel.INFO, "Client closed connection");
        verify(statusService, never()).isStreamSuperseded(status);
    }

    @Test
    void streamAlreadyReplacedLogsSupersededImmediately(CapturedOutput output) {
        request.setAttribute(TransferStatus.REQUEST_ATTRIBUTE, status);
        when(statusService.isStreamSuperseded(status)).thenReturn(true);

        resolveAbort();

        assertTrue(deferred.isEmpty());
        assertSingleLog(output, LogLevel.DEBUG, "replaced stream");
    }

    @Test
    void streamReplacedDuringGracePeriodLogsSuperseded(CapturedOutput output) {
        request.setAttribute(TransferStatus.REQUEST_ATTRIBUTE, status);
        when(statusService.isStreamSuperseded(status)).thenReturn(false, true);

        resolveAbort();
        assertTrue(resolverLines(output).isEmpty());
        assertEquals(1, deferred.size());

        deferred.get(0).run();
        assertSingleLog(output, LogLevel.DEBUG, "replaced stream");
    }

    @Test
    void streamNotReplacedLogsClosedAfterGracePeriod(CapturedOutput output) {
        request.setAttribute(TransferStatus.REQUEST_ATTRIBUTE, status);
        when(statusService.isStreamSuperseded(status)).thenReturn(false);

        resolveAbort();
        deferred.get(0).run();

        assertSingleLog(output, LogLevel.INFO, "Client closed connection while loading http://localhost/stream?player=17&id=1718148");
    }
}
