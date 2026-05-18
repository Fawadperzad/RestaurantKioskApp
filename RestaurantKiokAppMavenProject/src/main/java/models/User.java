package models;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Modell-Klasse für einen Benutzer.
 * Enthält Informationen über Name, Passwort und das aktuelle Guthaben.
 */
public class User {
    private final StringProperty username;
    private final StringProperty password;
    private final DoubleProperty balance;

    public User(String username, String password, double initialBalance) {
        this.username = new SimpleStringProperty(username);
        this.password = new SimpleStringProperty(password);
        this.balance = new SimpleDoubleProperty(initialBalance);
    }

    // Getter und Setter für den Benutzernamen
    public String getUsername() { return username.get(); }
    public StringProperty usernameProperty() { return username; }

    // Getter und Setter für das Passwort
    public String getPassword() { return password.get(); }

    // Getter und Setter für das Guthaben
    public double getBalance() { return balance.get(); }
    public void setBalance(double amount) { this.balance.set(amount); }
    public DoubleProperty balanceProperty() { return balance; }

    // Erhöht das Guthaben
    public void addBalance(double amount) {
        setBalance(getBalance() + amount);
    }

    @Override
    public String toString() {
        return getUsername() + " - Guthaben: " + String.format("%.2f", getBalance()) + "€";
    }
}