package network.mobileAppServer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

import com.google.gson.Gson;

import gui.panels.CommandPanel;
import gui.panels.MotorsPanel;
import main.DroneControlConsole;
import network.mobileAppServer.shared.dataObjects.DroneData;
import network.mobileAppServer.shared.dataObjects.ServerStatusData;
import network.mobileAppServer.shared.messages.CommandMessage;
import network.mobileAppServer.shared.messages.DronesInformationRequest;
import network.mobileAppServer.shared.messages.DronesInformationResponse;
import network.mobileAppServer.shared.messages.DronesMotorsSet;
import network.mobileAppServer.shared.messages.NetworkMessage;
import network.mobileAppServer.shared.messages.ServerMessage;
import network.mobileAppServer.shared.messages.ServerStatusResponse;

public class ServerConnectionHandler extends Thread {
	protected Socket socket;
	protected ObjectOutputStream out;
	protected ObjectInputStream in;
	protected String clientName = null;
	protected ServerConnectionListener connectionListener;

	public ServerConnectionHandler(Socket socket, ServerConnectionListener connectionListener) {
		this.socket = socket;
		this.connectionListener = connectionListener;
	}

	@Override
	public void run() {
		try {
			initConnection();

			while (true) {
				NetworkMessage networkMessage = new Gson().fromJson((String) in.readObject(), NetworkMessage.class);
				processData(networkMessage);
			}
		} catch (IOException e) {
			System.out.println("[SERVER CONNECTION HANDLER] Client " + socket.getInetAddress().getHostAddress() + " ("
					+ clientName + ") disconnected");
		} catch (ClassNotFoundException e) {
			System.out.println("[SERVER CONNECTION HANDLER] I didn't reveived a correct name from "
					+ socket.getInetAddress().getHostAddress());
			e.printStackTrace();
		} finally {
			// always shutdown the handler when something goes wrong
			closeConnection();
		}
	}

	private void processData(NetworkMessage data) throws ClassNotFoundException {
		ServerMessage inMessage = data.getMessage();
		DroneControlConsole console = connectionListener.getConsole();

		switch (data.getMsgType()) {
		case DronesInformationRequest:
			NetworkMessage responseMessageA = new NetworkMessage();
			DronesInformationResponse dronesInformationResponse = new DronesInformationResponse();
			ArrayList<DroneData> dronesIdentification = (connectionListener.getConsole().getDronesSet()
					.getDrones(((DronesInformationRequest) inMessage).getDroneIdentification()));
			dronesInformationResponse.setDronesData(dronesIdentification);
			responseMessageA.setMessage(dronesInformationResponse);
			sendData(responseMessageA);
			break;
		case ServerStatusRequest:
			NetworkMessage responseMessageB = new NetworkMessage();
			ServerStatusData serverStatusData = new ServerStatusData();

			serverStatusData.setAvailableBehaviors(console.getGUI().getCommandPanel().getAvailableBehaviors());
			serverStatusData.setAvailableControllers(console.getGUI().getCommandPanel().getAvailableControllers());
			serverStatusData.setConnectedClientsQty(connectionListener.getClientQuantity());

			if (console instanceof DroneControlConsole) {
				serverStatusData.setConnectedTo(console.getDronesSet().getConnectedToAddress());
			}

			ServerStatusResponse responseB = new ServerStatusResponse();
			responseB.setServerStatusData(serverStatusData);
			responseMessageB.setMessage(responseB);
			sendData(responseMessageB);
			break;
		case DroneMotorsSet:
			MotorsPanel panel = connectionListener.getConsole().getGUI().getMotorsPanel();
			DronesMotorsSet motorsMessage = ((DronesMotorsSet) inMessage);

			String connectedToDroneAddr = console.getDronesSet().getConnectedToAddress();

			if (connectedToDroneAddr.equals(motorsMessage.getDroneIP())
					|| connectedToDroneAddr.equals(motorsMessage.getDroneName())) {
				panel.setSliderValues(motorsMessage.getLeftSpeed(), motorsMessage.getRightSpeed());
				panel.setMaximumSpeed(motorsMessage.getSpeedLimit());
				panel.setOffsetValue(motorsMessage.getOffset());
			}
			break;
		case CommandMessage:
			CommandMessage cmdMessage = ((CommandMessage) inMessage);
			CommandPanel commandPanel = console.getGUI().getCommandPanel();
			switch (cmdMessage.getMessageAction()) {
			case DEPLOY:
				commandPanel.deploy.doClick();
				break;
			case SETLOGSTAMP:
				console.getGUI().getCommandPanel().setLogText(cmdMessage.getPayload()[0]);
				commandPanel.sendLog.doClick();
				break;
			// case START:
			// commandPanel.setSeletedJComboBoxConfigurationFile(cmdMessage.getPayload()[1]);
			// if(cmdMessage.getPayload()[1].equals("")){
			// commandPanel.setConfiguration(cmdMessage.getPayload()[2]);
			// }
			//
			// commandPanel.setSeletedJComboBoxBehavior(cmdMessage.getPayload()[0]);
			// commandPanel.start.doClick();
			// break;
			// case STOP:
			// commandPanel.stop.doClick();
			// break;
			case STOPALL:
				commandPanel.stopAll.doClick();
				break;
			case DEPLOYENTITIES:
				commandPanel.entitiesButton.doClick();
				break;
			default:
				break;
			}

			break;
		default:
			System.out.println("Received message with unknown type: " + inMessage.getMessageType());
			break;
		}
	}

	public void sendData(NetworkMessage outMessage) {
		String json = new Gson().toJson(outMessage, NetworkMessage.class);
		try {
			out.writeObject(json);
		} catch (IOException e) {
			System.err.println("[SERVER CONNECTION HANDLER] Unable to write object to socket... " + e.getMessage());
		}
		// System.out
		// .println("[SERVER CONNECTION HANDLER] Sent information of type "
		// + outMessage.getMsgType());
	}

	protected void initConnection() throws IOException, ClassNotFoundException {
		out = new ObjectOutputStream(socket.getOutputStream());
		in = new ObjectInputStream(socket.getInputStream());

		// out.println(InetAddress.getLocalHost().getHostName());
		// out.flush();

		clientName = (String) in.readObject();

		System.out.println("[SERVER CONNECTION HANDLER] Client " + socket.getInetAddress().getHostAddress() + " ("
				+ clientName + ") connected");
	}

	public synchronized void closeConnection() {
		try {
			if (socket != null && !socket.isClosed()) {
				socket.close();
				connectionListener.removeConnection(this);
				out.close();
				in.close();
			}
		} catch (IOException e) {
			System.out.println("[SERVER CONNECTION HANDLER] Unable to close connection to " + clientName
					+ "... there is an open connection?");
		}
	}

	public synchronized void closeConnectionWhitoutRemove() {
		try {
			if (socket != null && !socket.isClosed()) {
				socket.close();
				out.close();
				in.close();
			}
		} catch (IOException e) {
			System.out.println("[SERVER CONNECTION HANDLER] Unable to close connection to " + clientName
					+ "... there is an open connection?");
		}
	}

	public Socket getSocket() {
		return socket;
	}
}
