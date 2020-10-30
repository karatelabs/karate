package com.intuit.karate.demo.domain;

public class Product {
    private int id;
    private String name;
    private float price;
    private String[] tags;
    private Dimension dimensions;
    private Location warehouseLocation;

    public Product() {
    }

    public Product(int id, String name, float price, String[] tags, Dimension dimensions, Location warehouseLocation) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.tags = tags;
        this.dimensions = dimensions;
        this.warehouseLocation = warehouseLocation;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public float getPrice() {
        return price;
    }

    public void setPrice(float price) {
        this.price = price;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public Dimension getDimensions() {
        return dimensions;
    }

    public void setDimensions(Dimension dimensions) {
        this.dimensions = dimensions;
    }

    public Location getWarehouseLocation() {
        return warehouseLocation;
    }

    public void setWarehouseLocation(Location warehouseLocation) {
        this.warehouseLocation = warehouseLocation;
    }
}
