package models;

/**
 * Diese Klasse ist der Bauplan für ein Produkt.
 */
public class Product {
    private String id; // optional ID
    private String name;
    private double price;
    private String restaurant; // optional restaurant assignment

    public Product(String name, double price) {
        this.name = name;
        this.price = price;
    }

    public Product(String id, String name, double price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public Product(String id, String name, double price, String restaurant) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.restaurant = restaurant;
    }

    public Product(String name, double price, String restaurant) {
        this.name = name;
        this.price = price;
        this.restaurant = restaurant;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getPrice() { return price; }
    public String getRestaurant() { return restaurant; }

    @Override
    public String toString() {
        return (id != null ? (id + " - ") : "") + name + " - " + String.format("%.2f", price) + "€" + (restaurant != null ? (" ("+restaurant+")") : "");
    }
}