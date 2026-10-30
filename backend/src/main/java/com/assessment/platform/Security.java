package com.assessment.platform;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class Security {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  UserDetailsService users(Store db) {
    return email -> {
      var rows =
          db.jdbc.queryForList(
              "select email,password_hash from users where email=?",
              email.toLowerCase(java.util.Locale.ROOT));
      if (rows.isEmpty()) throw new UsernameNotFoundException("Invalid credentials");
      var row = rows.getFirst();
      return User.withUsername((String) row.get("email"))
          .password((String) row.get("password_hash"))
          .roles("USER")
          .build();
    };
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        "/api/v1/csrf", "/api/v1/register", "/api/v1/verify", "/actuator/health")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginProcessingUrl("/api/v1/login")
                    .successHandler(
                        (req, res, auth) -> {
                          res.setContentType("application/json");
                          res.getWriter().write("{\"status\":\"AUTHENTICATED\"}");
                        })
                    .failureHandler((req, res, e) -> res.sendError(401, "Invalid credentials")))
        .logout(
            l ->
                l.logoutUrl("/api/v1/logout")
                    .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((req, res, ex) -> res.sendError(401))
                    .accessDeniedHandler((req, res, ex) -> res.sendError(403)))
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
        .build();
  }
}
