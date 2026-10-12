package org.airsonic.player.controller;

import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.StatusReport;
import org.airsonic.player.service.StatusReportService;
import org.airsonic.player.service.TransferHistoryService;
import org.airsonic.player.service.TransferHistoryService.DailyTransfers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.util.List;

/**
 * Provides the data of the status page.
 */
@RestController
@RequestMapping({"/statistics", "/statistics.view"})
public class StatisticsController {

    private final StatusReportService statusReportService;
    private final TransferHistoryService transferHistoryService;
    private final SecurityService securityService;

    public StatisticsController(StatusReportService statusReportService, TransferHistoryService transferHistoryService,
            SecurityService securityService) {
        this.statusReportService = statusReportService;
        this.transferHistoryService = transferHistoryService;
        this.securityService = securityService;
    }

    /**
     * Everything the status page shows, in one response. Administrators see every transfer and the server
     * internals, other users only their own transfers and the library summary.
     */
    @GetMapping("/summary")
    public StatusReport getSummary(HttpServletRequest request) {
        return statusReportService.getReport(securityService.getCurrentUsername(request), request.isUserInRole("ADMIN"));
    }

    /**
     * The bytes transferred per day. Administrators get the totals of all users, other users their own.
     *
     * @param days the number of days, today included
     */
    @GetMapping("/history")
    public TransferHistory getHistory(HttpServletRequest request, @RequestParam(name = "days", defaultValue = "30") int days) {
        String username = request.isUserInRole("ADMIN") ? null : securityService.getCurrentUsername(request);
        return new TransferHistory(transferHistoryService.getHistoryStart(username),
                transferHistoryService.getDailyTransfers(days, username));
    }

    /**
     * @param start the first day with known transfers, null if nothing has been recorded yet
     */
    public record TransferHistory(LocalDate start, List<DailyTransfers> days) {
    }
}
