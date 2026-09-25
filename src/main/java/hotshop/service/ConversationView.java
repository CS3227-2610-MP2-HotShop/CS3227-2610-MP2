package hotshop.service;

import java.util.List;

import hotshop.model.Message;

/** An opened conversation: its summary and every message in the order sent. */
public record ConversationView(ConversationSummary summary, List<Message> messages) {
}
