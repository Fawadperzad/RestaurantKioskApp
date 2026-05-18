package controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.event.ActionEvent;
import models.DataManager;
import models.Product;
import java.io.IOException;
import java.net.URL;

/**
 * UserController
 * Controller for `User.fxml` — handles the user-facing kiosk: menu, cart, orders.
 */
public class UserController {

    @FXML private Label welcomeLabel;
    @FXML private Label balanceLabel;
    @FXML private ListView<String> cartList;
    @FXML private Label totalLabel;
    @FXML private ListView<String> historyListView;
    @FXML private javafx.scene.layout.VBox menuVBox;
    // Table and columns declared in User.fxml
    @FXML private TableView<models.Product> menuTable;
    @FXML private TableColumn<models.Product, String> colItemName;
    @FXML private TableColumn<models.Product, Double> colItemPrice;
    @FXML private TableColumn<models.Product, String> colItemRestaurant;
    @FXML private javafx.scene.control.TextField orderQuantity;
    @FXML private ChoiceBox<String> restaurantFilter;

    private double currentTotal = 0.0;
    private final ObservableList<String> cartItems = FXCollections.observableArrayList();
    // Aggregated cart entries: key = product id or name
    private static class CartEntry {
        String id;
        String name;
        double price;
        int qty;
        CartEntry(String id, String name, double price, int qty) { this.id = id; this.name = name; this.price = price; this.qty = qty; }
    }
    private final java.util.LinkedHashMap<String, CartEntry> cartEntries = new java.util.LinkedHashMap<>();
    // Filtered view for the menu table
    private javafx.collections.transformation.FilteredList<Product> menuFiltered;

    private static AdminController.AdminUser loggedInUser;

    public UserController() {
    }

    public static void setSessionUser(AdminController.AdminUser user) {
        loggedInUser = user;
        System.out.println("UserController.setSessionUser called: " + (user != null ? user.getUsername() : "<null>"));
    }

