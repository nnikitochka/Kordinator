package org.airsonic.player.filter;

import org.airsonic.player.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This filter is executed very early in the filter chain. It verifies that
 * the Airsonic home directory (c:\airsonic or /var/airsonic) exists and
 * is writable. If not, a proper error message is given to the user.
 * <p/>
 * (The Airsonic home directory is usually created automatically, but a common
 * problem on Linux is that the Tomcat user does not have the necessary
 * privileges).
 */
public class BootstrapVerificationFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(BootstrapVerificationFilter.class);
    private boolean airsonicHomeVerified = false;
    private final AtomicBoolean serverInfoLogged = new AtomicBoolean();

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        // Already verified?
        if (airsonicHomeVerified) {
            chain.doFilter(req, res);
            return;
        }

        Path home = SettingsService.getAirsonicHome();
        if (!directoryExists(home)) {
            error(res, "<p>The directory <b>" + home + "</b> does not exist. Please create it and make it writable, " +
                       "then restart the servlet container.</p>" +
                       "<p>(You can override the directory location by specifying -Dairsonic.home=... when " +
                       "starting the servlet container.)</p>");

        } else if (!directoryWritable(home)) {
            error(res, "<p>The directory <b>" + home + "</b> is not writable. Please change file permissions, " +
                       "then restart the servlet container.</p>" +
                       "<p>(You can override the directory location by specifying -Dairsonic.home=... when " +
                       "starting the servlet container.)</p>");

        } else {
            airsonicHomeVerified = true;
            logServerInfo(req);
            chain.doFilter(req, res);
        }
    }

    private void logServerInfo(ServletRequest req) {
        if (!serverInfoLogged.getAndSet(true) && req instanceof HttpServletRequest) {
            String serverInfo = ((HttpServletRequest) req).getSession().getServletContext().getServerInfo();
            LOG.info("Servlet container: {}", serverInfo);
        }
    }

    private boolean directoryExists(Path dir) {
        return Files.exists(dir) && Files.isDirectory(dir);
    }

    private boolean directoryWritable(Path dir) {
        try {
            Path tempFile = Files.createTempFile(dir, "test", null);
            return Files.exists(tempFile) && Files.deleteIfExists(tempFile);
        } catch (Exception x) {
            return false;
        }
    }

    private void error(ServletResponse res, String error) throws IOException {
        ServletOutputStream out = res.getOutputStream();
        out.println("<html>" +
                    "<head><title>Airsonic Error</title></head>" +
                    "<body>" +
                    "<h2>Airsonic Error</h2>" +
                    error +
                    "</body>" +
                    "</html>");
    }

    public void init(FilterConfig filterConfig) {
    }

    public void destroy() {
    }
}
