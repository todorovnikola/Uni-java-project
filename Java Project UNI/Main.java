import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
// --- ENUMS ---
enum Category {
    FOOD, NON_FOOD
}

// --- EXCEPTIONS ---
class InsufficientQuantityException extends Exception {
    public InsufficientQuantityException(String productName, int missingQty) {
        super("Недостатъчно количество от " + productName + ". Липсват: " + missingQty);
    }
}

// --- PRODUCT CLASS ---
class Product {
    private final String id;
    private final String name;
    private final double deliveryPrice;
    private final Category category;
    private final LocalDate expiryDate;
    private int quantity;

    public Product(String id, String name, double deliveryPrice, Category category, LocalDate expiryDate, int quantity) {
        this.id = id;
        this.name = name;
        this.deliveryPrice = deliveryPrice;
        this.category = category;
        this.expiryDate = expiryDate;
        this.quantity = quantity;
    }

    public double calculateSellingPrice(double markupPercent, int daysBeforeExpiryDiscount, double discountPercent) {
        if (expiryDate.isBefore(LocalDate.now())) {
            return 0;
        }
        double basePrice = deliveryPrice * (1 + markupPercent / 100);
        long daysLeft = LocalDate.now().until(expiryDate).getDays();
        if (daysLeft <= daysBeforeExpiryDiscount) {
            basePrice *= (1 - discountPercent / 100);
        }
        return basePrice;
    }

    public boolean isExpired() {
        return expiryDate.isBefore(LocalDate.now());
    }

    public String getName() { return name; }
    public String getId() { return id; }
    public int getQuantity() { return quantity; }
    public void reduceQuantity(int amount) throws InsufficientQuantityException {
        if (amount > quantity) {
            throw new InsufficientQuantityException(name, amount - quantity);
        }
        quantity -= amount;
    }
    public Category getCategory() { return category; }
    public double getDeliveryPrice() { return deliveryPrice; }
    public LocalDate getExpiryDate() { return expiryDate; }
}

// --- CASHIER ---
class Cashier {
    private final String id;
    private final String name;
    private final double salary;

    public Cashier(String id, String name, double salary) {
        this.id = id;
        this.name = name;
        this.salary = salary;
    }

    public String getName() { return name; }
    public String getId() { return id; }
    public double getSalary() { return salary; }

    @Override
    public String toString() {
        return name + " (ID: " + id + ")";
    }
}

// --- RECEIPT ---
class Receipt implements Serializable {
    private static int receiptCounter = 0;

    private final int number;
    private final Cashier cashier;
    private final LocalDateTime dateTime;
    private final Map<Product, Integer> items;
    private final double total;

    public Receipt(Cashier cashier, Map<Product, Integer> items, double total) {
        this.number = ++receiptCounter;
        this.cashier = cashier;
        this.dateTime = LocalDateTime.now();
        this.items = items;
        this.total = total;
    }

    public void saveToFile() throws IOException {
        String filename = "receipt_" + number + ".txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("Касова бележка #" + number);
            writer.println("Касиер: " + cashier);
            writer.println("Дата: " + dateTime);
            writer.println("Списък със стоки:");
            for (Map.Entry<Product, Integer> entry : items.entrySet()) {
                writer.printf("- %s x%d -> %.2f лв\n", entry.getKey().getName(), entry.getValue(), entry.getKey().getDeliveryPrice());
            }
            writer.printf("Общо: %.2f лв\n", total);
        }

        // Сериализация
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("receipt_" + number + ".ser"))) {
            out.writeObject(this);
        }
    }

    public static int getReceiptCount() {
        return receiptCounter;
    }

    public double getTotal() { return total; }
}

// --- STORE ---
class Store {
    private final List<Product> products = new ArrayList<>();
    private final List<Cashier> cashiers = new ArrayList<>();
    private final List<Receipt> receipts = new ArrayList<>();
    private final double foodMarkup;
    private final double nonFoodMarkup;
    private final int expiryThresholdDays;
    private final double discountPercent;

    public Store(double foodMarkup, double nonFoodMarkup, int expiryThresholdDays, double discountPercent) {
        this.foodMarkup = foodMarkup;
        this.nonFoodMarkup = nonFoodMarkup;
        this.expiryThresholdDays = expiryThresholdDays;
        this.discountPercent = discountPercent;
    }

    public void addProduct(Product product) {
        products.add(product);
    }

    public void addCashier(Cashier cashier) {
        cashiers.add(cashier);
    }

    public Receipt sellProducts(Cashier cashier, Map<String, Integer> requestedProducts) throws InsufficientQuantityException, IOException {
        Map<Product, Integer> sold = new HashMap<>();
        double total = 0;

        for (Map.Entry<String, Integer> entry : requestedProducts.entrySet()) {
            Product product = products.stream().filter(p -> p.getId().equals(entry.getKey())).findFirst().orElse(null);
            if (product == null || product.isExpired()) continue;
            product.reduceQuantity(entry.getValue());
            double price = product.calculateSellingPrice(
                    product.getCategory() == Category.FOOD ? foodMarkup : nonFoodMarkup,
                    expiryThresholdDays,
                    discountPercent
            );
            sold.put(product, entry.getValue());
            total += price * entry.getValue();
        }

        Receipt receipt = new Receipt(cashier, sold, total);
        receipt.saveToFile();
        receipts.add(receipt);
        return receipt;
    }

    public double calculateExpenses() {
        double salaryCosts = cashiers.stream().mapToDouble(Cashier::getSalary).sum();
        double deliveryCosts = products.stream().mapToDouble(p -> p.getDeliveryPrice() * p.getQuantity()).sum();
        return salaryCosts + deliveryCosts;
    }

    public double calculateRevenue() {
        return receipts.stream().mapToDouble(Receipt::getTotal).sum();
    }

    public double calculateProfit() {
        return calculateRevenue() - calculateExpenses();
    }

    public int getReceiptCount() {
        return receipts.size();
    }
}
// --- MAIN ---
public class Main {
    public static void main(String[] args) {
        Store store = new Store(20, 30, 5, 10);
        Cashier cashier = new Cashier("001", "Иван Иванов", 1200);
        store.addCashier(cashier);

        Product milk = new Product("P1", "Мляко", 1.20, Category.FOOD, LocalDate.now().plusDays(3), 10);
        Product soap = new Product("P2", "Сапун", 0.80, Category.NON_FOOD, LocalDate.now().plusMonths(6), 5);

        store.addProduct(milk);
        store.addProduct(soap);

        try {
            Map<String, Integer> order = new HashMap<>();
            order.put("P1", 2);
            order.put("P2", 1);

            store.sellProducts(cashier, order); // Use the cashier object already created

        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

        System.out.println("Оборот: " + store.calculateRevenue());
        System.out.println("Разходи: " + store.calculateExpenses());
        System.out.println("Печалба: " + store.calculateProfit());
        System.out.println("Издадени касови бележки: " + store.getReceiptCount());
    }
}