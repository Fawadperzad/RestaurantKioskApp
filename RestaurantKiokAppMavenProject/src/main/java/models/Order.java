package models;

import java.sql.Timestamp;

public class Order {
    private int id;
    private int userId;
    private double totalPrice;
    private String status;
    private Timestamp createdAt;

    public Order(int id, int userId, double totalPrice, String status, Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.totalPrice = totalPrice;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Zusätzlicher leichter Konstruktor für Testzwecke / In-Memory-Daten
    public Order(String username, String productName, double price) {
        // Für In-Memory-Testbestellungen speichern wir den Preis als totalPrice
        this.id = -1; // nicht gesetzt
        this.userId = -1; // nicht gesetzt (könnte durch Lookup gefüllt werden)
        this.totalPrice = price;
        this.status = "CREATED";
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    // Getters and Setters
    public int getId() { return id; }
    public double getTotalPrice() { return totalPrice; }
    public String getStatus() { return status; }
}
