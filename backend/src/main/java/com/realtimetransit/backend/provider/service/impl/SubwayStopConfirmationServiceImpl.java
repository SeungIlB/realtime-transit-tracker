package com.realtimetransit.backend.provider.service.impl;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.realtimetransit.backend.common.error.BusinessException;
import com.realtimetransit.backend.provider.client.dto.ExternalArrival;
import com.realtimetransit.backend.provider.client.TransitProviderProperties;
import com.realtimetransit.backend.provider.kric.client.KricRailwayTimetableClient;
import com.realtimetransit.backend.provider.kric.dto.KricStation;
import com.realtimetransit.backend.provider.kric.dto.KricTimetableCall;
import com.realtimetransit.backend.provider.service.SubwayStopConfirmationService;
import com.realtimetransit.backend.transit.entity.AlightingStopStatus;
import com.realtimetransit.backend.transit.entity.TransitLineEntity;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;
import com.realtimetransit.backend.transit.repository.TransitStopMapper;

import lombok.RequiredArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(TransitProviderProperties.class)
public class SubwayStopConfirmationServiceImpl implements SubwayStopConfirmationService {

	private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
	private static final Duration BOARDING_TIME_TOLERANCE = Duration.ofMinutes(45);
	private static final Duration MAX_TRIP_DURATION = Duration.ofHours(8);
	private static final DateTimeFormatter COMPACT_TIME = DateTimeFormatter.ofPattern("HHmm[ss]");

	private final KricRailwayTimetableClient timetableClient;
	private final TransitStopMapper transitStopMapper;
	private final TransitProviderProperties providerProperties;

	@Override
	public AlightingStopStatus confirmAlightingStop(
			TransitLineEntity line,
			TransitStopEntity boardingStop,
			TransitStopEntity alightingStop,
			ExternalArrival arrival) {
		if (alightingStop == null) return AlightingStopStatus.NOT_REQUESTED;
		AlightingStopStatus terminalStatus = confirmFromTerminal(
				line, boardingStop, alightingStop, arrival);
		if (terminalStatus != AlightingStopStatus.UNKNOWN) return terminalStatus;
		if (!timetableClient.isConfigured()) return fallbackStatus(arrival);
		if (arrival.getProviderVehicleId() == null || arrival.getExpectedAt() == null) {
			return fallbackStatus(arrival);
		}

		try {
			return confirmFromTimetable(line, boardingStop, alightingStop, arrival);
		} catch (BusinessException exception) {
			log.warn("KRIC timetable confirmation unavailable: {}", exception.getErrorCode());
			return fallbackStatus(arrival);
		}
	}

	private AlightingStopStatus confirmFromTerminal(
			TransitLineEntity line,
			TransitStopEntity boardingStop,
			TransitStopEntity alightingStop,
			ExternalArrival arrival) {
		String terminalProviderStopId = arrival.getDestinationProviderStopId();
		String providerDirectionId = arrival.getProviderDirectionId();
		if (terminalProviderStopId == null || providerDirectionId == null
				|| line.getId() == null || boardingStop.getId() == null || alightingStop.getId() == null) {
			return AlightingStopStatus.UNKNOWN;
		}
		return transitStopMapper.canReachAlightingBeforeTerminal(
				line.getId(), providerDirectionId, boardingStop.getId(), alightingStop.getId(), terminalProviderStopId)
				.map(reachable -> reachable
						? localStopStatus(arrival)
						: AlightingStopStatus.SKIPS)
				.orElse(AlightingStopStatus.UNKNOWN);
	}

	private static AlightingStopStatus localStopStatus(ExternalArrival arrival) {
		return "LOCAL".equals(arrival.getServiceType())
				? AlightingStopStatus.STOPS
				: AlightingStopStatus.UNKNOWN;
	}

