package hotshop.service;

/**
 * A seller's summary: pending offers across their listings, active and completed sale counts, and
 * the total agreed value of completed sales in SGD cents. Active sales are not totalled because
 * they can still be cancelled. Upcoming meetups are scheduled meetups that have not started.
 */
public record SalesDashboard(int pendingOffers, int activeSales, int completedSales, long totalSalesValueCents,
        int upcomingMeetups) {
}
