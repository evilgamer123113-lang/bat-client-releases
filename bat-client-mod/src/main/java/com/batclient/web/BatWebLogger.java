package com.batclient.web;

import io.github.nilsen84.ultralight.UltralightLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bridges Ultralight's internal logger to the mod's SLF4J logger. */
public class BatWebLogger implements UltralightLogger {
	private static final Logger LOGGER = LoggerFactory.getLogger("BatClient-Ultralight");

	@Override
	public void log(Level level, String message) {
		// Message can be empty; Ultralight sends every event through log().
		switch (level) {
			case Error -> LOGGER.error("[ultralight] {}", message);
			case Warning -> LOGGER.warn("[ultralight] {}", message);
			case Info -> LOGGER.info("[ultralight] {}", message);
		}
	}
}