package org.airsonic.player.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping({"/coverArtJsp", "/coverArtJsp.view"})
public class CoverArtJspController {
    @GetMapping
    public ModelAndView get(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> model = new HashMap<>();
        model.put("hideOverflow", "true".equalsIgnoreCase(request.getParameter("hideOverflow")));
        model.put("showLink", "true".equalsIgnoreCase(request.getParameter("showLink")));
        model.put("coverArtSize", Integer.parseInt(request.getParameter("coverArtSize")));
        model.put("albumId", request.getParameter("albumId"));
        model.put("playlistId", request.getParameter("playlistId"));
        model.put("podcastChannelId", request.getParameter("podcastChannelId"));
        model.put("caption1", request.getParameter("caption1"));
        model.put("caption2", request.getParameter("caption2"));
        model.put("caption3", request.getParameter("caption3"));
        model.put("captionCount", request.getParameter("captionCount"));
        model.put("showChange", "true".equalsIgnoreCase(request.getParameter("showChange")));
        model.put("showZoom", "true".equalsIgnoreCase(request.getParameter("showZoom")));

        return new ModelAndView("coverArt", model);
    }
}
