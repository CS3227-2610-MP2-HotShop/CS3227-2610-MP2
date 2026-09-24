package hotshop.ui;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.List;

import hotshop.model.Category;
import hotshop.model.Condition;
import hotshop.model.Listing;
import hotshop.model.ListingDetails;
import hotshop.model.OfferStatus;
import hotshop.service.ListingDraft;
import hotshop.service.ListingPhoto;
import hotshop.storage.ImageStorage;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;

/** One atomic listing form with an ordered photo selection; imported files remain service-owned. */
final class ListingEditor {
    private final MarketplaceUi app;
    private final UiPage page;
    private final Listing original;
    private final UiForm form = new UiForm();
    private final ComboBox<Category> category = SearchPage.choices(Category.values());
    private final ComboBox<Condition> condition = SearchPage.choices(Condition.values());
    private final List<ListingPhoto> photos = new ArrayList<>();
    private final VBox photoRows = new VBox(10);
    private List<String> initialFields;

    ListingEditor(MarketplaceUi app, Listing original) {
        this.app = app;
        this.original = original;
        page = app.page(original == null ? "Create Listing" : "Edit Listing");
        if (original == null) {
            page.load(app.runtime.getAccounts()::getOwnProfile,
                    user -> build(user.getPreferredPickupLocation().orElse("")));
        } else {
            original.getImages().forEach(image -> photos.add(ListingPhoto.keep(image.filename())));
            build(original.getDetails().pickupLocation());
        }
    }

    private void build(String pickup) {
        ListingDetails details = original == null ? null : original.getDetails();
        form.text("title", "Title", details == null ? "" : details.title());
        form.area("description", "Description", details == null ? "" : details.description());
        form.text("price", "Asking price (SGD)", details == null ? ""
                : BigDecimal.valueOf(details.priceCents(), 2).toPlainString());
        category.setId("category");
        category.setValue(details == null ? null : details.category());
        condition.setId("condition");
        condition.setValue(details == null ? null : details.condition());
        form.field("category", "Category", category);
        form.field("condition", "Condition", condition);
        form.text("pickup-location", "Listing pickup location", pickup);
        form.getChildren().addAll(UiControls.label(
                "Up to 10 JPEG/PNG photos; each at most 10 MiB and 4096 × 4096 pixels. No automatic resizing.", "hint"),
                photoRows, UiControls.button("Add Photo", "add-photo", this::addPhoto),
                UiControls.actions(UiControls.primary("Save Listing", "save-listing", this::save),
                        UiControls.button("Cancel", "cancel-edit", app::back)));
        page.body.getChildren().add(form);
        initialFields = fields();
        page.setDirty(() -> !initialFields.equals(fields()) || !originalPhotos().equals(photos));
        renderPhotos();
    }

    private List<ListingPhoto> originalPhotos() {
        return original == null ? List.of() : original.getImages().stream()
                .map(image -> ListingPhoto.keep(image.filename())).toList();
    }

    private List<String> fields() {
        return List.of(form.value("title"), form.value("description"), form.value("price"),
                String.valueOf(category.getValue()), String.valueOf(condition.getValue()),
                form.value("pickup-location"));
    }

    private void addPhoto() {
        if (photos.size() == Listing.MAX_IMAGES) {
            page.error(new IllegalArgumentException("A listing can have at most 10 photos."));
            return;
        }
        Path selected = UiImages.choose(app.stage);
        if (selected != null) {
            page.perform(() -> CompletableFuture.supplyAsync(() -> {
                try {
                    ImageStorage.validateImage(selected, ImageStorage.LISTING_LIMITS);
                    return selected;
                } catch (IOException failure) {
                    throw new CompletionException(failure);
                }
            }), valid -> {
                photos.add(ListingPhoto.add(valid));
                renderPhotos();
            });
        }
    }

    private void renderPhotos() {
        photoRows.getChildren().clear();
        for (int index = 0; index < photos.size(); index++) {
            int position = index;
            ListingPhoto photo = photos.get(index);
            Path source = switch (photo) {
                case ListingPhoto.Existing existing -> app.runtime.getListingImagePath(existing.filename());
                case ListingPhoto.NewFile added -> added.source();
            };
            var up = UiControls.button("Move Up", "photo-up-" + index, () -> move(position, -1));
            up.setDisable(index == 0);
            var down = UiControls.button("Move Down", "photo-down-" + index, () -> move(position, 1));
            down.setDisable(index == photos.size() - 1);
            photoRows.getChildren().add(UiControls.actions(UiImages.display(() -> source, 100, 75),
                    UiControls.label("Photo " + (index + 1), "muted"), up, down,
                    UiControls.button("Remove", "photo-remove-" + index, () -> {
                        photos.remove(position);
                        renderPhotos();
                    })));
        }
    }

    private void move(int position, int change) {
        Collections.swap(photos, position, position + change);
        renderPhotos();
    }

    private void save() {
        form.clearErrors();
        form.textLength("title", 120, true);
        form.textLength("description", 5000, true);
        form.textLength("pickup-location", 200, true);
        Long price = form.cents("price", false);
        if (price != null && (price < 1 || price > ListingDetails.MAX_PRICE_CENTS)) {
            form.reject("price", "Enter S$0.01 to S$1,000,000.00.");
        }
        if (category.getValue() == null) {
            form.reject("category", "Choose a category.");
        }
        if (condition.getValue() == null) {
            form.reject("condition", "Choose a condition.");
        }
        if (!form.isValid()) {
            return;
        }
        ListingDraft draft = new ListingDraft(form.value("title"), form.value("description"),
                category.getValue(), price, condition.getValue(), form.value("pickup-location"));
        if (original == null) {
            persist(draft);
        } else {
            page.perform(() -> app.runtime.getOffers().getOffersForListing(original.getId()), offers -> {
                boolean hasPending = offers.stream()
                        .anyMatch(value -> value.offer().getStatus() == OfferStatus.PENDING);
                ListingDetails updated = new ListingDetails(draft.title(), draft.description(), draft.category(),
                        draft.priceCents(), draft.condition(), draft.pickupLocation());
                boolean isChanged = !updated.equals(original.getDetails()) || !photos.equals(originalPhotos());
                if (!hasPending || !isChanged || app.confirm("Save Listing",
                        "These changes will reject all pending offers on this listing.")) {
                    persist(draft);
                }
            });
        }
    }

    private void persist(ListingDraft draft) {
        page.perform(() -> original == null
                ? app.runtime.getListings().createListing(draft, List.copyOf(photos))
                : app.runtime.getListings().updateListing(original.getId(), draft, List.copyOf(photos)), saved -> {
                    page.setDirty(() -> false);
                    app.replace(() -> app.listings.details(saved.listing().getId()));
                });
    }
}
