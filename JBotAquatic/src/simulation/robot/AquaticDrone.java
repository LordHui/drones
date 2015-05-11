package simulation.robot;

import java.util.ArrayList;
import java.util.List;
import mathutils.MathUtils;
import mathutils.Vector2d;
import net.jafama.FastMath;
import network.SimulatedBroadcastHandler;
import network.messageproviders.CompassMessageProvider;
import network.messageproviders.GPSMessageProvider;
import simpletestbehaviors.ChangeWaypointCIBehavior;
import simulation.Simulator;
import simulation.physicalobjects.PhysicalObject;
import simulation.robot.actuator.PropellersActuator;
import simulation.robot.actuators.Actuator;
import simulation.robot.sensors.CompassSensor;
import simulation.util.Arguments;
import simulation.util.ArgumentsAnnotation;
import commoninterface.AquaticDroneCI;
import commoninterface.CIBehavior;
import commoninterface.CISensor;
import commoninterface.messageproviders.BehaviorMessageProvider;
import commoninterface.messageproviders.EntitiesMessageProvider;
import commoninterface.messageproviders.EntityMessageProvider;
import commoninterface.messageproviders.LogMessageProvider;
import commoninterface.messageproviders.NeuralActivationsMessageProvider;
import commoninterface.network.ConnectionHandler;
import commoninterface.network.broadcast.BroadcastHandler;
import commoninterface.network.broadcast.BroadcastMessage;
import commoninterface.network.broadcast.HeartbeatBroadcastMessage;
import commoninterface.network.broadcast.PositionBroadcastMessage;
import commoninterface.network.messages.Message;
import commoninterface.network.messages.MessageProvider;
import commoninterface.objects.Entity;
import commoninterface.objects.Waypoint;
import commoninterface.utils.CIArguments;
import commoninterface.utils.CoordinateUtilities;
import commoninterface.utils.RobotLogger;
import commoninterface.utils.jcoord.LatLon;

public class AquaticDrone extends DifferentialDriveRobot implements AquaticDroneCI{

	private double frictionConstant = 0.21;//0.05
	private double accelarationConstant = 0.20;//0.1
	private Vector2d velocity = new Vector2d();
	private Simulator simulator;
	private ArrayList<Entity> entities = new ArrayList<Entity>();
	private ArrayList<CISensor> cisensors = new ArrayList<CISensor>();
	private PropellersActuator propellers;
	private SimulatedBroadcastHandler broadcastHandler;
	private Waypoint activeWaypoint;
	
	@ArgumentsAnnotation(name="gpserror", defaultValue = "0.0")
	private double gpsError = 0;
	
	@ArgumentsAnnotation(name="compasserror", defaultValue = "0.0")
	private double compassError = 0;
	
	@ArgumentsAnnotation(name="commrange", defaultValue = "0.0")
	private double commRange = 0.0;
	
	private ArrayList<MessageProvider> messageProviders;
	private ArrayList<CIBehavior> alwaysActiveBehaviors = new ArrayList<CIBehavior>();
	private CIBehavior activeBehavior;
	
	private RobotLogger logger;
	
	public AquaticDrone(Simulator simulator, Arguments args) {
		super(simulator, args);
		this.simulator = simulator;
		
		ArrayList<BroadcastMessage> broadcastMessages = new ArrayList<BroadcastMessage>();
		broadcastMessages.add(new HeartbeatBroadcastMessage(this));
		broadcastMessages.add(new PositionBroadcastMessage(this));
		broadcastHandler = new SimulatedBroadcastHandler(this, broadcastMessages);
		
		gpsError = args.getArgumentAsDoubleOrSetDefault("gpserror", gpsError);
		compassError = args.getArgumentAsDoubleOrSetDefault("compasserror", compassError);
		commRange = args.getArgumentAsDoubleOrSetDefault("commrange", commRange);
		
		if(commRange == 0)
			throw new RuntimeException("[AquaticDrone] CommRange is at 0!");
		
		alwaysActiveBehaviors.add(new ChangeWaypointCIBehavior(new CIArguments(""), this));
		
		sensors.add(new CompassSensor(simulator, sensors.size()+1, this, args));
		actuators.add(new PropellersActuator(simulator, actuators.size()+1, args));
	}
	
	@Override
	public void shutdown() {}
	
	@Override
	public void setWheelSpeed(double left, double right) {
		super.setWheelSpeed(left, right);
	}
	
	@Override
	public void updateSensors(double simulationStep, ArrayList<PhysicalObject> teleported) {
		super.updateSensors(simulationStep, teleported);
		for(CIBehavior b : alwaysActiveBehaviors)
			b.step(simulationStep);
		
		if(activeBehavior != null) {
			activeBehavior.step(simulationStep);
		}
	}

	public void setMotorSpeeds(double leftMotorPercentage, double rightMotorPercentage) {
		if(propellers == null)
			propellers = (PropellersActuator) getActuatorByType(PropellersActuator.class);
		
		propellers.setLeftPercentage(leftMotorPercentage);
		propellers.setRightPercentage(rightMotorPercentage);
	}

	@Override
	public double getCompassOrientationInDegrees() {
		CompassSensor compassSensor = (CompassSensor) getSensorByType(CompassSensor.class);
		double heading = (360-(compassSensor.getSensorReading(0) * 360) + 90) % 360;
		double error = compassError*simulator.getRandom().nextDouble()*2-compassError;
		return heading+error;
	}

