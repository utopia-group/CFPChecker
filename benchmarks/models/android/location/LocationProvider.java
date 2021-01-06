package android.location;

public class LocationProvider {
    private String name;

    protected LocationProvider(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public int getId()
    {
        return nd$int();
    }

    private int nd$int()
    {
        return 0;
    }
}

