package hotshop.ui;

import java.nio.file.Path;
import java.util.UUID;

import hotshop.model.User;
import hotshop.service.ServiceException;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

/** Account screens call AccountService; no credentials are retained after leaving these forms. */
final class AccountPages {
    private final MarketplaceUi app;

    AccountPages(MarketplaceUi app) {
        this.app = app;
    }

    void profile(UUID id) {
        if (id.equals(app.userId())) {
            ownProfile();
            return;
        }
        UiPage page = app.page(id.equals(app.userId()) ? "My Profile" : "Public Profile");
        page.load(() -> app.runtime.getAccounts().getPublicProfile(id), profile -> {
            var name = UiControls.label(profile.displayName(), "section-title");
            name.setId("profile-name");
            page.body.getChildren().addAll(name, UiImages.display(profile.profileImage().isEmpty() ? null
                    : () -> app.runtime.getProfileImagePath(profile.profileImage().orElseThrow()), 128, 128));
            page.load(() -> app.runtime.getListings().getPublicListings(id), listings -> {
                if (listings.isEmpty()) {
                    page.body.getChildren().add(UiControls.label("No available listings", "muted"));
                } else {
                    var grid = ListingCards.grid();
                    listings.forEach(value -> grid.getChildren().add(ListingCards.card(app, value, null)));
                    page.body.getChildren().add(grid);
                }
            });
        });
    }

    private void ownProfile() {
        UiPage page = app.page("My Profile");
        page.load(app.runtime.getAccounts()::getOwnProfile, user -> renderOwnProfile(page, user));
    }

    private void renderOwnProfile(UiPage page, User user) {
        UiForm form = new UiForm();
        var username = form.text("username", "Username (cannot be changed)", user.getUsername());
        username.setEditable(false);
        var displayName = form.text("display-name", "Display name", user.getDisplayName());
        var location = form.text("preferred-location", "Preferred pickup location (private)",
                user.getPreferredPickupLocation().orElse(""));
        String[] saved = {displayName.getText(), location.getText()};
        page.setDirty(() -> !saved[0].equals(displayName.getText()) || !saved[1].equals(location.getText()));
        page.setValidation(form::serviceError);
        form.getChildren().add(UiControls.primary("Save Profile", "save-profile", () -> {
            form.clearErrors();
            form.textLength("display-name", 80, true);
            form.textLength("preferred-location", 200, false);
            if (form.isValid()) {
                page.perform(() -> app.runtime.getAccounts().updateProfile(displayName.getText(),
                        location.getText().isBlank() ? null : location.getText()), updated -> {
                            displayName.setText(updated.getDisplayName());
                            location.setText(updated.getPreferredPickupLocation().orElse(""));
                            saved[0] = displayName.getText();
                            saved[1] = location.getText();
                            page.message("Profile saved.");
                        });
            }
        }));
        VBox image = new VBox(12);
        showProfileImage(image, user);
        var replace = UiControls.button("Replace Image", "replace-profile-image", () -> {
            Path selected = UiImages.choose(app.stage);
            if (selected != null) {
                page.perform(() -> app.runtime.getAccounts().replaceProfileImage(selected), updated -> {
                    showProfileImage(image, updated);
                    page.message("Profile image saved.");
                });
            }
        });
        var remove = UiControls.button("Remove Image", "remove-profile-image", () ->
                page.perform(app.runtime.getAccounts()::removeProfileImage, updated -> {
                    showProfileImage(image, updated);
                    page.message("Profile image removed.");
                }));
        page.body.getChildren().addAll(form, image,
                UiControls.label("Profile images: JPEG/PNG, at most 5 MiB and 512 × 512 pixels. "
                        + "Image changes save separately from profile text.", "hint"),
                UiControls.actions(replace, remove),
                UiControls.button("Change Password", "change-password", () -> changePassword(page)));
    }

    private void showProfileImage(VBox image, User user) {
        image.getChildren().setAll(UiImages.display(user.getProfileImage().isEmpty() ? null
                : () -> app.runtime.getProfileImagePath(user.getProfileImage().orElseThrow()), 128, 128));
    }

    private void changePassword(UiPage page) {
        UiForm form = new UiForm();
        form.password("current-password", "Current password");
        form.password("password", "New password");
        form.password("confirm-password", "Confirm new password");
        form.mapServiceError(ServiceException.Code.AUTHENTICATION, "current-password");
        form.mapServiceError(ServiceException.Code.VALIDATION, "password");
        form.getChildren().add(UiControls.label(
                "Use 8–128 characters, including uppercase, lowercase, a digit and punctuation.", "hint"));
        UiDialogs.form(app, page, "Change Password", "Save Password", form, () -> {
            form.clearErrors();
            form.textLength("current-password", 128, true);
            form.matchingPasswords("password", "confirm-password");
            return form.isValid();
        }, () -> app.runtime.getAccounts().changePassword(form.value("current-password"), form.value("password")),
                ignored -> page.message("Password changed. You remain logged in."));
    }

    void login(String username) {
        UiPage page = app.page("Log in");
        UiForm form = new UiForm();
        page.setValidation(form::serviceError);
        form.text("username", "Username", username);
        var password = form.password("password", "Password");
        Button submit = UiControls.primary("Log in", "login-submit", () -> {
            form.clearErrors();
            form.textLength("username", 30, true);
            form.textLength("password", 128, true);
            if (form.isValid()) {
                page.perform(() -> app.runtime.getAccounts().login(form.value("username"), form.value("password")),
                        user -> {
                            password.clear();
                            app.loggedIn(user);
                        });
            }
        });
        password.setOnAction(event -> submit.fire());
        form.getChildren().addAll(submit, UiControls.button("Create an account", "register-link",
                () -> app.navigate(this::register)));
        page.body.getChildren().add(form);
    }

    private void register() {
        UiPage page = app.page("Register");
        UiForm form = new UiForm();
        page.setValidation(form::serviceError);
        form.text("username", "Username", "");
        form.text("display-name", "Display name", "");
        form.password("password", "Password");
        form.password("confirm-password", "Confirm password");
        form.mapServiceError(ServiceException.Code.USERNAME_UNAVAILABLE, "username");
        form.mapServiceError(ServiceException.Code.VALIDATION, "password");
        form.getChildren().add(UiControls.label("Use 3–30 letters, digits or underscores for your username. "
                + "Passwords need 8–128 characters including uppercase, lowercase, a digit and punctuation.", "hint"));
        form.getChildren().add(UiControls.primary("Register", "register-submit", () -> {
            form.clearErrors();
            form.textLength("username", 30, true);
            form.textLength("display-name", 80, true);
            form.matchingPasswords("password", "confirm-password");
            if (!form.value("username").strip().matches("[A-Za-z0-9_]{3,30}")) {
                form.reject("username", "Use 3–30 letters, digits or underscores.");
            }
            if (form.isValid()) {
                page.perform(() -> app.runtime.getAccounts().register(form.value("username"),
                        form.value("password"), form.value("display-name")), user -> {
                            app.login(user.getUsername());
                            app.message("Account created. Log in to continue.");
                        });
            }
        }));
        form.getChildren().add(UiControls.button("Back to login", "login-link", () -> app.login("")));
        page.body.getChildren().add(form);
    }
}
