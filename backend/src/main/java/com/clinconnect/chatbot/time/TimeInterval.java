package com.clinconnect.chatbot.time;

import java.time.Instant;

/** A half-open executable interval [startAt, endAt) that a tool can query against. */
public record TimeInterval(Instant startAt, Instant endAt) {
}
