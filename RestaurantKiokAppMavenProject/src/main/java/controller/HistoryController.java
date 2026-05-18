package controller;

import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import utils.DBConnection;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class HistoryController {

    @FXML private ListView<String> historyListView;
    private String currentUsername;
    private int currentUserId = -1;

    /**
     * Setzt die User-Informationen und lädt die Historie direkt aus der Datenbank.
     */
    public void setUserInfo(String username) {
        this.currentUsername = username;

        // 1. Zuerst die User-ID finden (da deine Tabelle 'orders' user_id nutzt)
        loadUserIdAndHistory(username);
    }

    private void loadUserIdAndHistory(String username) {
        List<String> historyItems = new ArrayList<>();

        String userSql = "SELECT id FROM users WHERE username = ?";
        String historySql = "SELECT total_price, status, created_at FROM orders WHERE user_id = ? ORDER BY created_at DESC";

        try (Connection conn = DBConnection.getConnection()) {
            // A: ID holen
            try (PreparedStatement userPstmt = conn.prepareStatement(userSql)) {
                userPstmt.setString(1, username);
                ResultSet rsUser = userPstmt.executeQuery();
                if (rsUser.next()) {
                    currentUserId = rsUser.getInt("id");
                }
            }

            // B: Wenn ID gefunden, Bestellungen laden
            if (currentUserId != -1) {
                try (PreparedStatement historyPstmt = conn.prepareStatement(historySql)) {
                    historyPstmt.setInt(1, currentUserId);
                    ResultSet rsOrders = historyPstmt.executeQuery();

                    while (rsOrders.next()) {
                        double price = rsOrders.getDouble("total_price");
                        String status = rsOrders.getString("status");
                        Timestamp date = rsOrders.getTimestamp("created_at");

                        String entry = String.format("[%s] Betrag: %.2f € | Status: %s",
                                date.toString(), price, status);
                        historyItems.add(entry);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Ergänze in-memory Orders aus AdminController (falls vorhanden)
        try {
            java.util.List<String> mem = controller.AdminController.getOrdersForUser(username);
            if (mem != null && !mem.isEmpty()) {
                historyItems.addAll(mem);
            }
        } catch (Exception ex) {
            // defensive: nicht kritisch
            System.err.println("Failed to load in-memory orders: " + ex.getMessage());
        }

        // Anzeige im ListView aktualisieren
        if (!historyItems.isEmpty()) {
            historyListView.setItems(FXCollections.observableArrayList(historyItems));
        } else {
            historyListView.setPlaceholder(new Label("Du hast noch keine Bestellungen in der Datenbank."));
        }
    }

    @FXML
    public void goToMenu(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Order.fxml"));
            Parent root = loader.load();

            OrderController orderCtrl = loader.getController();
            if (orderCtrl != null) {
                orderCtrl.setUserInfo(currentUsername);
            }

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}