	private AlightingStopStatus confirmFromTimetable(
			TransitLineEntity line,
			TransitStopEntity boardingStop,
			TransitStopEntity alightingStop,
			ExternalArrival arrival) {
		List<KricStation> boardingStations = timetableClient.findStations(boardingStop.getPublicName());
		List<KricStation> alightingStations = timetableClient.findStations(alightingStop.getPublicName());
		List<StationPair> stationPairs = stationPairs(line, boardingStations, alightingStations);
		if (stationPairs.isEmpty()) return fallbackStatus(arrival);

		boolean boardingRunFound = false;
		boolean destinationDataIncomplete = false;
		boolean skippingRunFound = false;
		for (int dayCode : dayCodes(arrival.getExpectedAt())) {
			for (StationPair pair : stationPairs) {
				List<KricTimetableCall> boardingCalls = timetableClient.findTimetable(pair.getBoarding(), dayCode);
				KricTimetableCall boardingCall = closestBoardingCall(
						boardingCalls, arrival.getProviderVehicleId(), arrival.getExpectedAt());
				if (boardingCall == null) continue;
				boardingRunFound = true;

				List<KricTimetableCall> alightingCalls = timetableClient.findTimetable(pair.getAlighting(), dayCode);
				if (alightingCalls.isEmpty()) {
					destinationDataIncomplete = true;
					continue;
				}
				if (stopsAfterBoarding(boardingCall, alightingCalls, arrival.getProviderVehicleId(), arrival.getExpectedAt())) {
					return AlightingStopStatus.STOPS;
				}
				skippingRunFound = true;
			}
		}
		if (boardingRunFound && skippingRunFound && !destinationDataIncomplete) {
			return AlightingStopStatus.SKIPS;
		}
		return fallbackStatus(arrival);
	}

	private static List<StationPair> stationPairs(
			TransitLineEntity line,
			List<KricStation> boardingStations,
			List<KricStation> alightingStations) {
		List<StationPair> pairs = new ArrayList<>();
		for (KricStation boarding : boardingStations) {
			for (KricStation alighting : alightingStations) {
				if (sameRunScope(boarding, alighting)) pairs.add(new StationPair(boarding, alighting));
			}
		}
		String expectedNumericLine = numericLineCode(line.getProviderLineId());
		if (expectedNumericLine == null) return pairs;
		List<StationPair> exact = pairs.stream()
				.filter(pair -> expectedNumericLine.equals(stripLeadingZero(pair.getBoarding().getLineCode())))
				.toList();
		return exact.isEmpty() ? pairs : exact;
	}

	private static KricTimetableCall closestBoardingCall(
			List<KricTimetableCall> calls,
			String trainNumber,
			Instant expectedAt) {
		return calls.stream()
				.filter(call -> sameTrainNumber(call.getTrainNumber(), trainNumber))
				.filter(call -> scheduledInstant(call, expectedAt) != null)
				.min(Comparator.comparing(call -> absoluteDuration(scheduledInstant(call, expectedAt), expectedAt)))
				.filter(call -> absoluteDuration(scheduledInstant(call, expectedAt), expectedAt)
						.compareTo(BOARDING_TIME_TOLERANCE) <= 0)
				.orElse(null);
	}

	private static boolean stopsAfterBoarding(
			KricTimetableCall boardingCall,
			List<KricTimetableCall> alightingCalls,
			String trainNumber,
			Instant boardingExpectedAt) {
		Instant boardingTime = scheduledInstant(boardingCall, boardingExpectedAt);
		if (boardingTime == null) return false;
		return alightingCalls.stream()
				.filter(call -> sameTrainNumber(call.getTrainNumber(), trainNumber))
				.map(call -> scheduledInstant(call, boardingExpectedAt))
				.filter(java.util.Objects::nonNull)
				.anyMatch(time -> time.isAfter(boardingTime)
						&& Duration.between(boardingTime, time).compareTo(MAX_TRIP_DURATION) <= 0);
	}

