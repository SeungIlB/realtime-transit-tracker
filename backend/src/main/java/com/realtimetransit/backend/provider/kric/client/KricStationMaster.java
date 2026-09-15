package com.realtimetransit.backend.provider.kric.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.realtimetransit.backend.provider.kric.dto.KricStation;

@Component
public class KricStationMaster {

	private static final String RESOURCE_PATH = "data/kric-station-master-2026-07-11.csv";

	private final Map<String, List<KricStation>> stationsByLineCode;

	public KricStationMaster() {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
				new ClassPathResource(RESOURCE_PATH).getInputStream(), StandardCharsets.UTF_8))) {
			stationsByLineCode = reader.lines()
					.skip(1)
					.filter(line -> !line.isBlank())
					.map(KricStationMaster::parse)
					.collect(Collectors.groupingBy(
							KricStation::getLineCode,
							java.util.LinkedHashMap::new,
							Collectors.toUnmodifiableList()));
		} catch (IOException exception) {
			throw new IllegalStateException("KRIC station master could not be loaded", exception);
		}
	}

	public List<KricStation> findByLineCode(String lineCode) {
		return stationsByLineCode.getOrDefault(lineCode, List.of());
	}

	private static KricStation parse(String line) {
		String[] values = line.split(",", -1);
		if (values.length != 4 || java.util.Arrays.stream(values).anyMatch(String::isBlank)) {
			throw new IllegalStateException("Invalid KRIC station master row");
		}
		return KricStation.builder()
				.operatorCode(values[0].strip())
				.lineCode(values[1].strip())
				.stationCode(values[2].strip())
				.stationName(values[3].strip())
				.build();
	}
}
