package hotshop.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import hotshop.database.Database;
import hotshop.model.Conversation;
import hotshop.model.Listing;
import hotshop.model.Message;
import hotshop.model.Offer;
import hotshop.model.OfferStatus;
import hotshop.repository.ChatRepository;
import hotshop.repository.ListingRepository;
import hotshop.repository.OfferRepository;
import hotshop.repository.TransactionRepository;
import hotshop.repository.UserRepository;

/**
 * Conversations between a buyer and a seller about one listing, serialized with all other
 * application services. Every operation requires login and acts as the session's current user.
 * Only the buyer starts a conversation, by messaging the seller or by making an offer; only the
 * buyer and seller can read it. Returned futures fail with ServiceException.
 */
public final class ChatService {
    private static final String STORAGE_FAILURE = "Conversations are unavailable right now. Please try again.";
    /** Conversations with a pending offer or an active sale come before general enquiries. */
    private static final Comparator<ConversationSummary> LIST_ORDER = Comparator
            .comparing((ConversationSummary summary) -> !isAboutOfferOrSale(summary))
            .thenComparing(summary -> summary.unreadCount() == 0)
            .thenComparing(ConversationSummary::lastActivityAt, Comparator.reverseOrder())
            .thenComparing(summary -> summary.conversation().getId());
    private final Database database;
    private final ChatRepository chats;
    private final ListingRepository listings;
    private final OfferRepository offers;
    private final TransactionRepository transactions;
    private final UserRepository users;
    private final ServiceWorker worker;
    private final AuthenticatedSession session;
    private final Clock clock;

    /** Wires the shared database, worker, and session with the repositories conversations draw on. */
    public ChatService(Database database, ChatRepository chats, ListingRepository listings, OfferRepository offers,
            TransactionRepository transactions, UserRepository users, ServiceWorker worker,
            AuthenticatedSession session, Clock clock) {
        this.database = database;
        this.chats = chats;
        this.listings = listings;
        this.offers = offers;
        this.transactions = transactions;
        this.users = users;
        this.worker = worker;
        this.session = session;
        this.clock = clock;
    }

    /**
     * The buyer's "Chat with seller": sends a message about another seller's available or reserved
     * listing, starting the conversation if this is the buyer's first message or offer on it.
     */
    public CompletableFuture<ConversationView> messageSeller(UUID listingId, String text) {
        return worker.submit(() -> {
            UUID buyerId = session.requireUserId();
            requireId(listingId, "Choose a listing to ask about.");
            String message = Conversations.requireText(text);
            return transaction(connection -> {
                Listing listing = requireListing(connection, listingId);
                requireOtherSeller(listing, buyerId);
                requireOpen(listing);
                Instant now = ServiceSupport.now(clock);
                Conversation conversation = Conversations.findOrStart(connection, chats, listing, buyerId, now);
                Conversations.append(connection, chats, conversation, buyerId, message, now);
                return view(connection, conversation, buyerId);
            });
        });
    }

    /** Either participant replies in an existing conversation while its listing is available or reserved. */
    public CompletableFuture<ConversationView> sendMessage(UUID conversationId, String text) {
        return worker.submit(() -> {
            UUID userId = session.requireUserId();
            requireId(conversationId, "Choose a conversation first.");
            String message = Conversations.requireText(text);
            return transaction(connection -> {
                Conversation conversation = requireParticipant(connection, conversationId, userId);
                requireOpen(requireListing(connection, conversation.getListingId()));
                Conversations.append(connection, chats, conversation, userId, message, ServiceSupport.now(clock));
                return view(connection, conversation, userId);
            });
        });
    }

    /** Opens one of the current user's conversations and marks everything in it as read. */
    public CompletableFuture<ConversationView> openConversation(UUID conversationId) {
        return worker.submit(() -> {
            UUID userId = session.requireUserId();
            requireId(conversationId, "Choose a conversation first.");
            return transaction(connection -> open(connection,
                    requireParticipant(connection, conversationId, userId), userId));
        });
    }

    /**
     * The buyer's existing conversation about a listing, opened and marked read, or empty when they
     * have not started one; the screen then shows an empty chat until the first message is sent.
     */
    public CompletableFuture<Optional<ConversationView>> openChatWithSeller(UUID listingId) {
        return worker.submit(() -> {
            UUID buyerId = session.requireUserId();
            requireId(listingId, "Choose a listing to ask about.");
            return transaction(connection -> {
                Listing listing = requireListing(connection, listingId);
                requireOtherSeller(listing, buyerId);
                var existing = chats.findConversation(connection, listingId, buyerId);
                if (existing.isEmpty()) {
                    requireOpen(listing);
                    return Optional.<ConversationView>empty();
                }
                return Optional.of(open(connection, existing.orElseThrow(), buyerId));
            });
        });
    }