	static Instant scheduledInstant(KricTimetableCall call, Instant reference) {
		String value = firstNonBlank(call.getArrivalTime(), call.getDepartureTime());
		if (value == null) return null;
		Integer seconds = parseSeconds(value);
		if (seconds == null) return null;
		LocalDate date = reference.atZone(KOREA_ZONE).toLocalDate();
		Instant sameDate = date.atStartOfDay(KOREA_ZONE).plusSeconds(seconds).toInstant();
		return List.of(sameDate.minus(Duration.ofDays(1)), sameDate, sameDate.plus(Duration.ofDays(1))).stream()
				.min(Comparator.comparing(candidate -> absoluteDuration(candidate, reference)))
				.orElse(null);
	}

	private static Integer parseSeconds(String value) {
		String normalized = value.strip();
		try {
			if (normalized.contains(":")) {
				String[] parts = normalized.split(":");
				if (parts.length < 2 || parts.length > 3) return null;
				int hours = Integer.parseInt(parts[0]);
				int minutes = Integer.parseInt(parts[1]);
				int seconds = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
				if (hours < 0 || minutes >= 60 || seconds >= 60) return null;
				return hours * 3600 + minutes * 60 + seconds;
			}
			if (normalized.length() == 4) normalized += "00";
			if (normalized.length() == 6) {
				int hours = Integer.parseInt(normalized.substring(0, 2));
				int minutes = Integer.parseInt(normalized.substring(2, 4));
				int seconds = Integer.parseInt(normalized.substring(4, 6));
				if (hours >= 24 && minutes < 60 && seconds < 60) return hours * 3600 + minutes * 60 + seconds;
				return LocalTime.parse(normalized, COMPACT_TIME).toSecondOfDay();
			}
		} catch (DateTimeParseException | NumberFormatException ignored) {
			return null;
		}
		return null;
	}

	private List<Integer> dayCodes(Instant expectedAt) {
		LocalDate serviceDate = expectedAt.atZone(KOREA_ZONE).toLocalDate();
		if (providerProperties.getRailwayTimetable().getHolidayDates().contains(serviceDate)) {
			return List.of(9);
		}
		DayOfWeek day = serviceDate.getDayOfWeek();
		int primary = switch (day) {
			case SATURDAY -> 7;
			case SUNDAY -> 9;
			default -> 8;
		};
		return List.of(primary);
	}

	private static AlightingStopStatus fallbackStatus(ExternalArrival arrival) {
		return AlightingStopStatus.UNKNOWN;
	}

	private static boolean sameRunScope(KricStation first, KricStation second) {
		return java.util.Objects.equals(first.getOperatorCode(), second.getOperatorCode())
				&& java.util.Objects.equals(first.getLineCode(), second.getLineCode());
	}

	private static boolean sameTrainNumber(String first, String second) {
		return normalizeTrainNumber(first).equals(normalizeTrainNumber(second));
	}

	private static String normalizeTrainNumber(String value) {
		if (value == null) return "";
		String normalized = value.strip().replaceFirst("^0+(?!$)", "");
		return normalized.toUpperCase(java.util.Locale.ROOT);
	}

	private static String numericLineCode(String providerLineId) {
		if (providerLineId == null || !providerLineId.matches("0?[1-9]호선")) return null;
		return stripLeadingZero(providerLineId.replace("호선", ""));
	}

	private static String stripLeadingZero(String value) {
		return value == null ? "" : value.replaceFirst("^0+(?!$)", "");
	}

	private static Duration absoluteDuration(Instant first, Instant second) {
		return Duration.between(first, second).abs();
	}

	private static String firstNonBlank(String first, String second) {
		return first != null && !first.isBlank() ? first : second != null && !second.isBlank() ? second : null;
	}

	@Getter
	@AllArgsConstructor
	private static class StationPair {
		private final KricStation boarding;
		private final KricStation alighting;
	}
}
