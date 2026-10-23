package org.airsonic.player.controller;

import org.airsonic.player.command.PlayerSettingsCommand;
import org.airsonic.player.domain.*;
import org.airsonic.player.service.PlayQueueService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.TranscodingService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the player settings page.
 */
@Controller
@RequestMapping("/playerSettings")
public class PlayerSettingsController {
    private final PlayerService playerService;
    private final SecurityService securityService;
    private final TranscodingService transcodingService;
    private final PlayQueueService playQueueService;

    public PlayerSettingsController(PlayerService playerService, SecurityService securityService, TranscodingService transcodingService, PlayQueueService playQueueService) {
        this.playerService = playerService;
        this.securityService = securityService;
        this.transcodingService = transcodingService;
        this.playQueueService = playQueueService;
    }

    @GetMapping
    protected String displayForm() {
        return "playerSettings";
    }

    @ModelAttribute
    protected void formBackingObject(HttpServletRequest request, Model model) throws Exception {

        handleRequestParameters(request);
        List<Player> players = getPlayers(request);

        User user = securityService.getCurrentUser(request);
        PlayerSettingsCommand command = new PlayerSettingsCommand();
        Player player = null;
        Integer playerId = ServletRequestUtils.getIntParameter(request, "id");
        if (playerId != null) {
            player = playerService.getPlayerById(playerId);
        } else if (!players.isEmpty()) {
            player = players.getFirst();
        }

        if (player != null) {
            command.setPlayerId(player.getId());
            command.setName(player.getName());
            command.setDescription(player.toString());
            command.setType(player.getType());
            command.setLastSeen(player.getLastSeen());
            command.setDynamicIp(player.getDynamicIp());
            command.setAutoControlEnabled(player.getAutoControlEnabled());
            command.setM3uBomEnabled(player.getM3uBomEnabled());
            command.setTranscodeSchemeName(player.getTranscodeScheme().name());
            command.setTechnologyName(player.getTechnology().name());
            command.setAllTranscodings(transcodingService.getAllTranscodings());
            List<Transcoding> activeTranscodings = transcodingService.getTranscodingsForPlayer(player);
            int[] activeTranscodingIds = new int[activeTranscodings.size()];
            for (int i = 0; i < activeTranscodings.size(); i++) {
                activeTranscodingIds[i] = activeTranscodings.get(i).getId();
            }
            command.setActiveTranscodingIds(activeTranscodingIds);
        }

        command.setTranscodingSupported(transcodingService.isDownsamplingSupported(null));
        command.setTranscodeDirectory(SettingsService.getTranscodeDirectory().toString());
        command.setTranscodeSchemes(TranscodeScheme.values());
        command.setTechnologies(PlayerTechnology.values());
        command.setPlayers(players.toArray(new Player[players.size()]));
        command.setAdmin(user.isAdminRole());

        model.addAttribute("command",command);
    }

    @PostMapping
    protected String doSubmitAction(@ModelAttribute("command") PlayerSettingsCommand command, RedirectAttributes redirectAttributes) {
        Player player = playerService.getPlayerById(command.getPlayerId());
        if (player != null) {
            boolean update = false;
            boolean stopped = false;
            if (player.getAutoControlEnabled() != command.getAutoControlEnabled()) {
                player.setAutoControlEnabled(command.getAutoControlEnabled());
                update = true;
            }
            if (player.getM3uBomEnabled() != command.getM3uBomEnabled()) {
                player.setM3uBomEnabled(command.getM3uBomEnabled());
                update = true;
            }
            if (player.getDynamicIp() != command.getDynamicIp()) {
                player.setDynamicIp(command.getDynamicIp());
                update = true;
            }
            if (!StringUtils.equals(player.getName(), StringUtils.trimToNull(command.getName()))) {
                player.setName(StringUtils.trimToNull(command.getName()));
                update = true;
            }
            if (player.getTranscodeScheme() != TranscodeScheme.valueOf(command.getTranscodeSchemeName())) {
                if (!stopped) {
                    playQueueService.stop(player);
                    stopped = true;
                }
                player.setTranscodeScheme(TranscodeScheme.valueOf(command.getTranscodeSchemeName()));
                update = true;
            }
            if (player.getTechnology() != PlayerTechnology.valueOf(command.getTechnologyName())) {
                if (!stopped) {
                    playQueueService.stop(player);
                    stopped = true;
                }
                player.setTechnology(PlayerTechnology.valueOf(command.getTechnologyName()));
                update = true;
            }

            if (update) {
                playerService.updatePlayer(player);
            }

            transcodingService.setTranscodingsForPlayer(player, command.getActiveTranscodingIds());

            redirectAttributes.addFlashAttribute("settings_toast", true);
            return "redirect:playerSettings.view?id=" + command.getPlayerId();
        } else {
            return "redirect:notFound";
        }
    }

    private List<Player> getPlayers(HttpServletRequest request) {
        User user = securityService.getCurrentUser(request);
        String username = user.getUsername();
        List<Player> players = playerService.getAllPlayers();
        List<Player> authorizedPlayers = new ArrayList<>();

        for (Player player : players) {
            // Only display authorized players.
            if (user.isAdminRole() || username.equals(player.getUsername())) {
                authorizedPlayers.add(player);
            }
        }
        return authorizedPlayers;
    }

    private void handleRequestParameters(HttpServletRequest request) throws Exception {
        if (request.getParameter("delete") != null) {
            playerService.removePlayerById(ServletRequestUtils.getIntParameter(request, "delete"));
        } else if (request.getParameter("clone") != null) {
            playerService.clonePlayer(ServletRequestUtils.getIntParameter(request, "clone"));
        }
    }

}
