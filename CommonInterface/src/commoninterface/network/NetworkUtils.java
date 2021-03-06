package commoninterface.network;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class NetworkUtils {

	private static String ADDRESS = null;

	// In order to find our own IP address among all possible NICs,
	// we should at least know how it starts.
	// private static final String ADDRESS_START1 = "192.168.3";
	private static final String ADDRESS_START1 = "192.168.1";
	private static final String ADDRESS_START2 = "172.17.";
	private static final String ADDRESS_START3 = "192.168.3";

	public static String getAddress() {
		if (ADDRESS == null) {
			try {
				for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
						.hasMoreElements();) {
					NetworkInterface intf = en.nextElement();

					for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
						String next = enumIpAddr.nextElement().toString().replace("/", "");
						if (next.startsWith(ADDRESS_START1) || next.startsWith(ADDRESS_START2)
								|| next.startsWith(ADDRESS_START3)) {
							ADDRESS = next;
							return next;
						}
					}
				}
			} catch (SocketException e) {
				e.printStackTrace();
			}
			return null;
		} else {
			return ADDRESS;
		}
	}

	public static String getAddress(String intfName) {

		if (ADDRESS == null) {
			try {
				for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
						.hasMoreElements();) {
					NetworkInterface intf = en.nextElement();

					if (intf.getName().equals(intfName)) {

						for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
								.hasMoreElements();) {
							String next = enumIpAddr.nextElement().toString().replace("/", "");
							if (next.startsWith(ADDRESS_START1) || next.startsWith(ADDRESS_START2)
									|| next.startsWith(ADDRESS_START3)) {
								ADDRESS = next;
								return next;
							}
						}
					}
				}
			} catch (SocketException e) {
				e.printStackTrace();
			}
			return null;
		} else {
			return ADDRESS;
		}
	}

	public static String getBroadcastAddress(String address) {
		String broadcastAddress = "";

		String[] split = address.split("\\.");

		for (int i = 0; i < split.length - 1; i++)
			broadcastAddress += split[i] + ".";

		return broadcastAddress + "255";
	}

	public static String getHostname() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			System.err.println(e.getMessage());
			return null;
		}
	}
}
