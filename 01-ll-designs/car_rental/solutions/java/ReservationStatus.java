/** Reservation lifecycle: SCHEDULED -> IN_PROGRESS -> COMPLETED, or SCHEDULED -> CANCELLED. */
public enum ReservationStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
