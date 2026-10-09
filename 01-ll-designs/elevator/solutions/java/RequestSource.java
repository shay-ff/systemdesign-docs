/**
 * Where a request came from. This distinction is a favourite interview probe:
 *
 * - FLOOR_BUTTON (a.k.a. hall call): someone on floor N pressed Up/Down.
 *   We know the pickup floor and the TRAVEL DIRECTION, but NOT the destination
 *   — the destination is only revealed when the passenger boards and presses
 *   a cab button.
 * - CAB_BUTTON (a.k.a. car call): someone INSIDE the cab pressed "take me to
 *   floor N". We know the destination but the car is already committed to
 *   that passenger.
 *
 * Scheduling algorithms differ because of this: FCFS treats both as opaque
 * work items; SCAN needs the direction intent of hall calls to decide whether
 * a moving cab can stop for an intermediate pickup (it only should if the
 * pickup direction matches the sweep direction).
 */
public enum RequestSource {
    FLOOR_BUTTON,
    CAB_BUTTON
}
