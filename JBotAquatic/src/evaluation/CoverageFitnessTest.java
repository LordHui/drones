package evaluation;

import commoninterface.entities.Entity;
import commoninterface.entities.GeoFence;
import commoninterface.entities.Waypoint;
import commoninterface.utils.CoordinateUtilities;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import mathutils.Vector2d;
import org.jfree.graphics2d.svg.SVGGraphics2D;
import org.jfree.graphics2d.svg.SVGUtils;
import simulation.Simulator;
import simulation.physicalobjects.Line;
import simulation.physicalobjects.PhysicalObject;
import simulation.physicalobjects.PhysicalObjectType;
import simulation.robot.AquaticDrone;
import simulation.robot.Robot;
import simulation.util.Arguments;

public class CoverageFitnessTest extends AvoidCollisionsFunction {

    private boolean isSetup = false;
    private double[][] coverage;
    private double resolution = 1;
    private double width = 5, height = 5;
    private final double decrease = 0.001; //1000 steps to go from 1.0 to 0.0
    private double accum = 0;
    private List<Line> lines;

    private double max = 0;
    private double distance = 10;
    private double steps = 0;

    private int snapshotFrequency = 0;
    private double scale = 5;
    private int margin = 50;
    private boolean instant = false;

    public CoverageFitnessTest(Arguments args) {
        super(args);
        resolution = args.getArgumentAsDoubleOrSetDefault("resolution", resolution);
        distance = args.getArgumentAsDoubleOrSetDefault("distance", distance);
        snapshotFrequency = args.getArgumentAsIntOrSetDefault("snapshotfrequency", snapshotFrequency);
        instant = args.getFlagIsTrue("instant");
        scale = args.getArgumentAsDoubleOrSetDefault("scale", scale);
        margin = args.getArgumentAsIntOrSetDefault("imagemargin", margin);
    }

    public void setup(Simulator simulator) {
        // SET GEOFENCE
        AquaticDrone ad = (AquaticDrone) simulator.getRobots().get(0);
        GeoFence fence = null;
        for (Entity e : ad.getEntities()) {
            if (e instanceof GeoFence) {
                fence = (GeoFence) e;
                break;
            }
        }
        lines = getLines(fence.getWaypoints(), simulator);

        double maxXAbs = 0, maxYAbs = 0;

        for (Line l : lines) {
            maxXAbs = Math.max(maxXAbs, Math.abs(l.getPointA().x));
            maxYAbs = Math.max(maxYAbs, Math.abs(l.getPointA().y));
        }

        width = Math.max(maxXAbs, maxYAbs) * 2;
        height = width;
        coverage = new double[(int) (height / resolution)][(int) (width / resolution)];
        steps = simulator.getEnvironment().getSteps();
        for (int y = 0; y < coverage.length; y++) {
            for (int x = 0; x < coverage[y].length; x++) {
                double coordX = (x - coverage[y].length / 2) * resolution;
                double coordY = (y - coverage.length / 2) * resolution;
                if (!insideLines(new Vector2d(coordX, coordY), simulator)) {
                    coverage[y][x] = -1;
                } else {
                    max++;
                }
            }
        }
    }

    public boolean insideLines(Vector2d v, Simulator sim) {
        int count = 0;
        for (Line l : lines) {
            if (l.intersectsWithLineSegment(v, new Vector2d(0, -Integer.MAX_VALUE)) != null) {
                count++;
            }
        }
        return count % 2 != 0;
    }

    protected List<Line> getLines(LinkedList<Waypoint> waypoints, Simulator simulator) {
        List<Line> linesList = new ArrayList<Line>();
        for (int i = 1; i < waypoints.size(); i++) {

            Waypoint wa = waypoints.get(i - 1);
            Waypoint wb = waypoints.get(i);
            commoninterface.mathutils.Vector2d va = CoordinateUtilities.GPSToCartesian(wa.getLatLon());
            commoninterface.mathutils.Vector2d vb = CoordinateUtilities.GPSToCartesian(wb.getLatLon());

            simulation.physicalobjects.Line l = new simulation.physicalobjects.Line(simulator, "line" + i, va.getX(), va.getY(), vb.getX(), vb.getY());
            linesList.add(l);
        }

        Waypoint wa = waypoints.get(waypoints.size() - 1);
        Waypoint wb = waypoints.get(0);
        commoninterface.mathutils.Vector2d va = CoordinateUtilities.GPSToCartesian(wa.getLatLon());
        commoninterface.mathutils.Vector2d vb = CoordinateUtilities.GPSToCartesian(wb.getLatLon());

        simulation.physicalobjects.Line l = new simulation.physicalobjects.Line(simulator, "line0", va.getX(), va.getY(), vb.getX(), vb.getY());
        linesList.add(l);
        return linesList;
    }

