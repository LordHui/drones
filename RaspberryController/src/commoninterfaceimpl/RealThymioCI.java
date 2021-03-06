package commoninterfaceimpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import commoninterface.CIBehavior;
import commoninterface.CISensor;
import commoninterface.ThymioCI;
import commoninterface.entities.Entity;
import commoninterface.mathutils.Vector2d;
import commoninterface.messageproviders.BehaviorMessageProvider;
import commoninterface.messageproviders.EntitiesMessageProvider;
import commoninterface.messageproviders.EntityMessageProvider;
import commoninterface.messageproviders.LogMessageProvider;
import commoninterface.messageproviders.NeuralActivationsMessageProvider;
import commoninterface.messageproviders.ThymioVirtualPositionMessageProvider;
import commoninterface.network.CommandConnectionListener;
import commoninterface.network.ConnectionHandler;
import commoninterface.network.ConnectionListener;
import commoninterface.network.ControllerMessageHandler;
import commoninterface.network.MotorConnectionListener;
import commoninterface.network.NetworkUtils;
import commoninterface.network.broadcast.BroadcastHandler;
import commoninterface.network.broadcast.BroadcastMessage;
import commoninterface.network.broadcast.HeartbeatBroadcastMessage;
import commoninterface.network.broadcast.SharedThymioBroadcastMessage;
import commoninterface.network.messages.Message;
import commoninterface.network.messages.MessageProvider;
import commoninterface.utils.CIArguments;
import commoninterface.utils.RobotLogger;
import commoninterface.utils.logger.LogCodex;
import commoninterface.utils.logger.LogCodex.LogType;
import io.SystemStatusMessageProvider;
import io.ThymioIOManager;
import io.input.ControllerInput;
import network.broadcast.RealBroadcastHandler;
import utils.ThymioFileLogger;

public class RealThymioCI extends Thread implements ThymioCI {

	private static long CYCLE_TIME = 100;// in miliseconds

	private String status = "";
	private String initMessages = "\n";
	private ThymioIOManager ioManager;
	private ControllerMessageHandler messageHandler;

	private ConnectionListener connectionListener;
	private MotorConnectionListener motorConnectionListener;
	private CommandConnectionListener commandConnectionListener;
	private BroadcastHandler broadcastHandler;

	private List<MessageProvider> messageProviders = new ArrayList<MessageProvider>();
	private ArrayList<CISensor> cisensors = new ArrayList<CISensor>();

	private long startTimeInMillis;
	private double timestep = 0;
	private double behaviorTimeStep = 0;
	private double leftSpeed = 0;
	private double rightSpeed = 0;

	private boolean startBehavior;

	private CIBehavior activeBehavior = null;
	private ArrayList<Entity> entities = new ArrayList<Entity>();

	private Vector2d virtualPosition;
	private Double virtualOrientation;

	private RobotLogger logger;
	private RobotLogger entityLogger;

	@Override
	public void begin(HashMap<String, CIArguments> args) {
		this.startTimeInMillis = System.currentTimeMillis();

		addShutdownHooks();

		initIO(args.get("--io"));
		initMessageProviders();
		initConnections();

		messageHandler = new ControllerMessageHandler(this);
		messageHandler.start();

		setStatus("Running!\n");

		logger.logMessage(LogCodex.encodeLog(LogType.MESSAGE, initMessages));
	}

	@Override
	public void run() {
		while (true) {

			long lastCycleTime = System.currentTimeMillis();
			CIBehavior current = activeBehavior;
			if (current != null) {
				if (startBehavior)
					behaviorTimeStep = 0;

				current.step(behaviorTimeStep);

				if (startBehavior)
					startBehavior = false;

				if (current.getTerminateBehavior()) {
					stopActiveBehavior();
				}
			}

			ioManager.setMotorSpeeds(leftSpeed, rightSpeed);

			if (broadcastHandler != null)
				broadcastHandler.update(timestep);

			long timeToSleep = CYCLE_TIME - (System.currentTimeMillis() - lastCycleTime);

			if (timeToSleep > 0) {
				try {
					Thread.sleep(timeToSleep);
				} catch (InterruptedException e) {
				}
			}

			timestep++;
			behaviorTimeStep++;
		}
	}

