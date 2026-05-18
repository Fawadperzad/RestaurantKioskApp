package controller;

import controller.AdminController;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.event.ActionEvent;
import java.io.IOException;
import java.net.URL;
import utils.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import models.DataManager;
import models.Product;

/**
 * OrderController
 * Handles the customer side of the application.
 */
public class OrderController {

    @FXML private Label welcomeLabel;
    @FXML private Label balanceLabel;

    // menu is rendered dynamically into menuVBox (no TableView in FXML)
    @FXML private ListView<String> cartList;
    @FXML private Label totalLabel;
    @FXML private javafx.scene.layout.VBox menuVBox;

    private double currentTotal = 0.0;
    private final ObservableList<String> cartItems = FXCollections.observableArrayList();

    // This holds the logged-in user's data
    private static AdminController.AdminUser loggedInUser;

    // No-arg constructor required by FXMLLoader
    public OrderController() {
    }


    public OrderController(Label welcomeLabel, Label balanceLabel)
    {
        this.welcomeLabel = welcomeLabel;
        this.balanceLabel = balanceLabel;
    }

    /**
     * Called by LoginController to set the session user
     */
    public static void setSessionUser(AdminController.AdminUser user) {
        loggedInUser = user;
    }

    @FXML
    public void initialize() {
        // 1. Display User Info
        if (loggedInUser != null && welcomeLabel != null) {
            welcomeLabel.setText("Welcome, " + loggedInUser.getUsername() + "!");
            updateBalanceDisplay();
        }

        // 2. Ensure cartList is wired
        if (cartList != null) {
            cartList.setItems(cartItems);
        }

        // Populate dynamic menu UI from central DataManager
        refreshMenuUI();
        try {
            DataManager.getProductList().addListener((javafx.collections.ListChangeListener<Product>) ch -> refreshMenuUI());
        } catch (Exception ignored) {}
    }

    // Build dynamic menu into menuVBox from DataManager
    private void refreshMenuUI() {
        System.out.println("OrderController.refreshMenuUI: products=" + DataManager.getProductList().size());
        if (menuVBox == null) {
            try {
                if (welcomeLabel != null && welcomeLabel.getScene() != null) {
                    javafx.scene.Node found = welcomeLabel.getScene().lookup("#menuVBox");
                    if (found instanceof javafx.scene.layout.VBox) menuVBox = (javafx.scene.layout.VBox) found;
                }
            } catch (Exception e) {
                System.err.println("OrderController.refreshMenuUI: lookup failed: " + e.getMessage());
            }
        }
        if (menuVBox == null) return;

        javafx.application.Platform.runLater(() -> {
            menuVBox.getChildren().clear();
            for (Product p : DataManager.getProductList()) {
                javafx.scene.layout.HBox hb = new javafx.scene.layout.HBox(10);
                hb.setStyle("-fx-border-color: #ecf0f1; -fx-border-width: 0 0 1 0; -fx-padding: 10;");
                hb.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                Label name = new Label(p.getName()); name.setPrefWidth(200); name.setStyle("-fx-font-size:16px;");
                Label price = new Label(String.format("%.2f €", p.getPrice())); price.setPrefWidth(80);
                Button add = new Button("Hinzufügen");
                add.setOnAction(evt -> addItem(p.getName(), p.getPrice()));

                hb.getChildren().addAll(name, price, add);
                menuVBox.getChildren().add(hb);
            }
            if (DataManager.getProductList().isEmpty()) {
                Label empty = new Label("Keine Produkte verfügbar. Bitte Admin kontaktieren.");
                empty.setStyle("-fx-font-style: italic; -fx-text-fill: #7f8c8d;");
                menuVBox.getChildren().add(empty);
            }
        });
    }

    private void updateBalanceDisplay() {
        if (loggedInUser != null) {
            balanceLabel.setText(String.format("Current Balance: $%.2f", loggedInUser.getBalance()));
        }
    }

    // --- Item handlers referenced by FXML ---

    @FXML
    private void addBurger() {
        addItem("🍔 Burger", 8.50);
    }

