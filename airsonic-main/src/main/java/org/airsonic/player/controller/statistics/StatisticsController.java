package org.airsonic.player.controller.statistics;

import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.StatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/statistics")
public class StatisticsController {
    private final SecurityService securityService;
    private final StatusService statusService;

    public StatisticsController(SecurityService securityService, StatusService statusService) {
        this.securityService = securityService;
        this.statusService = statusService;
    }

    @GetMapping("/users")
    public List<UserDataTransferStatistics> getUserStatistics() {
        return securityService.getAllUsers().stream().map(x -> new UserDataTransferStatistics(x.getUsername(), x.getBytesStreamed(), x.getBytesDownloaded(), x.getBytesUploaded())).collect(Collectors.toList());
    }

    @GetMapping("/transfers")
    public List<TransferStatusHolder> getTransferStatistics(HttpServletRequest request) {
        List<TransferStatus> streamStatuses = statusService.getAllStreamStatuses();
        List<TransferStatus> downloadStatuses = statusService.getAllDownloadStatuses();
        List<TransferStatus> uploadStatuses = statusService.getAllUploadStatuses();

        Locale locale = RequestContextUtils.getLocale(request);
        List<TransferStatusHolder> transferStatuses = new ArrayList<>();
        streamStatuses.stream()
                .filter(x -> ((x.getMillisSinceLastUpdate() / 1000L / 60L) < 60L))
                .map(x -> new TransferStatusHolder(x, TransferType.stream, locale))
                .forEach(transferStatuses::add);

        downloadStatuses.stream()
                .map(x -> new TransferStatusHolder(x, TransferType.download, locale))
                .forEach(transferStatuses::add);

        uploadStatuses.stream()
                .map(x -> new TransferStatusHolder(x, TransferType.upload, locale))
                .forEach(transferStatuses::add);

        return transferStatuses;
    }
}
