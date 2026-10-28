package org.airsonic.player.controller.rest.scan;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.rest.RestPlayerResolver;
import org.airsonic.player.service.MediaScannerService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.ScanStatus;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class ScanController {
    private final MediaScannerService scanner;
    private final JAXBWriter jaxbWriter;
    private final RestPlayerResolver playerResolver;

    public ScanController(MediaScannerService scanner, JAXBWriter jaxbWriter, RestPlayerResolver playerResolver) {
        this.scanner = scanner;
        this.jaxbWriter = jaxbWriter;
        this.playerResolver = playerResolver;
    }

    @RequestMapping("/startScan")
    public void startScan(HttpServletRequest request, HttpServletResponse response) {
        resolvePlayer(request);
        scanner.scanLibrary();
        getScanStatus(request, response);
    }

    @RequestMapping("/getScanStatus")
    public void getScanStatus(HttpServletRequest request, HttpServletResponse response) {
        resolvePlayer(request);
        ScanStatus status = new ScanStatus();
        status.setScanning(scanner.isScanning());
        status.setCount((long) scanner.getScanCount());
        Response result = jaxbWriter.createResponse(true);
        result.setScanStatus(status);
        jaxbWriter.writeResponse(request, response, result);
    }

    private void resolvePlayer(HttpServletRequest request) {
        playerResolver.getOrCreatePlayerId(request.getRemoteUser(), request.getParameter("c"), request.getRemoteAddr(), false);
    }
}
