package models;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Zentrale Datenhaltung (in-memory) für Produkte, Benutzer und Bestellungen.
 * Controllers sollten diese Liste verwenden, damit Änderungen sofort in allen Views sichtbar sind.
 */
public class DataManager {
    private static final ObservableList<Product> productList = FXCollections.observableArrayList();
    private static final ObservableList<User> userList = FXCollections.observableArrayList();
    private static final ObservableList<Order> orderList = FXCollections.observableArrayList();

    public static ObservableList<Product> getProductList() { return productList; }
    public static ObservableList<User> getUserList() { return userList; }
    public static ObservableList<Order> getOrderList() { return orderList; }

    // Convenience helpers
    public static void addProduct(Product p) { productList.add(p); }
    public static void removeProduct(Product p) { productList.remove(p); }

    public static void addUser(User u) { userList.add(u); }
    public static void removeUser(User u) { userList.remove(u); }

    public static void addOrder(Order o) { orderList.add(o); }

    static {
        // sample data
        userList.add(new User("admin", "admin123", 0.0));
        userList.add(new User("testuser", "1234", 50.0));

        productList.add(new Product("P100", "Water", 1.50));
        productList.add(new Product("P101", "Lemonade", 1.80));

        orderList.add(new Order("testuser", "Lemonade", 1.80));
    }
}
