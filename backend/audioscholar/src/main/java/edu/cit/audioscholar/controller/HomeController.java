package edu.cit.audioscholar.controller;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import edu.cit.audioscholar.dto.Monitor;
import edu.cit.audioscholar.service.UptimeRobotService;

@Controller
public class HomeController {

    private final UptimeRobotService uptimeRobotService;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.ENGLISH);

    @Autowired
    public HomeController(UptimeRobotService uptimeRobotService) {
        this.uptimeRobotService = uptimeRobotService;
    }

    @GetMapping("/")
    public String showStatusPage(Model model) {
        ZonedDateTime now = ZonedDateTime.now();
        String lastUpdated = now.format(TIMESTAMP_FORMATTER);
        long nextUpdateSeconds = 300;

        List<Monitor> monitors = uptimeRobotService.getMonitors();

        String overallStatusText = "No Monitors";
        String overallStatusSuffix = "";
        String overallStatusColor = "grey";
        boolean hasIssues = false;
        boolean allUp = true;

        if (monitors != null && !monitors.isEmpty()) {
            for (Monitor monitor : monitors) {
                if (monitor.getStatus() == 8 || monitor.getStatus() == 9) {
                    hasIssues = true;
                    allUp = false;
                } else if (monitor.getStatus() != 2) {
                    allUp = false;
                }
            }

            if (hasIssues) {
                overallStatusText = "Some systems";
                overallStatusSuffix = "Down";
                overallStatusColor = "orange";
            } else if (allUp) {
                overallStatusText = "All systems";
                overallStatusSuffix = "Operational";
                overallStatusColor = "green";
            } else {
                overallStatusText = "Some systems";
                overallStatusSuffix = "Not Reporting";
                overallStatusColor = "orange";
            }
        } else if (monitors == null) {
            overallStatusText = "Status";
            overallStatusSuffix = "Unavailable";
            overallStatusColor = "red";
            hasIssues = true;
            monitors = List.of();
        } else {
            overallStatusText = "No systems";
            overallStatusSuffix = "Configured";
            overallStatusColor = "grey";
        }

        model.addAttribute("monitors", monitors);
        model.addAttribute("overallStatusText", overallStatusText);
        model.addAttribute("overallStatusSuffix", overallStatusSuffix);
        model.addAttribute("overallStatusColor", overallStatusColor);
        model.addAttribute("hasError", monitors == null || hasIssues);
        model.addAttribute("lastUpdated", lastUpdated);
        model.addAttribute("nextUpdateSeconds", nextUpdateSeconds);

        return "status";
    }
}
