package fr.seblaporte.kitchenvault.entity;

public enum ShoppingCategory {
    PRODUCE("produce"),
    MEAT("meat"),
    DAIRY("dairy"),
    BAKERY("bakery"),
    GROCERY("grocery"),
    FROZEN("frozen"),
    SPICES("spices"),
    OTHER("other");

    private final String value;

    ShoppingCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ShoppingCategory fromValue(String value) {
        for (ShoppingCategory cat : values()) {
            if (cat.value.equalsIgnoreCase(value)) {
                return cat;
            }
        }
        return OTHER;
    }
}
