package edu.canteenx.model;

public abstract class User {
    private final long id;
    private final String name, email;
    protected User(long id, String name, String email) { this.id=id; this.name=name; this.email=email; }
    public long getId(){return id;} public String getName(){return name;} public String getEmail(){return email;}
    public abstract String roleName();
    public abstract String dashboardMessage();
}