    /**
     * Seller only: opens a buyer's existing conversation about one of the seller's listings, for
     * example from an incoming offer or a sale. Sellers never start conversations.
     */
    public CompletableFuture<ConversationView> openChatWithBuyer(UUID listingId, UUID buyerId) {
        return worker.submit(() -> {
            UUID sellerId = session.requireUserId();
            requireId(listingId, "Choose a listing first.");
            requireId(buyerId, "Choose a buyer first.");
            return transaction(connection -> {
                Listing listing = requireListing(connection, listingId);
                if (!listing.getSellerId().equals(sellerId)) {
                    throw ServiceException.permission("Only the seller can open conversations with buyers "
                            + "about this listing.");
                }
                Conversation conversation = chats.findConversation(connection, listingId, buyerId)
                        .orElseThrow(() -> ServiceException.notFound(
                                "This buyer hasn't started a conversation about this listing yet."));
                return open(connection, conversation, sellerId);
            });
        });
    }

    /**
     * Every conversation the current user takes part in, as buyer or seller: those with a pending
     * offer or an active sale first, then the rest; within each, unread ones first, then by latest
     * activity (a message, an offer made, or an offer closed), newest first.
     */
    public CompletableFuture<List<ConversationSummary>> getConversations() {
        return worker.submit(() -> {
            UUID userId = session.requireUserId();
            return transaction(connection -> summaries(connection, userId));
        });
    }

    /** Unread messages and offer events across all the current user's conversations, for the sidebar. */
    public CompletableFuture<Integer> getUnreadCount() {
        return worker.submit(() -> {
            UUID userId = session.requireUserId();
            return transaction(connection -> summaries(connection, userId).stream()
                    .mapToInt(ConversationSummary::unreadCount).sum());
        });
    }

    private List<ConversationSummary> summaries(Connection connection, UUID userId) throws SQLException {
        List<ConversationSummary> result = new ArrayList<>();
        for (Conversation conversation : chats.findConversationsFor(connection, userId)) {
            result.add(summary(connection, conversation, userId));
        }
        result.sort(LIST_ORDER);
        return result;
    }

    private ConversationView open(Connection connection, Conversation conversation, UUID userId)
            throws SQLException {
        long lastSequence = chats.findLatestMessage(connection, conversation.getId())
                .map(Message::sequence).orElse(0L);
        Instant time = ServiceSupport.now(clock);
        conversation.markRead(userId, lastSequence, ServiceSupport.latest(time, conversation.getCreatedAt()));
        chats.updateReadState(connection, conversation);
        return view(connection, conversation, userId);
    }

    private ConversationView view(Connection connection, Conversation conversation, UUID userId)
            throws SQLException {
        return new ConversationView(summary(connection, conversation, userId),
                chats.findMessages(connection, conversation.getId()));
    }

    private ConversationSummary summary(Connection connection, Conversation conversation, UUID userId)
            throws SQLException {
        Listing listing = requireListing(connection, conversation.getListingId());
        List<Offer> buyerOffers = offers.findByListingAndBuyer(connection, listing.getId(),
                conversation.getBuyerId());
        Optional<Message> latestMessage = chats.findLatestMessage(connection, conversation.getId());
        boolean isBuyer = conversation.getBuyerId().equals(userId);
        int unread = chats.countUnreadMessages(connection, conversation.getId(), userId,
                conversation.getReadSequence(userId))
                + unreadOfferEvents(buyerOffers, isBuyer, conversation.getLastOpenedAt(userId).orElse(null));
        Instant offerActivity = buyerOffers.stream().map(ChatService::latestEvent)
                .max(Comparator.naturalOrder()).orElse(conversation.getCreatedAt());
        Instant lastActivity = ServiceSupport.latest(offerActivity,
                latestMessage.map(Message::sentAt).orElse(conversation.getCreatedAt()));
        return new ConversationSummary(conversation, listing,
                ServiceSupport.publicProfile(connection, users, conversation.getOtherParticipant(userId)),
                isBuyer ? SaleRole.BUYER : SaleRole.SELLER, latestOffer(buyerOffers),
                activeSale(connection, conversation), unread, preview(latestMessage, buyerOffers), lastActivity,
                Conversation.isOpenFor(listing));
    }

