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
@RequestMapping({"/ratingJsp", "/ratingJsp.view"})
public class RatingJspController {
    @GetMapping
    public ModelAndView get(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> model = new HashMap<>();
        model.put("rating", Integer.parseInt(request.getParameter("rating")));
        String readonlyParam = request.getParameter("readOnly");
        model.put("readOnly", "true".equalsIgnoreCase(readonlyParam));
        model.put("id", request.getParameter("id"));

        return new ModelAndView("rating", model);
    }
}
