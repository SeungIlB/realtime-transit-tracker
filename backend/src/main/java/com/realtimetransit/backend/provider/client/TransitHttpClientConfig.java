package com.realtimetransit.backend.provider.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class TransitHttpClientConfig {
	@Bean
	RestClient.Builder transitRestClientBuilder() {
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
		requestFactory.setReadTimeout(java.time.Duration.ofSeconds(10));
		return RestClient.builder().requestFactory(requestFactory);
	}
}
