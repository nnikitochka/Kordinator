package org.airsonic.player.controller.rest.ping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.airsonic.player.controller.JAXBWriter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class PingController {
    private final JAXBWriter jaxbWriter;

    public PingController(JAXBWriter jaxbWriter) {
        this.jaxbWriter = jaxbWriter;
    }

    @RequestMapping("/ping")
    public void ping(HttpServletRequest request, HttpServletResponse response) {
        jaxbWriter.writeResponse(request, response, jaxbWriter.createResponse(true));
    }
}
