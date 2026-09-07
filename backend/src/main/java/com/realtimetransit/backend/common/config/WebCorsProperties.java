package com.realtimetransit.backend.common.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("app.cors")
public class WebCorsProperties {

	private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
}
