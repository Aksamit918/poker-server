package com.poker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(request -> {
                    var opt = new CorsConfiguration();
                    opt.setAllowedOriginPatterns(List.of("*"));
                    opt.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    opt.setAllowedHeaders(List.of("*"));
                    opt.setAllowCredentials(true);
                    return opt;
                }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws-poker/**").permitAll()
                        .anyRequest().permitAll()
                );
        return http.build();
    }
}