	private void addShutdownHooks() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				shutdown();
			}
		});
	}

	@Override
	public void shutdown() {
		logger.logMessage(LogCodex.encodeLog(LogType.MESSAGE, "Shutting down Controller..."));

		if (logger != null)
			logger.stopLogging();

		if (entityLogger != null)
			entityLogger.stopLogging();

		ioManager.shutdown();

		System.out.println("# Finished Controller cleanup!");
	}

	@Override
	public void reset() {
		ioManager.stopThymio();

		if (activeBehavior != null) {
			activeBehavior.cleanUp();
			activeBehavior = null;
			ioManager.setMotorSpeeds(leftSpeed, rightSpeed);
		}
		try {
			// make sure that the current control step is processed
			Thread.sleep(100);
		} catch (InterruptedException e) {
		}

	}

	@Override
	public void processInformationRequest(Message request, ConnectionHandler conn) {
		messageHandler.addMessage(request, conn);
	}

	// Init's
	private void initIO(CIArguments args) {
		ioManager = new ThymioIOManager(this, args);
		initMessages += ioManager.getInitMessages();
	}

	private void initMessageProviders() {
		System.out.println("Creating Message Providers:");

		messageProviders.add(new SystemStatusMessageProvider(this));
		System.out.println("\tSystemStatusMessageProvider");

		for (ControllerInput i : ioManager.getInputs()) {
			if (i instanceof MessageProvider) {
				messageProviders.add((MessageProvider) i);
				System.out.println("\t" + i.getClass().getSimpleName());
			}
		}

		messageProviders.add(new EntityMessageProvider(this));
		System.out.println("\tEntityMessageProvider");
		messageProviders.add(new EntitiesMessageProvider(this));
		System.out.println("\tEntitiesMessageProvider");
		messageProviders.add(new BehaviorMessageProvider(this));
		System.out.println("\tBehaviorMessageProvider");
		messageProviders.add(new NeuralActivationsMessageProvider(this));
		System.out.println("\tNeuralActivationsMessageProvider");
		messageProviders.add(new LogMessageProvider(this));
		System.out.println("\tLogMessageProvider");
		messageProviders.add(new ThymioVirtualPositionMessageProvider(this));
		System.out.println("\tThymioVirtualPositionMessageProvider");

	}

	private void initConnections() {
		try {

			System.out.println("Starting connections...");

			connectionListener = new ConnectionListener(this);
			connectionListener.start();

			initMessages += "[INIT] ConnectionListener: ok\n";

			motorConnectionListener = new MotorConnectionListener(this);
			motorConnectionListener.start();

			commandConnectionListener = new CommandConnectionListener(this);
			commandConnectionListener.start();

			ArrayList<BroadcastMessage> broadcastMessages = new ArrayList<BroadcastMessage>();
			broadcastMessages.add(new HeartbeatBroadcastMessage(this));
			broadcastMessages.add(new SharedThymioBroadcastMessage(this));
			broadcastHandler = new RealBroadcastHandler(this, broadcastMessages);

			initMessages += "[INIT] MotorConnectionListener: ok\n";

		} catch (IOException e) {
			initMessages += "[INIT] Unable to start Network Connection Listeners! (" + e.getMessage() + ")\n";
		}
	}

	// Behaviors
	@Override
	public void startBehavior(CIBehavior b) {
		stopActiveBehavior();
		activeBehavior = b;
		startBehavior = true;
	}

	@Override
	public void stopActiveBehavior() {
		if (activeBehavior != null) {
			activeBehavior.cleanUp();
			activeBehavior = null;
			ioManager.setMotorSpeeds(0, 0);
		}
	}

	@Override
	public void setMotorSpeeds(double left, double right) {
		leftSpeed = left;
		rightSpeed = right;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	// Getters
	@Override
	public String getNetworkAddress() {
		return NetworkUtils.getAddress();
	}

	@Override
	public BroadcastHandler getBroadcastHandler() {
		return broadcastHandler;
	}

	@Override
	public CIBehavior getActiveBehavior() {
		return activeBehavior;
	}

	@Override
	public ArrayList<CISensor> getCISensors() {
		return cisensors;
	}

	@Override
	public CISensor getCISensorByType(Class<? extends CISensor> sensorClass) {
		for (CISensor s : cisensors) {
			if (s.getClass().equals(sensorClass))
				return s;
		}
		return null;
	}

	@Override
	public ArrayList<Entity> getEntities() {
		return entities;
	}

	@Override
	public String getInitMessages() {
		return initMessages;
	}

	@Override
	public List<MessageProvider> getMessageProviders() {
		return messageProviders;
	}

	@Override
	public List<Short> getInfraredSensorsReadings() {
		return ioManager.getProximitySensorsReadings();
	}

	@Override
	public double[] getCameraReadings() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double getTimeSinceStart() {
		long elapsedMillis = System.currentTimeMillis() - this.startTimeInMillis;
		return (elapsedMillis) / 1000.0;
	}

	@Override
	public String getStatus() {
		return status;
	}

	public ThymioIOManager getIOManager() {
		return ioManager;
	}

	public void startLogger() {
		ThymioFileLogger fileLogger_1 = new ThymioFileLogger(this);
		fileLogger_1.start();
		this.logger = fileLogger_1;

		ThymioFileLogger fileLogger_2 = new ThymioFileLogger(this, "entities");
		fileLogger_2.start();
		this.entityLogger = fileLogger_2;
	}

	@Override
	public Vector2d getVirtualPosition() {
		return virtualPosition;
	}

	@Override
	public void setVirtualPosition(double x, double y) {
		if (virtualPosition == null)
			virtualPosition = new Vector2d(x, y);
		else
			virtualPosition.set(x, y);
	}

	@Override
	public Double getVirtualOrientation() {
		return virtualOrientation;
	}

	@Override
	public void setVirtualOrientation(double orientation) {
		virtualOrientation = orientation;
	}

	@Override
	public double getThymioRadius() {
		return 0.08;
	}

	@Override
	public RobotLogger getLogger() {
		return logger;
	}

	@Override
	public RobotLogger getEntityLogger() {
		return entityLogger;
	}

	@Override
	public double getLeftMotorSpeed() {
		return leftSpeed;
	}

	@Override
	public double getRightMotorSpeed() {
		return rightSpeed;
	}

	@Override
	public void replaceEntity(Entity e) {
		synchronized (entities) {
			entities.remove(e);
			entities.add(e);

		}
	}

	@Override
	public void setProperty(String name, String value) {
		// TODO Auto-generated method stub

	}

	@Override
	public void setEntities(ArrayList<Entity> entities) {
		this.entities = entities;
	}
}