	@Override
	public LatLon getGPSLatLon() {
		
		LatLon latLon = CoordinateUtilities.cartesianToGPS(getPosition().getX(), getPosition().getY());
		
		if(gpsError > 0) {
			
			commoninterface.mathutils.Vector2d pos = CoordinateUtilities.GPSToCartesian(latLon);
			double radius = simulator.getRandom().nextDouble()*gpsError;
			double angle = simulator.getRandom().nextDouble()*Math.PI*2;

			pos.setX(pos.getX()+radius*Math.cos(angle));
			pos.setY(pos.getY()+radius*Math.sin(angle));
			
			return CoordinateUtilities.cartesianToGPS(pos);
		}
		
		return latLon;
	}
	
	@Override
	public double getGPSOrientationInDegrees() {
		return getCompassOrientationInDegrees();
	}

	@Override
	public double getTimeSinceStart() {
		return ((double)simulator.getTime())/10.0;
	}
	
	@Override
	public void setLed(int index, commoninterface.LedState state) {
		LedState robotState;
		
		switch(state) {
			case BLINKING:
				robotState = LedState.BLINKING;
				break;
			case OFF:
				robotState = LedState.OFF;
				break;
			case ON:
				robotState = LedState.ON;
				break;
			default:
				robotState = LedState.OFF;
		}
		
		setLedState(robotState);
	}
	
	private double motorModel(double d) {
		return 0.0048*Math.exp(2.4912*Math.abs(d*2)) - 0.0048;
	}
	
	@Override
	public void updateActuators(Double time, double timeDelta) {

		if(stopTimestep > 0) {
			rightWheelSpeed = 0;
			leftWheelSpeed = 0;
			stopTimestep--;
		}
		
		double lw = Math.signum(rightWheelSpeed-leftWheelSpeed);
		
		orientation = MathUtils.modPI2(orientation + motorModel(rightWheelSpeed-leftWheelSpeed)*lw);
		
		double accelDirection = (rightWheelSpeed+leftWheelSpeed) < 0 ? -1 : 1;
		double lengthOfAcc = accelarationConstant * (leftWheelSpeed + rightWheelSpeed);
		
		//Backwards motion should be slower. This value here is just an
		//estimate, and should be improved by taking real world samples
		if(accelDirection < 0)
			lengthOfAcc*=0.2;
		
		Vector2d accelaration = new Vector2d(lengthOfAcc * FastMath.cosQuick(orientation), lengthOfAcc * FastMath.sinQuick(orientation));
		
		velocity.setX(velocity.getX() * (1 - frictionConstant));
		velocity.setY(velocity.getY() * (1 - frictionConstant));    
		
		velocity.add(accelaration);
		
		position.set(
				position.getX() + timeDelta * velocity.getX(), 
				position.getY() + timeDelta * velocity.getY());
		
		for (Actuator actuator : actuators) {
			actuator.apply(this);
		}
		
		broadcastHandler.update(time);

	}
	
	public double getCommRange() {
		return commRange;
	}

	@Override
	public void begin(CIArguments args) {
		
	}

	@Override
	public ArrayList<Entity> getEntities() {
		return entities;
	}
	
	@Override
	public ArrayList<CISensor> getCISensors() {
		return cisensors;
	}
	
	@Override
	public String getNetworkAddress() {
		return getId()+":"+getId()+":"+getId()+":"+getId();
	}
	
	@Override
	public BroadcastHandler getBroadcastHandler() {
		return broadcastHandler;
	}
	
	public Simulator getSimulator() {
		return simulator;
	}
	
	@Override
	public Waypoint getActiveWaypoint() {
		return activeWaypoint;
	}
	
	@Override
	public void setActiveWaypoint(Waypoint wp) {
		this.activeWaypoint = wp;
	}
	
	@Override
	public String getInitMessages() {
		return "Simulated drone with ID "+getId();
	}
	
	@Override
	public void processInformationRequest(Message request, ConnectionHandler conn) {
		Message response = null;
		
		for (MessageProvider p : getMessageProviders()) {
			response = p.getMessage(request);
			
			if (response != null)
				break;
		}
		
		if(conn != null && response != null) {
			conn.sendData(response);
		}
	}
	
	@Override
	public void reset() {
		leftWheelSpeed = 0;
		rightWheelSpeed = 0;
	}

	@Override
	public RobotLogger getLogger() {
		return logger;
	}
	
	@Override
	public List<MessageProvider> getMessageProviders() {
		
		//We only do this here because messageProviders might not be necessary
		//most of the times, and it saves simulation time
		if(messageProviders == null) {
			initMessageProviders();
		}
		
		return messageProviders;
	}
	
	private void initMessageProviders() {
		messageProviders = new ArrayList<MessageProvider>();
		
		messageProviders.add(new CompassMessageProvider(this));
		messageProviders.add(new GPSMessageProvider(this));
		messageProviders.add(new EntityMessageProvider(this));
		messageProviders.add(new EntitiesMessageProvider(this));
		messageProviders.add(new BehaviorMessageProvider(this));
		messageProviders.add(new NeuralActivationsMessageProvider(this));
		messageProviders.add(new LogMessageProvider(this));
	}
	
	@Override
	public String getStatus() {
		if(getActiveBehavior() != null)
			return "Running behavior "+getActiveBehavior().getClass().getSimpleName();
		return "Idle";
	}
	
	@Override
	public void startBehavior(CIBehavior b) {
		stopActiveBehavior();
		activeBehavior = b;
		activeBehavior.start();
		log("Starting CIBehavior "+b.getClass().getSimpleName());
	}

	@Override
	public void stopActiveBehavior() {
		if (activeBehavior != null) {
			activeBehavior.cleanUp();
			log("Stopping CIBehavior "+activeBehavior.getClass().getSimpleName());
			activeBehavior = null;
			setMotorSpeeds(0, 0);
		}
	}
	
	@Override
	public CIBehavior getActiveBehavior() {
		return activeBehavior;
	}
	
	private void log(String msg) {
		if(logger != null)
			logger.logMessage(msg);
	}
	
}