package com.realtimetransit.backend.common.cache;

import java.time.Duration;
import java.util.Map;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;


@Configuration
@EnableCaching
public class RedisCacheConfig {

	@Bean
	RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
		var cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(
				connectionFactory,
				BatchStrategies.scan(1_000));
		var defaultConfig = configuration(Duration.ofMinutes(5));

		return RedisCacheManager.builder(cacheWriter)
				.cacheDefaults(defaultConfig)
				.withInitialCacheConfigurations(Map.of(
						TransitCacheNames.GBIS_VEHICLE_LOCATIONS, configuration(Duration.ofSeconds(15)),
						TransitCacheNames.GBIS_ARRIVALS, configuration(Duration.ofSeconds(15)),
						TransitCacheNames.SEOUL_SUBWAY_ARRIVALS, configuration(Duration.ofSeconds(15)),
						TransitCacheNames.NATIONAL_BUS_LOCATIONS, configuration(Duration.ofSeconds(15)),
						TransitCacheNames.RAILWAY_TIMETABLE, configuration(Duration.ofHours(6)),
						TransitCacheNames.KAKAO_PUBLIC_TRANSIT, configuration(Duration.ofMinutes(10)),
						TransitCacheNames.TRANSIT_STATIC_DATA, configuration(Duration.ofHours(24))))
				.enableStatistics()
				.build();
	}

	private RedisCacheConfiguration configuration(Duration ttl) {
		return RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(ttl)
				.disableCachingNullValues()
				.prefixCacheNameWith("rtt:v3:cache:")
				.serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer()));
	}

	static RedisSerializer<Object> jsonSerializer() {
		var typeValidator = BasicPolymorphicTypeValidator.builder()
				.allowIfSubType("com.realtimetransit.backend.")
				.allowIfSubType("java.math.")
				.allowIfSubType("java.time.")
				.allowIfSubType("java.util.")
				.allowIfSubType("tools.jackson.databind.node.")
				.allowIfSubTypeIsArray()
				.build();
		return GenericJacksonJsonRedisSerializer.builder()
				.enableDefaultTyping(typeValidator)
				.writer((mapper, value) -> mapper.writerFor(Object.class).writeValueAsBytes(value))
				.build();
	}
}
