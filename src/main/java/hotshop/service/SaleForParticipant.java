package hotshop.service;

import java.util.Set;

import hotshop.model.Transaction;

/**
 * One sale as seen by one participant: the sale with its listing snapshot and history, the other
 * participant's public profile, the viewer's role, next step, currently available actions, and
 * the sale's meetup summary.
 * The sale is a detached copy.
 */
public record SaleForParticipant(Transaction sale, PublicProfile otherParticipant, SaleRole role,
        NextStep nextStep, Set<SaleAction> availableActions, MeetupSummary meetup) {
}