    @FXML
    public void initialize() {
        System.out.println("UserController.initialize: loggedInUser=" + (loggedInUser != null ? loggedInUser.getUsername() : "<null>"));
        if (cartList != null) cartList.setItems(cartItems);
        if (welcomeLabel != null && loggedInUser != null) welcomeLabel.setText("Welcome, " + loggedInUser.getUsername() + "!");
        if (balanceLabel != null && loggedInUser != null) balanceLabel.setText(String.format("%.2f $", loggedInUser.getBalance()));
        if (historyListView != null) historyListView.setItems(FXCollections.observableArrayList(AdminController.getOrdersForUser(loggedInUser != null ? loggedInUser.getUsername() : "")));

        // Initialize menu table if present
        try {
            if (colItemName != null) colItemName.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("name"));
            if (colItemPrice != null) colItemPrice.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("price"));
            if (colItemRestaurant != null) colItemRestaurant.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("restaurant"));
            if (menuTable != null) {
                menuFiltered = new javafx.collections.transformation.FilteredList<>(models.DataManager.getProductList(), p -> true);
                menuTable.setItems(menuFiltered);
            }
            // Populate restaurant filter choices
            if (restaurantFilter != null) {
                java.util.Set<String> restSet = new java.util.LinkedHashSet<>();
                restSet.add("All Restaurants");
                for (Product p : models.DataManager.getProductList()) if (p.getRestaurant() != null) restSet.add(p.getRestaurant());
                restaurantFilter.getItems().setAll(restSet);
                restaurantFilter.getSelectionModel().selectFirst();
                restaurantFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> applyRestaurantFilter(newV));
            }
         } catch (Exception ex) {
             System.err.println("UserController.initialize: failed to init menuTable: " + ex.getMessage());
         }

         // Populate dynamic menu from central DataManager product list
         refreshMenuUI();
         // Listen for product changes
         try {
             DataManager.getProductList().addListener((javafx.collections.ListChangeListener<Product>) c -> refreshMenuUI());
         } catch (Exception ex) {
             System.err.println("UserController.initialize: cannot add listener to product list: " + ex.getMessage());
         }
     }

    private void applyRestaurantFilter(String selected) {
        if (menuFiltered == null) return;
        if (selected == null || selected.equalsIgnoreCase("All Restaurants")) {
            menuFiltered.setPredicate(p -> true);
        } else {
            menuFiltered.setPredicate(p -> selected.equals(p.getRestaurant()));
        }
        if (menuTable != null) menuTable.refresh();
        // Also refresh dynamic menu UI so filtered items are shown/hidden
        refreshMenuUI();
    }
    /**
     * Handler for the "Add to Cart" button declared in User.fxml
     */
    @FXML
    private void handleAddToCart(javafx.event.ActionEvent event) {
        try {
            Product sel = null;
            if (menuTable != null) sel = menuTable.getSelectionModel().getSelectedItem();
            if (sel == null) {
                showAlert("Bitte wählen Sie ein Produkt aus der Liste aus.");
                return;
            }
            int qty = 1;
            if (orderQuantity != null) {
                try { qty = Integer.parseInt(orderQuantity.getText().trim()); } catch (NumberFormatException ignore) { qty = 1; }
                if (qty <= 0) qty = 1;
            }
            addToCart(sel, qty);
        } catch (Exception e) {
            System.err.println("handleAddToCart: " + e.getMessage());
            e.printStackTrace();
            showAlert("Fehler beim Hinzufügen zum Warenkorb.");
        }
    }

    // Build the menu UI from DataManager.getProductList()
    public void refreshMenuUI() {
        System.out.println("UserController.refreshMenuUI called; productCount=" + DataManager.getProductList().size());
        if (menuVBox == null) {
            // try scene lookup fallback
            try {
                if (cartList != null && cartList.getScene() != null) {
                    javafx.scene.Node found = cartList.getScene().lookup("#menuVBox");
                    if (found instanceof javafx.scene.layout.VBox) {
                        menuVBox = (javafx.scene.layout.VBox) found;
                        System.out.println("UserController.refreshMenuUI: recovered menuVBox via scene.lookup");
                    }
                }
            } catch (Exception e) {
                System.err.println("UserController.refreshMenuUI: scene lookup failed: " + e.getMessage());
            }
        }
        if (menuVBox == null) {
            System.out.println("UserController.refreshMenuUI: menuVBox null, abort");
            return;
        }

        javafx.application.Platform.runLater(() -> {
            // Clear existing content and build a header + scrollable product list
            menuVBox.getChildren().clear();

            Label header = new Label("Menu Card");
            header.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
            menuVBox.getChildren().add(header);

            // Scrollable container for product entries
            ScrollPane sp = new ScrollPane();
            sp.setFitToWidth(true);
            sp.setStyle("-fx-background-color: transparent; -fx-padding: 0;");

            javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8);
            content.setStyle("-fx-padding: 8;");

            // Use filtered list when available so restaurant filter affects dynamic menu too
            Iterable<Product> source = (menuFiltered != null) ? menuFiltered : DataManager.getProductList();
            for (Product p : source) {
                javafx.scene.layout.HBox hb = new javafx.scene.layout.HBox(10);
                hb.setStyle("-fx-border-color: #ecf0f1; -fx-border-width: 0 0 1 0; -fx-padding: 10;");
                hb.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                Label name = new Label(p.getName());
                name.setPrefWidth(200.0);
                name.setStyle("-fx-font-size: 16px;");

                Label price = new Label(String.format("%.2f $", p.getPrice()));
                price.setPrefWidth(100.0);
                price.setStyle("-fx-font-weight: bold;");

                Button add = new Button("Add to Cart");
                add.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-cursor: hand;");
                add.setOnAction(evt -> {
                    int q = 1;
                    if (orderQuantity != null) {
                        try { q = Integer.parseInt(orderQuantity.getText().trim()); } catch (NumberFormatException ignored) { q = 1; }
                        if (q <= 0) q = 1;
                    }
                    addToCart(p, q);
                });

                hb.getChildren().addAll(name, price, add);
                content.getChildren().add(hb);
            }

            // If no products, show placeholder message
            if (DataManager.getProductList().isEmpty()) {
                Label empty = new Label("No products available. Please ask admin to add items.");
                empty.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");
                content.getChildren().add(empty);
            }

            sp.setContent(content);
            // Make scrollpane grow to fill available space in menuVBox
            javafx.scene.layout.VBox.setVgrow(sp, javafx.scene.layout.Priority.ALWAYS);
            menuVBox.getChildren().add(sp);

            // Hint/footer
            HBox hintBox = new HBox();
            hintBox.setStyle("-fx-padding: 8; -fx-background-color: #f8f9fa; -fx-background-radius: 5;");
            Label hint = new Label("Select items to see them in your cart on the right.");
            hint.setStyle("-fx-font-style: italic; -fx-text-fill: #7f8c8d;");
            hintBox.getChildren().add(hint);
            menuVBox.getChildren().add(hintBox);

            System.out.println("UserController.refreshMenuUI: menuVBox children=" + menuVBox.getChildren().size());
        });
    }

    private void addItem(String name, double price) {
        // Backwards-compatible single-add helper uses aggregated addToCart when possible
        Product dummy = new Product(name, price);
        addToCart(dummy, 1);
    }
    private void addToCart(Product p, int qty) {
        if (p == null || qty <= 0) return;
        String key = p.getId() != null ? p.getId() : p.getName();
        CartEntry e = cartEntries.get(key);
        if (e == null) {
            e = new CartEntry(key, p.getName(), p.getPrice(), qty);
            cartEntries.put(key, e);
        } else {
            e.qty += qty;
        }
        // Rebuild cartList display and total
        currentTotal = 0.0;
        java.util.List<String> display = new java.util.ArrayList<>();
        for (CartEntry ce : cartEntries.values()) {
            double lineTotal = ce.price * ce.qty;
            currentTotal += lineTotal;
            display.add(String.format("%s x%d - %.2f $", ce.name, ce.qty, lineTotal));
        }
        javafx.application.Platform.runLater(() -> {
            cartItems.setAll(display);
            if (totalLabel != null) totalLabel.setText(String.format("%.2f $", currentTotal));
            // Reset quantity input to 1 after adding so subsequent adds start from 1
            try {
                if (orderQuantity != null) {
                    // Increment the displayed quantity by the amount just added so the field reflects cumulative adds
                    int current = 0;
                    try { current = Integer.parseInt(orderQuantity.getText().trim()); } catch (Exception ex) { current = 0; }
                    int newVal = current + qty;
                    if (newVal < 0) newVal = 0;
                    orderQuantity.setText(String.valueOf(newVal));
                }
            } catch (Exception ignored) {}
        });
    }

    // Keep these handlers for the static items declared in the FXML so FXMLLoader can bind them