    /**
     * The buyer's pending offer if they have one, otherwise their newest. A pending offer is always
     * the newest, but a withdrawal and a new offer in the same millisecond would otherwise tie.
     */
    private static Optional<Offer> latestOffer(List<Offer> buyerOffers) {
        return buyerOffers.stream().filter(offer -> offer.getStatus() == OfferStatus.PENDING).findFirst()
                .or(() -> buyerOffers.stream().findFirst());
    }

    /**
     * Offer events the viewer did not cause, since they last opened the conversation; each offer
     * counts at most once. The seller sees new and withdrawn offers; the buyer sees acceptances and
     * rejections, including automatic ones. The seller made those decisions, so they are not news
     * to them.
     */
    private static int unreadOfferEvents(List<Offer> buyerOffers, boolean isBuyer, Instant lastOpened) {
        int count = 0;
        for (Offer offer : buyerOffers) {
            Instant event = switch (offer.getStatus()) {
                case PENDING -> isBuyer ? null : offer.getCreatedAt();
                case WITHDRAWN -> isBuyer ? null : offer.getClosedAt().orElseThrow();
                case ACCEPTED, REJECTED -> isBuyer ? offer.getClosedAt().orElseThrow() : null;
            };
            if (event != null && (lastOpened == null || event.isAfter(lastOpened))) {
                count++;
            }
        }
        return count;
    }

    /** The newer of the latest message and the latest offer event; a message wins a tie. */
    private static String preview(Optional<Message> latestMessage, List<Offer> buyerOffers) {
        Optional<Offer> latestOffer = buyerOffers.stream().max(Comparator.comparing(ChatService::latestEvent));
        if (latestOffer.isEmpty() || latestMessage.isPresent()
                && !latestEvent(latestOffer.orElseThrow()).isAfter(latestMessage.orElseThrow().sentAt())) {
            return latestMessage.map(Message::text).orElse("");
        }
        Offer offer = latestOffer.orElseThrow();
        String outcome = offer.getStatus() == OfferStatus.PENDING ? "made" : ServiceSupport.describe(offer.getStatus());
        return "Offer of " + ServiceSupport.formatPrice(offer.getAmountCents()) + " " + outcome;
    }

    private static Instant latestEvent(Offer offer) {
        return offer.getClosedAt().orElse(offer.getCreatedAt());
    }

    /** The active sale between these two on this listing; another buyer's sale does not count. */
    private Optional<UUID> activeSale(Connection connection, Conversation conversation) throws SQLException {
        var saleId = transactions.findActiveIdForListing(connection, conversation.getListingId());
        if (saleId.isEmpty()) {
            return Optional.empty();
        }
        var sale = transactions.findById(connection, saleId.orElseThrow());
        return sale.filter(found -> found.getBuyerId().equals(conversation.getBuyerId())).map(found -> found.getId());
    }

    private static boolean isAboutOfferOrSale(ConversationSummary summary) {
        return summary.activeSaleId().isPresent() || summary.latestOffer()
                .filter(offer -> offer.getStatus() == OfferStatus.PENDING).isPresent();
    }

    /** Sold and archived listings keep their conversations readable, but nobody can add to them. */
    private static void requireOpen(Listing listing) {
        if (!Conversation.isOpenFor(listing)) {
            throw ServiceException.invalidState("This listing is " + ServiceSupport.describe(listing.getStatus())
                    + ", so no new messages can be sent about it.");
        }
    }

    private static void requireOtherSeller(Listing listing, UUID buyerId) {
        if (listing.getSellerId().equals(buyerId)) {
            throw ServiceException.permission("This is your own listing, so there's no seller to message.");
        }
    }

    private Conversation requireParticipant(Connection connection, UUID conversationId, UUID userId)
            throws SQLException {
        Conversation conversation = chats.findConversation(connection, conversationId)
                .orElseThrow(() -> ServiceException.notFound("This conversation no longer exists."));
        if (!conversation.isParticipant(userId)) {
            throw ServiceException.permission("Only the buyer and seller can take part in this conversation.");
        }
        return conversation;
    }

    private Listing requireListing(Connection connection, UUID id) throws SQLException {
        return listings.findById(connection, id)
                .orElseThrow(() -> ServiceException.notFound("This listing no longer exists."));
    }

    private static void requireId(UUID id, String message) {
        if (id == null) {
            throw ServiceException.validation(message);
        }
    }

    private <T> T transaction(Database.Work<T> work) {
        return ServiceSupport.transaction(database, STORAGE_FAILURE, work);
    }
}
