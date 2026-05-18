package controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import models.DataManager;
import models.Product;
import java.io.IOException;
import java.net.URL;

/**
 * AdminController
 * Logic for managing users and products.
 * Removed: Create-Order / Active Cart / Order History UI from admin.
 * Added: Product search & edit functionality in Inventory Management.
 */
public class AdminController {

    // User Management
    @FXML private TableView<AdminUser> userTable;
    @FXML private TableColumn<AdminUser, String> colUserId;
    @FXML private TableColumn<AdminUser, String> colUserName;
    @FXML private TableColumn<AdminUser, String> colUserRole; // Neue Spalte für Rolle
    @FXML private TableColumn<AdminUser, String> colUserPassword; // Passwort-Spalte
    @FXML private TableColumn<AdminUser, Double> colUserBalance;

    @FXML private TextField newUserName;
    @FXML private PasswordField newUserPassword;
    @FXML private ComboBox<String> newUserRole; // ComboBox für Rolle aus FXML
    @FXML private TextField amountToChange;

    // Product Management
    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, String> colProdId;
    @FXML private TableColumn<Product, String> colProdName;
    @FXML private TableColumn<Product, Double> colProdPrice;
    @FXML private TableColumn<Product, String> colProdRestaurant;

    @FXML private TextField newProductName;
    @FXML private TextField newProductPrice;
    @FXML private ComboBox<String> productRestaurantChoice;

    // Search field for products
    @FXML private TextField productSearchField;
    @FXML private ListView<String> publicProductListView;
    @FXML private javafx.scene.text.Text filteredProductCountText;
    @FXML private ComboBox<String> viewRestaurantFilter;

    // Internal state
    // Users stored centrally for admin actions and login compatibility
    private static final ObservableList<AdminUser> userList = FXCollections.observableArrayList();

    // Orders kept for backend purposes (other controllers may use)
    private static final ObservableList<AdminOrder> orderList = FXCollections.observableArrayList();

    // Use central product list (backed by DataManager)
    private final ObservableList<Product> productList = DataManager.getProductList();

    // Filtered/Sorted wrapper for the TableView search
    private FilteredList<Product> filteredProducts;
    private AdminUser selectedUserEditing = null;

    // Public accessor for other code that expects AdminController.getUserList()
    public static ObservableList<AdminUser> getUserList() {
        return userList;
    }

    // Validate credentials against admin-created users
    public static boolean validateUser(String username, String password) {
        if (username == null || password == null) return false;
        for (AdminUser u : userList) {
            if (u.getUsername().equalsIgnoreCase(username) && u.getPassword().equals(password)) return true;
        }
        return false;
    }

    // Return AdminUser by username
    public static AdminUser getUser(String username) {
        if (username == null) return null;
        for (AdminUser u : userList) {
            if (u.getUsername().equalsIgnoreCase(username)) return u;
        }
        return null;
    }

    // Provide product list through AdminController for compatibility
    public ObservableList<Product> getProductList() {
        return productList;
    }

    @FXML
    private void handleRefreshProducts() {
        if (publicProductListView != null) {
            javafx.application.Platform.runLater(() -> {
                publicProductListView.getItems().clear();
                for (Product p : productList) {
                    publicProductListView.getItems().add(String.format("%s — %s — %.2f $", p.getId(), p.getName(), p.getPrice()));
                }
            });
        }
    }

    // Adds an in-memory order record (kept for compatibility)
    public static void addOrder(String username, double totalPrice, String status) {
        AdminOrder o = new AdminOrder(username, totalPrice, status, new java.sql.Timestamp(System.currentTimeMillis()));
        orderList.add(o);
        System.out.println("In-memory order added: user=" + username + " total=" + totalPrice + " status=" + status);
    }

