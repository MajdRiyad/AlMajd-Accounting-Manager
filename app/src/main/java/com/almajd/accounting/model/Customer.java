package com.almajd.accounting.model;

public class Customer {

    private String storeName;
    private String ownerName;
    private String location;
    private String phone;
    private String createdAt;

    public Customer() {
    }

    public Customer(String storeName, String ownerName, String location, String phone, String createdAt) {
        this.storeName = storeName;
        this.ownerName = ownerName;
        this.location = location;
        this.phone = phone;
        this.createdAt = createdAt;
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
