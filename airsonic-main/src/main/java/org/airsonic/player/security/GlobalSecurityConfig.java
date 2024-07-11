package org.airsonic.player.security;

import com.google.common.collect.ImmutableMap;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.sonos.SonosLinkSecurityInterceptor.SonosJWTVerification;
import org.apache.commons.codec.binary.Base16;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.airsonic.player.security.MultipleCredsMatchingAuthenticationProvider.SALT_TOKEN_MECHANISM_SPECIALIZATION;

@Configuration
@EnableWebSecurity
public class GlobalSecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalSecurityConfig.class);

    static final String FAILURE_URL = "/login?error";

    @SuppressWarnings("deprecation")
    public static final Map<String, PasswordEncoder> ENCODERS = new HashMap<>(ImmutableMap
            .<String, PasswordEncoder>builderWithExpectedSize(19)
            .put("bcrypt", new BCryptPasswordEncoder())
            .put("ldap", new org.springframework.security.crypto.password.LdapShaPasswordEncoder())
            .put("MD4", new org.springframework.security.crypto.password.Md4PasswordEncoder())
            .put("MD5", new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("MD5"))
            .put("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8())
            .put("scrypt", SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8())
            .put("SHA-1", new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("SHA-1"))
            .put("SHA-256", new org.springframework.security.crypto.password.MessageDigestPasswordEncoder("SHA-256"))
            .put("sha256", new org.springframework.security.crypto.password.StandardPasswordEncoder())
            .put("argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8())

            // base decodable encoders
            .put("noop", new PasswordEncoderDecoderWrapper(org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance(), p -> p))
            .put("hex", new HexPasswordEncoder())
            .put("encrypted-AES-GCM", new AesGcmPasswordEncoder()) // placeholder (real instance created below)

            // base decodable encoders that rely on salt+token being passed in (not stored in db with this type)
            .put("noop" + SALT_TOKEN_MECHANISM_SPECIALIZATION, new SaltedTokenPasswordEncoder(p -> p))
            .put("hex" + SALT_TOKEN_MECHANISM_SPECIALIZATION, new SaltedTokenPasswordEncoder(new HexPasswordEncoder()))
            .put("encrypted-AES-GCM" + SALT_TOKEN_MECHANISM_SPECIALIZATION, new SaltedTokenPasswordEncoder(new AesGcmPasswordEncoder())) // placeholder (real instance created below)

            // TODO: legacy marked base encoders, to be upgraded to one-way formats at breaking version change
            .put("legacynoop", new PasswordEncoderDecoderWrapper(org.springframework.security.crypto.password.NoOpPasswordEncoder.getInstance(), p -> p))
            .put("legacyhex", new HexPasswordEncoder())

            .put("legacynoop" + SALT_TOKEN_MECHANISM_SPECIALIZATION, new SaltedTokenPasswordEncoder(p -> p))
            .put("legacyhex" + SALT_TOKEN_MECHANISM_SPECIALIZATION, new SaltedTokenPasswordEncoder(new HexPasswordEncoder()))
            .build());

    public static final Set<String> OPENTEXT_ENCODERS = Set.of("noop", "hex", "legacynoop", "legacyhex");
    public static final Set<String> DECODABLE_ENCODERS = Set.of("noop", "hex", "logacynoop", "encrypted-AES-GCM");
    public static final Set<String> NONLEGACY_ENCODERS = ENCODERS.keySet().stream()
            .filter(e -> !StringUtils.containsAny(e, "legacy", SALT_TOKEN_MECHANISM_SPECIALIZATION))
            .collect(Collectors.toSet());
    public static final Set<String> NONLEGACY_DECODABLE_ENCODERS = SetUtils.intersection(DECODABLE_ENCODERS, NONLEGACY_ENCODERS);
    public static final Set<String> NONLEGACY_NONDECODABLE_ENCODERS = SetUtils.difference(NONLEGACY_ENCODERS, DECODABLE_ENCODERS);

    @Autowired
    private CsrfSecurityRequestMatcher csrfSecurityRequestMatcher;

    @Autowired
    private SecurityService securityService;

    @Autowired
    SettingsService settingsService;

    @Lazy
    @Autowired
    MultipleCredsMatchingAuthenticationProvider multipleCredsProvider;

    @Autowired
    SonosJWTVerification sonosJwtVerification;

    @Bean
    public PasswordEncoder passwordEncoder() {
        boolean generatedKeys = false;

        String encryptionKeyPass = settingsService.getEncryptionPassword();
        if (StringUtils.isBlank(encryptionKeyPass)) {
            LOG.warn("Generating new encryption key password");
            encryptionKeyPass = JWTSecurityService.generateKey();
            settingsService.setEncryptionPassword(encryptionKeyPass);
            generatedKeys = true;
        }

        String encryptionKeySalt = settingsService.getEncryptionSalt();
        if (StringUtils.isBlank(encryptionKeySalt)) {
            LOG.warn("Generating new encryption key salt");
            Base16 base16 = new Base16();
            encryptionKeySalt = base16.encodeToString(KeyGenerators.secureRandom(16).generateKey());
            settingsService.setEncryptionSalt(encryptionKeySalt);
            generatedKeys = true;
        }

        if (generatedKeys) {
            settingsService.save();
        }

        AesGcmPasswordEncoder encoder = new AesGcmPasswordEncoder(encryptionKeyPass, encryptionKeySalt);
        ENCODERS.put("encrypted-AES-GCM", encoder);
        ENCODERS.put("encrypted-AES-GCM" + SALT_TOKEN_MECHANISM_SPECIALIZATION, new SaltedTokenPasswordEncoder(encoder));

        DelegatingPasswordEncoder pEncoder = new DelegatingPasswordEncoder(settingsService.getNonDecodablePasswordEncoder(), ENCODERS) {
            @Override
            public boolean upgradeEncoding(String prefixEncodedPassword) {
                PasswordEncoder encoder = ENCODERS.get(StringUtils.substringBetween(prefixEncodedPassword, "{", "}"));
                if (encoder != null) {
                    return encoder.upgradeEncoding(StringUtils.substringAfter(prefixEncodedPassword, "}"));
                }

                return false;
            }
        };

        pEncoder.setDefaultPasswordEncoderForMatches(new PasswordEncoder() {
            @Override
            public boolean matches(CharSequence rawPassword, String encodedPassword) {
                return false;
            }

            @Override
            public String encode(CharSequence rawPassword) {
                return null;
            }
        });

        return pEncoder;
    }

    @EventListener
    public void loginFailureListener(AbstractAuthenticationFailureEvent event) {
        if (event.getSource() instanceof AbstractAuthenticationToken) {
            AbstractAuthenticationToken token = (AbstractAuthenticationToken) event.getSource();
            Object details = token.getDetails();
            if (details instanceof WebAuthenticationDetails) {
                LOG.info("Login failed from [{}]", ((WebAuthenticationDetails) details).getRemoteAddress());
            }
        }
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        if (settingsService.isLdapEnabled()) {
            auth.ldapAuthentication()
                    .contextSource()
                        .managerDn(settingsService.getLdapManagerDn())
                        .managerPassword(settingsService.getLdapManagerPassword())
                        .url(settingsService.getLdapUrl())
                    .and()
                    .userSearchFilter(settingsService.getLdapSearchFilter())
                    .userDetailsContextMapper(new CustomUserDetailsContextMapper())
                    .ldapAuthoritiesPopulator(new CustomLDAPAuthenticatorPostProcessor.CustomLDAPAuthoritiesPopulator())
                    .addObjectPostProcessor(new CustomLDAPAuthenticatorPostProcessor(securityService, settingsService));
        }
        String jwtKey = settingsService.getJWTKey();
        if (StringUtils.isBlank(jwtKey)) {
            LOG.warn("Generating new jwt key");
            jwtKey = JWTSecurityService.generateKey();
            settingsService.setJWTKey(jwtKey);
            settingsService.save();
        }
        JWTAuthenticationProvider jwtAuth = new JWTAuthenticationProvider(jwtKey);
        jwtAuth.addAdditionalCheck("/ws/Sonos", sonosJwtVerification);
        auth.authenticationProvider(jwtAuth);
        auth.authenticationProvider(multipleCredsProvider);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain extSecurityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {

        http = http.addFilter(new WebAsyncManagerIntegrationFilter());
        http = http.addFilterBefore(
            new JWTRequestParameterProcessingFilter(
                authenticationManager,
                FAILURE_URL)
            , UsernamePasswordAuthenticationFilter.class);

        http
                .securityMatcher("/ext/**")
                .csrf((csrf) -> csrf
                        .requireCsrfProtectionMatcher(csrfSecurityRequestMatcher))
                .headers(header -> header.frameOptions(fp -> fp.sameOrigin()))
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers("/ext/stream/**", "/ext/coverArt*", "/ext/share/**", "/ext/hls/**",
                                "/ext/captions**")
                        .hasAnyRole("TEMP", "USER")
                        .anyRequest().authenticated())
                .sessionManagement(manager -> manager.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                        .sessionFixation().none())
                .exceptionHandling(Customizer.withDefaults())
                .securityContext(Customizer.withDefaults())
                .requestCache(Customizer.withDefaults())
                .anonymous(Customizer.withDefaults())
                .servletApi(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {

        RESTRequestParameterProcessingFilter restAuthenticationFilter = new RESTRequestParameterProcessingFilter();
        restAuthenticationFilter.setAuthenticationManager(authenticationManager);

        // Try to load the 'remember me' key.
        //
        // Note that using a fixed key compromises security as perfect
        // forward secrecy is not guaranteed anymore.
        //
        // An external entity can then re-use our authentication cookies before
        // the expiration time, or even, given enough time, recover the password
        // from the MD5 hash.
        //
        // A null key means an ephemeral key is autogenerated
        String rememberMeKey = settingsService.getRememberMeKey();
        if (rememberMeKey != null) {
            LOG.info("Using a fixed 'remember me' key from properties, this is insecure.");
        }

        http
            .cors((cors) -> cors.configurationSource(corsConfigurationSource()))
            //.addFilterBefore(restAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .httpBasic(Customizer.withDefaults())
            .addFilterAfter(restAuthenticationFilter, BasicAuthenticationFilter.class)
            .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/Sonos/**").requireCsrfProtectionMatcher(csrfSecurityRequestMatcher))
            .headers(header -> header.frameOptions(fo -> fo.sameOrigin()))
            .authorizeHttpRequests((authorize) -> authorize.requestMatchers("/recover*", "/accessDenied*", "/style/**", "/icons/**", "/flash/**", "/script/**",
                    "/login", "/error", "/sonos/**", "/sonoslink/**", "/ws/Sonos/**").permitAll().requestMatchers("/personalSettings*",
                    "/playerSettings*", "/shareSettings*", "/credentialsSettings*").hasRole("SETTINGS")
                    .requestMatchers("/generalSettings*", "/advancedSettings*", "/userSettings*", "/musicFolderSettings*",
                            "/databaseSettings*", "/transcodeSettings*", "/rest/startScan*").hasRole("ADMIN")
                    .requestMatchers("/deletePlaylist*", "/savePlaylist*").hasRole("PLAYLIST").requestMatchers("/download*").hasRole("DOWNLOAD")
                    .requestMatchers("/upload*").hasRole("UPLOAD").requestMatchers("/createShare*").hasRole("SHARE")
                    .requestMatchers("/changeCoverArt*", "/editTags*", "/editMediaDir*").hasRole("COVERART").requestMatchers("/setMusicFileInfo*").hasRole("COMMENT")
                    .requestMatchers("/podcastReceiverAdmin*", "/podcastEpisodes*").hasRole("PODCAST")
                    .requestMatchers("/**").hasRole("USER").anyRequest().authenticated())
            .formLogin((login) -> login
                    .loginPage("/login")
                    .permitAll()
                    .defaultSuccessUrl("/index", true)
                    .failureUrl(FAILURE_URL)
                    .usernameParameter("j_username")
                    .passwordParameter("j_password"))
            .logout((logout) -> logout
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                .clearAuthentication(true)
                .invalidateHttpSession(true)
                .logoutSuccessUrl("/login?logout"))
            .rememberMe((rememberMe) -> rememberMe
                .key(rememberMeKey)
                .userDetailsService(securityService));
        return http.build();
    }

    /*
    @Bean(name = "mvcHandlerMappingIntrospector")
    public HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
        return new HandlerMappingIntrospector();
    }
    */

    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Collections.singletonList("*"));
        configuration.setAllowedMethods(Collections.singletonList("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/rest/**", configuration);
        source.registerCorsConfiguration("/stream/**", configuration);
        source.registerCorsConfiguration("/hls**", configuration);
        source.registerCorsConfiguration("/captions**", configuration);
        source.registerCorsConfiguration("/ext/stream/**", configuration);
        source.registerCorsConfiguration("/ext/hls**", configuration);
        source.registerCorsConfiguration("/ext/captions**", configuration);
        return source;
    }
}
