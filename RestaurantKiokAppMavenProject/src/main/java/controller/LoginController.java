package controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import java.io.IOException;
import java.net.URL;


/**
 * LoginController
 * Routes users based on their role:
 * - Admin (admin/admin123) -> AdminPanel.fxml (Management)
 * - User -> Order.fxml (Shopping/Buying)
 * * Note: Fixed file path logic to prevent "Exit value: 1" errors.
 */
public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;

    /**
     * Main login logic triggered by the Login button.
     */
    @FXML
    private void handleLogin() {
        String username = usernameField.getText() != null ? usernameField.getText().trim() : "";
        String password = passwordField.getText() != null ? passwordField.getText().trim() : "";

        // 1. Basic validation
        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText("Please enter both username and password.");
            return;
        }

        System.out.println("Login attempt: username='" + username + "'");
        System.out.println("Existing users in AdminController:");
        for (controller.AdminController.AdminUser u : controller.AdminController.getUserList()) {
            System.out.println(" - '" + u.getUsername() + "' role=" + u.getAccountRole() + " balance=" + u.getBalance());
        }

        // 2. ADMIN ROLE CHECK
        if (username.equalsIgnoreCase("admin") && password.equals("admin123")) {
            System.out.println("Login Success: Administrator Access.");
            loadScene("/fxml/Admin.fxml", "Admin Dashboard - Management", username);
            return;
        }

        // 3. REGULAR USER ROLE CHECK
        controller.AdminController.AdminUser maybeUser = controller.AdminController.getUser(username);
        if (maybeUser != null && maybeUser.getPassword().equals(password)) {
            System.out.println("Login Success: User Access.");
            // Prefer user-specific dashboard if available
            URL userFxmlCheck = getClass().getResource("/fxml/User.fxml");
            if (userFxmlCheck != null) {
                loadScene("/fxml/User.fxml", "Your Account - Kiosk", username);
            } else {
                loadScene("/fxml/Order.fxml", "Kiosk - Shop & Order", username);
            }
            return;
        }

        // 4. INVALID LOGIN - provide helpful info
        if (maybeUser == null) {
            errorLabel.setText("Invalid username or password!");
            System.out.println("Login failed: user not found. Existing users:");
            for (controller.AdminController.AdminUser u : controller.AdminController.getUserList()) {
                System.out.println(" - " + u.getUsername() + " (role=" + u.getAccountRole() + ")");
            }
        } else {
            errorLabel.setText("Incorrect password for user: " + username);
            System.out.println("Login failed: wrong password for user '" + username + "'");
        }
    }

    /**
     * Helper method to switch scenes and update the window title.
     * Includes extra debugging to catch "Exit value: 1" causes.
     */
    private void loadScene(String fxmlFile, String title, String username) {
        try {
            // If loading Order.fxml, pre-check the user exists and set session
            controller.AdminController.AdminUser userObj = null;
            if (fxmlFile.endsWith("Order.fxml")) {
                userObj = controller.AdminController.getUser(username);
                if (userObj == null) {
                    if (errorLabel != null) errorLabel.setText("User not found. Bitte vom Admin anlegen.");
                    return;
                }
                // set the session early so controller.initialize() can read it
                controller.OrderController.setSessionUser(userObj);
            }

            // Locate the FXML resource
            URL fxmlLocation = getClass().getResource(fxmlFile);

            if (fxmlLocation == null) {
                System.err.println("CRITICAL ERROR: Could not find FXML file at: " + fxmlFile);
                System.err.println("Ensure the file is in src/main/resources" + fxmlFile);
                if (errorLabel != null) errorLabel.setText("System Error: View file missing: " + fxmlFile);
                return;
            }

            // Load the FXML
            FXMLLoader loader = new FXMLLoader(fxmlLocation);
            Parent root;
            try {
                root = loader.load();
            } catch (Exception loadEx) {
                System.err.println("FAILED TO LOAD FXML: " + fxmlFile + " ; cause: " + loadEx.getMessage());
                loadEx.printStackTrace();
                // If loading User.fxml fails due to a missing handler, fall back to Order.fxml to allow user access
                if (fxmlFile.endsWith("User.fxml")) {
                    System.err.println("Attempting fallback to /fxml/Order.fxml");
                    URL fallback = getClass().getResource("/fxml/Order.fxml");
                    if (fallback != null) {
                        try {
                            FXMLLoader f2 = new FXMLLoader(fallback);
                            Parent root2 = f2.load();
                            root = root2;
                            if (errorLabel != null) errorLabel.setText("Loaded fallback view (Order) because User view failed to load; see console for details.");
                        } catch (Exception ex2) {
                            System.err.println("Fallback also failed: " + ex2.getMessage());
                            ex2.printStackTrace();
                            if (errorLabel != null) errorLabel.setText("System Error: could not load user view or fallback.");
                            return;
                        }
                    } else {
                        if (errorLabel != null) errorLabel.setText("System Error: User view failed and fallback missing.");
                        return;
                    }
                } else {
                    setErrorLabelWithException("Fehler beim Laden der Ansicht", (Exception) loadEx);
                    return;
                }
            }

            // After loading, pass the username/session to the controller instance so it can initialize UI
            Object ctrl = loader.getController();
            // Note: if we used fallback, loader still points to original; try to get controller from fallback loader if null
            if (ctrl == null && root != null) {
                // try to obtain controller via FXMLLoader set on fallback
                // (we already attempted to set errorLabel above)
            }
            if (ctrl != null) {
                // If it's an OrderController, set static session if available and call instance setter if present
                if (ctrl instanceof controller.OrderController) {
                    // Ensure static session is set for initialize()
                    controller.AdminController.AdminUser u = userObj != null ? userObj : controller.AdminController.getUser(username);
                    if (u != null) controller.OrderController.setSessionUser(u);
                    try {
                        ((controller.OrderController) ctrl).setUserInfo(username);
                    } catch (Exception ignored) {}
                }
                // If it's a UserController, call instance method to set user info
                else if (ctrl instanceof controller.UserController) {
                    try {
                        ((controller.UserController) ctrl).setUserInfo(username);
                    } catch (Exception ignored) {}
                }
            }

            // Get the current stage (window) from the login button
            if (loginButton != null && loginButton.getScene() != null) {
                Stage stage = (Stage) loginButton.getScene().getWindow();

                // Set the new scene to the existing stage
                stage.setScene(new Scene(root));
                stage.setTitle(title);
                stage.centerOnScreen();
                stage.show();

                // After scene is shown, call controller-specific post-show hooks
                if (ctrl != null) {
                    if (ctrl instanceof controller.UserController) {
                        try {
                            ((controller.UserController) ctrl).refreshMenuUI();
                        } catch (Exception ignore) {}
                    } else if (ctrl instanceof controller.OrderController) {
                        try { ((controller.OrderController) ctrl).setUserInfo(username); } catch (Exception ignore) {}
                    }
                }
            } else {
                System.err.println("ERROR: Could not get the current Window Stage.");
                if (errorLabel != null) errorLabel.setText("Internal error: cannot get application window.");
            }

        } catch (Exception e) {
            // Kombinierter Catch: behandelt sowohl I/O-Fehler beim Laden der FXML-Datei
            // als auch unerwartete Fehler. Die separate IOException-Catch-Klausel war
            // nicht notwendig, weil alle Aufrufe innerhalb des try-Blocks bereits
            // Exceptions abfangen oder keine geprüften IOExceptions mehr werfen.
            System.err.println("FAILED TO LOAD VIEW: " + fxmlFile);
            System.err.println("Cause: " + e.getMessage());
            e.printStackTrace(); // This will show you exactly which line in the FXML is broken
            setErrorLabelWithException("Fehler beim Laden der Ansicht", e);
        }
    }

    // Setzt eine aussagekräftige Fehlermeldung ins errorLabel (Kurzform + evtl. Cause)
    private void setErrorLabelWithException(String prefix, Exception e) {
        if (errorLabel == null) return;
        // Extrahiere Root Cause
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        String msg = root.getMessage();
        if (msg == null || msg.trim().isEmpty()) msg = e.getClass().getSimpleName();
        // Kürze die Meldung, falls sehr lang
        if (msg.length() > 200) msg = msg.substring(0, 200) + "...";
        errorLabel.setText(prefix + ": " + msg);
    }

    /**
     * Logic to validate a regular user.
     */
    private boolean isValidUser(String username, String password) {
        return !username.equalsIgnoreCase("admin") && controller.AdminController.validateUser(username, password);
    }

    /**
     * Redirects to forgot password logic
     */
    @FXML
    private void handleRegisterRedirect() {
        System.out.println("Forgot Password link clicked.");
    }
}

