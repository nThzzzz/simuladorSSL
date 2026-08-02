package Model;

public class Robot {
    double x;
    double y;
    double theta;
    boolean isBlue;
    int id;

    public Robot(double x, double y, double theta, boolean isBlue, int id) {
        this.x = x;
        this.y = y;
        this.theta = theta;
        this.isBlue = isBlue;
        this.id = id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getTheta() {
        return theta;
    }

    public boolean isBlue() {
        return isBlue;
    }

    public int getId() {
        return id;
    }
}
