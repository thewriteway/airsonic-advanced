package org.airsonic.player.security;

import org.airsonic.player.service.JWTSecurityService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;

public class JWTRequestParameterProcessingFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(JWTRequestParameterProcessingFilter.class);
    private final AuthenticationManager authenticationManager;
    private final AuthenticationFailureHandler failureHandler;

    protected JWTRequestParameterProcessingFilter(AuthenticationManager authenticationManager, String failureUrl) {
        this.authenticationManager = authenticationManager;
        failureHandler = new SimpleUrlAuthenticationFailureHandler(failureUrl);
    }

    public Authentication attemptAuthentication(Optional<JWTAuthenticationToken> token) throws AuthenticationException {
        if (token.isPresent()) {
            return authenticationManager.authenticate(token.get());
        }
        throw new AuthenticationServiceException("Invalid auth method");
    }

    private static Optional<JWTAuthenticationToken> findToken(HttpServletRequest request) {
        return Optional.ofNullable(request.getParameter(JWTSecurityService.JWT_PARAM_NAME))
                .filter(StringUtils::isNotEmpty)
                .map(t -> new JWTAuthenticationToken(null, t, request.getRequestURI().substring(request.getContextPath().length() + 1) + "?" + request.getQueryString()));
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException,
            ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        Optional<JWTAuthenticationToken> token = findToken(request);
        if (!token.isPresent()) {
            chain.doFilter(req, resp);
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Request is to process authentication");
        }

        Authentication authResult;

        try {
            authResult = attemptAuthentication(token);
            if (authResult == null) {
                // return immediately as subclass has indicated that it hasn't completed
                // authentication
                return;
            }
        } catch (InternalAuthenticationServiceException failed) {
            LOG.error(
                    "An internal error occurred while trying to authenticate the user.",
                    failed);
            unsuccessfulAuthentication(request, response, failed);

            return;
        } catch (AuthenticationException e) {
            unsuccessfulAuthentication(request, response, e);
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Authentication success. Updating SecurityContextHolder to contain: "
                    + authResult);
        }

        SecurityContextHolder.getContext().setAuthentication(authResult);

        chain.doFilter(request, response);
    }

    protected void unsuccessfulAuthentication(HttpServletRequest request,
                                              HttpServletResponse response, AuthenticationException failed)
            throws IOException, ServletException {
        SecurityContextHolder.clearContext();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Authentication request failed: " + failed.toString(), failed);
            LOG.debug("Updated SecurityContextHolder to contain null Authentication");
            LOG.debug("Delegating to authentication failure handler " + failureHandler);
        }

        failureHandler.onAuthenticationFailure(request, response, failed);
    }

    @Override
    public void destroy() {
    }

}