    @Override
    public void update(Simulator simulator) {
        if (!isSetup) {
            setup(simulator);
            isSetup = true;
        }

        ArrayList<Robot> robots = simulator.getRobots();

        double sum = 0;

        for (int y = 0; y < coverage.length; y++) {
            for (int x = 0; x < coverage[y].length; x++) {

                if (coverage[y][x] == -1) {
                    continue;
                }

                if (coverage[y][x] > 0) {
                    if (coverage[y][x] <= 1) {
                        coverage[y][x] -= decrease;
                        if (coverage[y][x] < 0) {
                            coverage[y][x] = 0;
                        }
                    }
                }

                if (coverage[y][x] > 0) {
                    sum += coverage[y][x];
                }
            }
        }

        for (Robot r : robots) {
            if (r.isEnabled()) {
                AquaticDrone ad = (AquaticDrone) r;
                if (insideLines(r.getPosition(), simulator)) {

                    double rX = ad.getPosition().getX();
                    double rY = ad.getPosition().getY();

                    double minX = rX - distance;
                    double minY = rY - distance;

                    double maxX = rX + distance;
                    double maxY = rY + distance;

                    int pMinX = (int) ((minX / resolution) + coverage.length / 2);
                    int pMinY = (int) ((minY / resolution) + coverage[0].length / 2);

                    double pMaxX = (maxX / resolution) + coverage.length / 2;
                    double pMaxY = (maxY / resolution) + coverage[0].length / 2;

                    for (int y = pMinY; y < pMaxY; y++) {

                        if (y >= coverage.length || y < 0) {
                            continue;
                        }

                        for (int x = pMinX; x < pMaxX; x++) {

                            if (x >= coverage[y].length || x < 0) {
                                continue;
                            }

                            if (coverage[y][x] == -1) {
                                continue;
                            }
                            coverage[y][x] = 1.0;
                        }
                    }
                } else if (ad.isInvolvedInCollison()) {
                    r.setEnabled(false);
                }
            }
        }

        if (instant) {
            fitness = sum / max;
        } else {
            accum += ((sum / max) / steps);
            fitness = accum;
        }

        if (snapshotFrequency > 0 && (int) (double) simulator.getTime() % snapshotFrequency == 0) {
            try {
                File folder = new File("heatmaps");
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                drawHeatmap(new File(folder, simulator.hashCode() + "_" + simulator.getTime() + ".svg"), simulator);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        super.update(simulator);
    }

    protected void drawHeatmap(File out, Simulator sim) throws IOException {
        // create canvas
        int w = (int) (width * scale) + margin * 2;
        int h = (int) (height * scale) + margin * 2;
        SVGGraphics2D gr = new SVGGraphics2D(w, h);
        gr.setPaint(Color.WHITE);
        gr.fillRect(0, 0, w, h);

        // draw heatmap
        for (int y = coverage.length - 1; y >= 0; y--) {
            for (int x = 0; x < coverage[y].length; x++) {
                int minX = (int) Math.round((x * resolution) * scale);
                int minY = (int) Math.round((y * resolution) * scale);
                int maxX = (int) Math.round(((x + 1) * resolution) * scale);
                int maxY = (int) Math.round(((y + 1) * resolution) * scale);

                if (coverage[y][x] != -1) {
                    Color c = new Color(1 - (float) coverage[y][x], 1 - (float) coverage[y][x], 1 - (float) coverage[y][x]);
                    gr.setPaint(c);
                    gr.fillRect(minX + margin, minY + margin, maxX - minX, maxY - minY);
                }
            }
        }

        // draw robots
        gr.setPaint(Color.RED);
        for (Robot r : sim.getRobots()) {
            Vector2d pos = r.getPosition();
            int size = (int) Math.round(r.getRadius() * 2 * scale);
            int x = (int) Math.round((pos.x + width / 2) * scale - size / 2d);
            int y = (int) Math.round((pos.y + height / 2) * scale - size / 2d);
            gr.fillOval(x + margin, y + margin, size, size);
        }

        // draw bounds
        gr.setPaint(Color.BLUE);
        for (Line l : lines) {
            Vector2d pointA = l.getPointA();
            Vector2d pointB = l.getPointB();
            int xa = (int) ((pointA.x + width / 2) * scale);
            int xb = (int) ((pointB.x + width / 2) * scale);
            int ya = (int) ((pointA.y + height / 2) * scale);
            int yb = (int) ((pointB.y + height / 2) * scale);
            gr.drawLine(xa + margin, ya + margin, xb + margin, yb + margin);
        }

        // write file
        SVGUtils.writeToSVG(out, gr.getSVGElement());
    }

    @Override
    public double getFitness() {
        return fitness;
    }
}