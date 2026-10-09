import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One unit of elevator demand. Immutable.
 *
 * For a FLOOR_BUTTON request: `floor` = where the passenger waits,
 * `direction` = where they want to travel. The destination is unknown here —
 * it becomes a separate CAB_BUTTON request after boarding. This split is the
 * real-world subtlety interviewers test: "when do you actually know the
 * passenger's destination?" Answer: only at cab-button press time.
 *
 * For a CAB_BUTTON request: `floor` = destination, direction is derived from
 * (currentFloor -> destination) at press time purely for logging/ordering.
 */
public final class ElevatorRequest {

    private static final AtomicLong SEQUENCE = new AtomicLong(0);

    private final long id;
    private final RequestSource source;
    private final int floorNumber;
    private final Direction direction;

    private ElevatorRequest(RequestSource source, int floorNumber, Direction direction) {
        this.id = SEQUENCE.incrementAndGet();
        this.source = Objects.requireNonNull(source, "Request source cannot be null");
        this.floorNumber = floorNumber;
        this.direction = direction;
    }

    /** Hall call: someone at `floorNumber` wants to travel `direction`. */
    public static ElevatorRequest floorButton(int floorNumber, Direction direction) {
        if (floorNumber < 1) {
            throw new IllegalArgumentException("Floor number must be >= 1, got " + floorNumber);
        }
        if (direction == null || !direction.isMoving()) {
            throw new IllegalArgumentException(
                    "Hall call needs a travel direction (UP or DOWN), got " + direction);
        }
        return new ElevatorRequest(RequestSource.FLOOR_BUTTON, floorNumber, direction);
    }

    /** Car call: passenger inside the cab wants to reach `floorNumber`. */
    public static ElevatorRequest cabButton(int floorNumber) {
        if (floorNumber < 1) {
            throw new IllegalArgumentException("Floor number must be >= 1, got " + floorNumber);
        }
        // Direction is unknown until the car moves; stored as UP for bookkeeping
        // and never consulted for CAB_BUTTON scheduling decisions.
        return new ElevatorRequest(RequestSource.CAB_BUTTON, floorNumber, Direction.UP);
    }

    public long getId() {
        return id;
    }

    public RequestSource getSource() {
        return source;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    /** Only meaningful for FLOOR_BUTTON requests (hall calls). */
    public Direction getDirection() {
        return direction;
    }

    public boolean isHallCall() {
        return source == RequestSource.FLOOR_BUTTON;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ElevatorRequest)) {
            return false;
        }
        return this.id == ((ElevatorRequest) o).id;
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

    @Override
    public String toString() {
        if (source == RequestSource.FLOOR_BUTTON) {
            return "hall-call#" + id + "(floor " + floorNumber + ", " + direction + ")";
        }
        return "cab-call#" + id + "(to floor " + floorNumber + ")";
    }
}
