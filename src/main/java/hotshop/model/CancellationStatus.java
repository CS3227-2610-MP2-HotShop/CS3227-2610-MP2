package hotshop.model;

/** Outcome of a cancellation proposal; only PENDING requests can be resolved. */
public enum CancellationStatus {
    PENDING, ACCEPTED, REJECTED, WITHDRAWN
}
