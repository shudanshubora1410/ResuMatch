package com.resumatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.util.concurrent.TimeUnit;

@Configuration
public class StaticStorageConfig implements WebMvcConfigurer {
    @Value("${resumatch.storage.path}")
    private String storagePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File f = new File(storagePath);
        if (!f.exists()) f.mkdirs();
        registry.addResourceHandler("/storage/**")
                .addResourceLocations("file:" + f.getAbsolutePath() + "/");
        // disable caching for static pages / css / js so updates are picked up immediately during dev
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noStore().mustRevalidate())
                .setCachePeriod(0);
    }
}
