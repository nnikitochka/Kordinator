package org.airsonic.player.controller;

import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.VersionService;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Controller for the help page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping("/help")
public class HelpController {

    private static final Logger LOG = LoggerFactory.getLogger(HelpController.class);

    private static final int LOG_LINES_TO_SHOW = 50;

    private final VersionService versionService;
    private final SettingsService settingsService;
    private final SecurityService securityService;

    public HelpController(VersionService versionService, SettingsService settingsService, SecurityService securityService) {
        this.versionService = versionService;
        this.settingsService = settingsService;
        this.securityService = securityService;
    }

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> map = new HashMap<>();

        if (versionService.isNewFinalVersionAvailable()) {
            map.put("newVersionAvailable", true);
            map.put("latestVersion", versionService.getLatestFinalVersion());
        } else if (versionService.isNewBetaVersionAvailable()) {
            map.put("newVersionAvailable", true);
            map.put("latestVersion", versionService.getLatestBetaVersion());
        }

        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();

        String serverInfo = request.getSession().getServletContext().getServerInfo() +
                            ", java " + System.getProperty("java.version") +
                            ", " + System.getProperty("os.name");

        map.put("user", securityService.getCurrentUser(request));
        map.put("brand", settingsService.getBrand());
        map.put("localVersion", versionService.getLocalVersion());
        map.put("buildDate", versionService.getLocalBuildDate());
        map.put("buildNumber", versionService.getLocalBuildNumber());
        map.put("serverInfo", serverInfo);
        map.put("usedMemory", totalMemory - freeMemory);
        map.put("totalMemory", totalMemory);
        Path logFile = Paths.get(settingsService.getLogFile());
        List<String> latestLogEntries = getLatestLogEntries(logFile);
        map.put("logEntries", latestLogEntries);
        map.put("logFile", logFile);

        return new ModelAndView("help","model",map);
    }

    private static List<String> getLatestLogEntries(Path logFile) {
        List<String> lines = new LinkedList<>();
        try (ReversedLinesFileReader reader = new ReversedLinesFileReader(logFile.toFile(), Charset.defaultCharset())) {
            String current;
            while ((current = reader.readLine()) != null) {
                if (lines.size() >= LOG_LINES_TO_SHOW) {
                    break;
                }
                lines.addFirst(current);
            }
            return lines;
        } catch (IOException e) {
            LOG.warn("Could not open log file {}", logFile, e);
            return null;
        }
    }


}