    @FXML
    private void addPizza() {
        addItem("🍕 Pizza", 10.00);
    }

    @FXML
    private void addDrink() {
        addItem("🥤 Kaltgetränk", 2.50);
    }

    // Helper to add an item to the cart and update totals
    private void addItem(String name, double price) {
        cartItems.add(name + " - €" + String.format("%.2f", price));
        currentTotal += price;
        totalLabel.setText(String.format("Gesamt: €%.2f", currentTotal));
    }

    @FXML
    private void handlePlaceOrder() {
        if (loggedInUser == null) {
            showError("Kein eingeloggter Benutzer.");
            return;
        }

        if (currentTotal <= 0) {
            showError("Ihr Warenkorb ist leer.");
            return;
        }

        double orderTotal = currentTotal; // capture before resetting

        System.out.println("Placing order for user=" + (loggedInUser != null ? loggedInUser.getUsername() : "<null>") + " total=" + orderTotal);

        if (loggedInUser.getBalance() >= orderTotal) {
            // Deduct balance
            loggedInUser.setBalance(loggedInUser.getBalance() - orderTotal);

            // Persist order in-memory
            AdminController.addOrder(loggedInUser.getUsername(), orderTotal, "COMPLETED");

            // Try to persist to DB if possible: find user id in DB and insert order
            try (Connection conn = DBConnection.getConnection()) {
                // find user id in DB
                String userSql = "SELECT id FROM users WHERE username = ?";
                try (PreparedStatement ps = conn.prepareStatement(userSql)) {
                    ps.setString(1, loggedInUser.getUsername());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            int userId = rs.getInt("id");
                            String insertSql = "INSERT INTO orders (user_id, total_price, status, created_at) VALUES (?, ?, ?, ?)";
                            try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                                ins.setInt(1, userId);
                                ins.setDouble(2, orderTotal);
                                ins.setString(3, "COMPLETED");
                                ins.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                                ins.executeUpdate();
                            }
                        } else {
                            System.out.println("Order: user not in DB, skipping DB persist.");
                        }
                    }
                }
            } catch (SQLException ex) {
                System.err.println("DB error while saving order; saved in-memory only: " + ex.getMessage());
                ex.printStackTrace();
            }

            // Success Feedback
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Erfolg");
            alert.setHeaderText("Bestellung abgeschlossen");
            alert.setContentText(String.format("Sie haben €%.2f ausgegeben. Verbleibend: €%.2f", orderTotal, loggedInUser.getBalance()));
            alert.showAndWait();

            // Reset cart
            cartItems.clear();
            currentTotal = 0.0;
            totalLabel.setText("Gesamt: €0.00");
            updateBalanceDisplay();
        } else {
            showError("Unzureichendes Guthaben. Bitte kontaktieren Sie den Admin.");
        }
    }

    @FXML
    private void goToHistory() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/History.fxml"));
            Parent root = loader.load();

            // Pass username to history controller
            Object ctrl = loader.getController();
            if (ctrl instanceof controller.HistoryController && loggedInUser != null) {
                ((controller.HistoryController) ctrl).setUserInfo(loggedInUser.getUsername());
            }

            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCheckout() {
        // Alias for placing order
        handlePlaceOrder();
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            URL location = getClass().getResource("/fxml/Login.fxml");
            if (location == null) {
                System.err.println("Could not find Login.fxml");
                return;
            }
            Parent root = FXMLLoader.load(location);
            stage.setScene(new Scene(root));
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // No-arg overload for FXML if it invokes without parameter
    @FXML
    private void handleLogout() {
        handleLogout(new ActionEvent());
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setContentText(message);
        alert.show();
    }

    public void setUserInfo(String currentUsername)
    {
        if (currentUsername == null) return;
        AdminController.AdminUser user = AdminController.getUser(currentUsername);
        if (user != null) {
            setSessionUser(user);
            if (welcomeLabel != null) welcomeLabel.setText("Welcome, " + user.getUsername() + "!");
            updateBalanceDisplay();
        }
    }
}
