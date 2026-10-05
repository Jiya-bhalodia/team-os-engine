package com.placement.teama.model.enums;

public enum RequestState {
    QUEUED, PROCESSING, EVALUATED, SLOT_ALLOCATION_PENDING, ALLOCATED,
    NOT_ELIGIBLE, FAILED, CANCELLED, TIMEOUT
}
