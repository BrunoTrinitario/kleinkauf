package com.kleinkauf.users.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.time.Duration;

import org.springframework.http.CacheControl;

// Profile photos: public files under avatar.path. A new photo gets a new name, so they can be cached for long
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${avatar.path}")
    private String avatarPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // The location needs a trailing slash, and toUri only adds it when the folder already exists
        String location = Paths.get(avatarPath).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/users/avatars/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