    // Returns formatted order entries (kept for compatibility)
    public static java.util.List<String> getOrdersForUser(String username) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (AdminOrder o : orderList) {
            if (o.getUsername().equals(username)) {
                out.add(String.format("[%s] Betrag: %.2f € | Status: %s", o.getCreatedAt().toString(), o.getTotalPrice(), o.getStatus()));
            }
        }
        return out;
    }

    @FXML
    public void initialize() {
        // Set up User Table
        if (colUserId != null) colUserId.setCellValueFactory(new PropertyValueFactory<>("id"));
        if (colUserName != null) colUserName.setCellValueFactory(new PropertyValueFactory<>("username"));
        if (colUserRole != null) colUserRole.setCellValueFactory(new PropertyValueFactory<>("accountRole"));
        if (colUserPassword != null) colUserPassword.setCellValueFactory(new PropertyValueFactory<>("password"));
        if (colUserBalance != null) colUserBalance.setCellValueFactory(new PropertyValueFactory<>("balance"));
        if (userTable != null) userTable.setItems(userList);

        // Diagnostic: print which FXML fields were injected (helps find mismatches between FXML and controller)
        validateFXMLInjections();

        // Set up Product Table
        if (colProdId != null) colProdId.setCellValueFactory(new PropertyValueFactory<>("id"));
        if (colProdName != null) colProdName.setCellValueFactory(new PropertyValueFactory<>("name"));
        if (colProdPrice != null) colProdPrice.setCellValueFactory(new PropertyValueFactory<>("price"));
        if (colProdRestaurant != null) colProdRestaurant.setCellValueFactory(new PropertyValueFactory<>("restaurant"));

        // Wrap productList in a filtered list for searching
        filteredProducts = new FilteredList<>(productList, p -> true);
        SortedList<Product> sorted = new SortedList<>(filteredProducts);
        if (productTable != null) {
            productTable.setItems(sorted);
            sorted.comparatorProperty().bind(productTable.comparatorProperty());
        }

        // Search field listener
        if (productSearchField != null) {
            productSearchField.textProperty().addListener((obs, oldV, newV) -> {
                final String term = newV == null ? "" : newV.toLowerCase().trim();
                filteredProducts.setPredicate(p -> {
                    if (term.isEmpty()) return true;
                    return (p.getName() != null && p.getName().toLowerCase().contains(term))
                            || (p.getId() != null && p.getId().toLowerCase().contains(term))
                            || String.valueOf(p.getPrice()).contains(term)
                            || (p.getRestaurant()!=null && p.getRestaurant().toLowerCase().contains(term));
                });
                updateFilteredCount();
            });
        }

        // Populate restaurant choice controls if present
        if (productRestaurantChoice != null) {
            productRestaurantChoice.getItems().setAll("Restaurant 1","Restaurant 2","Restaurant 3","Restaurant 4","Restaurant 5");
        }
        if (viewRestaurantFilter != null) {
            viewRestaurantFilter.getItems().setAll("All Restaurants","Restaurant 1","Restaurant 2","Restaurant 3","Restaurant 4","Restaurant 5");
            viewRestaurantFilter.getSelectionModel().selectFirst();
        }

        // initial count update
        updateFilteredCount();

        // Seed sample admin user if empty (kept for compatibility)
        if (userList.isEmpty()) {
            userList.add(new AdminUser("101", "DemoUser", "123", "Admin", 50.0));
        }

        // Debug logging
        System.out.println("AdminController.initialize: productList.size=" + productList.size());
    }

    /**
     * Creates user with username, password, and initial amount.
     */
    @FXML
    private void handleCreateUser() {
        String name = (newUserName != null && newUserName.getText() != null) ? newUserName.getText().trim() : "";
        String pass = (newUserPassword != null && newUserPassword.getText() != null) ? newUserPassword.getText().trim() : "";
        String amountStr = (amountToChange != null && amountToChange.getText() != null) ? amountToChange.getText().trim() : "";
        System.out.println("handleCreateUser called: name='" + name + "' roleSelected=" + (newUserRole!=null?newUserRole.getValue():"<null>") );

        if (!name.isEmpty() && !pass.isEmpty()) {
            double initialBalance = 0.0;
            try {
                if (!amountStr.isEmpty()) initialBalance = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                System.err.println("Invalid amount format. Defaulting to 0.0");
            }

            String role = "User";
            if (newUserRole != null && newUserRole.getValue() != null && !newUserRole.getValue().isEmpty()) role = newUserRole.getValue();

            if (selectedUserEditing != null) {
                // Update selected user: replace the AdminUser in the list
                String oldUsername = selectedUserEditing.getUsername();
                int idx = userList.indexOf(selectedUserEditing);
                if (idx >= 0) {
                    AdminUser updated = new AdminUser(selectedUserEditing.getId(), name, pass, role, initialBalance);
                    userList.set(idx, updated);
                }
                // Update DataManager user record if present (match by old username)
                for (models.User u : DataManager.getUserList()) {
                    if (u.getUsername().equals(oldUsername)) {
                        // update observable properties
                        u.usernameProperty().set(name);
                        u.setBalance(initialBalance);
                        break;
                    }
                }
                selectedUserEditing = null;
            } else {
                AdminUser created = new AdminUser(String.valueOf(userList.size() + 101), name, pass, role, initialBalance);
                userList.add(created);
                DataManager.getUserList().add(new models.User(name, pass, initialBalance));
                System.out.println("Created user: " + name + " role=" + role + " balance=" + initialBalance);
            }

            newUserName.clear();
            newUserPassword.clear();
            amountToChange.clear();
            if (newUserRole != null) newUserRole.getSelectionModel().clearSelection();
        }
    }

    @FXML
    private void handleEditUser() {
        AdminUser sel = userTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie einen Benutzer zum Bearbeiten aus.", ButtonType.OK);
            a.show();
            return;
        }
        // populate form
        if (newUserName != null) newUserName.setText(sel.getUsername());
        if (newUserPassword != null) newUserPassword.setText(sel.getPassword());
        if (amountToChange != null) amountToChange.setText(String.valueOf(sel.getBalance()));
        if (newUserRole != null) newUserRole.setValue(sel.getAccountRole());
        selectedUserEditing = sel;
    }

    @FXML
    private void handleDeleteUser() {
        AdminUser selected = userTable.getSelectionModel().getSelectedItem();
        if (selected != null) userList.remove(selected);
    }

    @FXML
    private void handleFilterByRestaurant() {
        if (viewRestaurantFilter == null || filteredProducts == null) return;
        String selected = viewRestaurantFilter.getValue();
        if (selected == null || selected.equalsIgnoreCase("All Restaurants")) {
            filteredProducts.setPredicate(p -> true);
        } else {
            filteredProducts.setPredicate(p -> selected.equals(p.getRestaurant()));
        }
        updateFilteredCount();
    }

    private void updateFilteredCount() {
        javafx.application.Platform.runLater(() -> {
            int count = filteredProducts == null ? 0 : filteredProducts.size();
            if (filteredProductCountText != null) filteredProductCountText.setText(String.valueOf(count));
        });
    }

    @FXML
    private void handleEditProduct() {
        Product sel = productTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Alert a = new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie ein Produkt zum Bearbeiten aus.", ButtonType.OK);
            a.show();
            return;
        }

        // Populate the form fields so admin can edit and save
        if (newProductName != null) newProductName.setText(sel.getName());
        if (newProductPrice != null) newProductPrice.setText(String.valueOf(sel.getPrice()));
        if (productRestaurantChoice != null && sel.getRestaurant() != null) productRestaurantChoice.setValue(sel.getRestaurant());

        // Change Save button behavior: when pressed, update existing product if selection matches
        // We implement a simple approach: overwrite product with same id on save
    }

    // Save/update from the form (reused by handleCreateProduct)
    private void saveOrUpdateProductFromForm() {
        String name = newProductName.getText();
        String priceStr = newProductPrice.getText();
        if (name == null || name.trim().isEmpty()) return;
        try {
            double price = Double.parseDouble(priceStr);
            // Check if a product with same name exists (could use id mapping). We'll try to match selection in table
            Product sel = productTable.getSelectionModel().getSelectedItem();
            String restaurant = productRestaurantChoice != null ? productRestaurantChoice.getValue() : null;
            if (sel != null) {
                Product updated = new Product(sel.getId(), name, price, restaurant);
                int idx = productList.indexOf(sel);
                if (idx >= 0) DataManager.getProductList().set(idx, updated);
            } else {
                Product p = new Product("P" + (productList.size() + 101), name, price, restaurant);
                DataManager.addProduct(p);
            }
            // Clear
            newProductName.clear();
            newProductPrice.clear();
            if (productRestaurantChoice != null) productRestaurantChoice.getSelectionModel().clearSelection();

            // Ensure any active search/filter does not hide the newly added product:
            if (productSearchField != null && filteredProducts != null) {
                productSearchField.clear();
                filteredProducts.setPredicate(prod -> true);
            }

            // Clear any selection in the table so the new item appears without being filtered out by selection logic
            if (productTable != null) productTable.getSelectionModel().clearSelection();

            // Update UI counters and auxiliary list
            updateFilteredCount();
            handleRefreshProducts();
        } catch (NumberFormatException ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Ungültiger Preis.", ButtonType.OK);
            a.show();
        }
    }

    @FXML
    private void handleCreateProduct() {
        // Delegate to unified save/update handler
        saveOrUpdateProductFromForm();
    }

    @FXML
    private void handleDeleteProduct() {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected != null) DataManager.removeProduct(selected);
    }

    // Backwards-compatible no-op handlers for Create-Order buttons still present in Admin.fxml
    @FXML
    private void addBurger() { showInfo("Diese Aktion ist im Admin-Modus nicht verfügbar."); }

    @FXML
    private void addPizza() { showInfo("Diese Aktion ist im Admin-Modus nicht verfügbar."); }

    @FXML
    private void addDrink() { showInfo("Diese Aktion ist im Admin-Modus nicht verfügbar."); }

    @FXML
    private void handlePlaceOrder() { showInfo("Platzieren von Bestellungen ist im Admin-Modus nicht unterstützt."); }

    @FXML
    private void goToHistory() { showInfo("History-Anzeige via separatem Tab verfügbar."); }

    private void showInfo(String msg) {
        try {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Hinweis");
            a.setHeaderText(null);
            a.setContentText(msg);
            a.show();
        } catch (Exception ignored) {}
    }

    @FXML
    private void handleLogout() {
        try {
            Stage stage = (Stage) userTable.getScene().getWindow();
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

    // --- Helper Classes ---
    public static class AdminUser {
        private String id, username, password;
        private String accountRole; // neues Feld
        private double balance;

        public AdminUser(String id, String username, String password, double balance) {
            this.id = id;
            this.username = username;
            this.password = password;
            this.balance = balance;
            this.accountRole = "user";
        }

        public AdminUser(String id, String username, String password, String accountRole, double balance) {
            this.id = id;
            this.username = username;
            this.password = password;
            this.accountRole = accountRole;
            this.balance = balance;
        }

        public String getId() { return id; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getAccountRole() { return accountRole; }
        public void setAccountRole(String role) { this.accountRole = role; }
        public double getBalance() { return balance; }
        public void setBalance(double balance) { this.balance = balance; }
    }

    public static class AdminOrder {
        private String username;
        private double totalPrice;
        private String status;
        private java.sql.Timestamp createdAt;

        public AdminOrder(String username, double totalPrice, String status, java.sql.Timestamp createdAt) {
            this.username = username;
            this.totalPrice = totalPrice;
            this.status = status;
            this.createdAt = createdAt;
        }

        public String getUsername() { return username; }
        public double getTotalPrice() { return totalPrice; }
        public String getStatus() { return status; }
        public java.sql.Timestamp getCreatedAt() { return createdAt; }
    }

    // Diagnostic helper to print which @FXML fields are null (useful when FXMLLoader loads a different FXML/controller mismatch)
    private void validateFXMLInjections() {
        StringBuilder sb = new StringBuilder("FXML injection status:\n");
        sb.append(String.format("userTable=%s, newUserName=%s, newUserPassword=%s, amountToChange=%s\n",
                userTable==null?"null":"ok",
                newUserName==null?"null":"ok",
                newUserPassword==null?"null":"ok",
                amountToChange==null?"null":"ok"));
        sb.append(String.format("productTable=%s, newProductName=%s, productRestaurantChoice=%s\n",
                productTable==null?"null":"ok",
                newProductName==null?"null":"ok",
                productRestaurantChoice==null?"null":"ok"));
        System.out.println(sb.toString());
        // If important fields are null, show a warning in the console (don't interrupt user)
        if (userTable==null || newUserName==null || newUserPassword==null) {
            System.err.println("WARNING: Some FXML fields were not injected into AdminController. Check fx:controller and fx:id names in Admin.fxml and rebuild.");
        }
    }
}