//    @FXML
//    private void addBurger() { addItem("🍔 Burger", 8.50); }
//
//    @FXML
//    private void addPizza() { addItem("🍕 Pizza", 10.00); }
//
//    @FXML
//    private void addDrink() { addItem("🥤 Cold Drink", 2.50); }

    @FXML
    private void handlePlaceOrder() {
        if (loggedInUser == null) {
            showAlert("Kein eingeloggter Benutzer.");
            return;
        }
        if (currentTotal <= 0) {
            showAlert("Ihr Warenkorb ist leer.");
            return;
        }
        // Deduct balance if possible
        if (loggedInUser.getBalance() >= currentTotal) {
            double placed = currentTotal;
            loggedInUser.setBalance(loggedInUser.getBalance() - placed);
            AdminController.addOrder(loggedInUser.getUsername(), placed, "COMPLETED");
            // refresh history
            if (historyListView != null) historyListView.setItems(FXCollections.observableArrayList(AdminController.getOrdersForUser(loggedInUser.getUsername())));
            cartItems.clear();
            currentTotal = 0.0;
            if (totalLabel != null) totalLabel.setText("0.00 $");
            if (balanceLabel != null) balanceLabel.setText(String.format("%.2f $", loggedInUser.getBalance()));
            showAlert(String.format("Bestellung abgeschlossen: %.2f $", placed));
        } else {
            showAlert("Unzureichendes Guthaben.");
        }
    }

    @FXML
    private void handleRefreshHistory() {
        if (loggedInUser != null && historyListView != null) {
            historyListView.setItems(FXCollections.observableArrayList(AdminController.getOrdersForUser(loggedInUser.getUsername())));
        }
    }

    // Clears the cart contents and resets totals
    @FXML
    private void handleClearCart() {
        cartEntries.clear();
        cartItems.clear();
        currentTotal = 0.0;
        if (totalLabel != null) totalLabel.setText("0.00 $");
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

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setContentText(msg);
        a.show();
    }

    // backward-compatible setter used when history controller passes username
    public void setUserInfo(String username) {
        if (username == null) return;
        System.out.println("UserController.setUserInfo called with username=" + username);
        AdminController.AdminUser u = AdminController.getUser(username);
        if (u != null) {
            setSessionUser(u);
            if (welcomeLabel != null) welcomeLabel.setText("Welcome, " + u.getUsername() + "!");
            if (balanceLabel != null) balanceLabel.setText(String.format("%.2f $", u.getBalance()));
            System.out.println("UserController.setUserInfo: found user, updated labels for " + u.getUsername());
        } else {
            System.out.println("UserController.setUserInfo: user not found for " + username);
        }
    }

    // Public method used by AdminController or others to refresh menu filter externally
    public void handleRefreshMenu() {
        try {
            String sel = (restaurantFilter != null) ? restaurantFilter.getValue() : null;
            applyRestaurantFilter(sel);
            refreshMenuUI();
        } catch (Exception e) {
            System.err.println("handleRefreshMenu: " + e.getMessage());
        }
    }
 }
