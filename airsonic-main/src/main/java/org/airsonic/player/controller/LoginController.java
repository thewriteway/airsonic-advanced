package org.airsonic.player.controller;

import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring MVC Controller that serves the login page.
 */
@Controller
@RequestMapping({"/login", "/login.view"})
public class LoginController {

    @Autowired
    private SecurityService securityService;
    @Autowired
    private SettingsService settingsService;

    @GetMapping
    public ModelAndView login(HttpServletRequest request, HttpServletResponse response) {

        // Auto-login if "user" and "password" parameters are given.
        String username = request.getParameter("user");
        String password = request.getParameter("password");
        if (username != null && password != null) {
            username = StringUtil.urlEncode(username);
            password = StringUtil.urlEncode(password);
            return new ModelAndView(new RedirectView("/login?" +
                    UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY + "=" + username +
                    "&" + UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_PASSWORD_KEY + "=" + password
            ));
        }

        Map<String, Object> map = new HashMap<String, Object>();
        map.put("logout", request.getParameter("logout") != null);
        map.put("error", request.getParameter("error") != null);
        map.put("brand", settingsService.getBrand());
        map.put("loginMessage", settingsService.getLoginMessage());

        if (securityService.checkDefaultAdminCredsPresent()) {
            map.put("insecure", true);
        } else {
            map.put("insecure", false);
        }

        return new ModelAndView("login", "model", map);
    }
}
