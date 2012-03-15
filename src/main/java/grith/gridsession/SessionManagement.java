package grith.gridsession;

import grisu.jcommons.utils.OutputHelpers;
import grisu.jcommons.utils.WalltimeUtils;
import grith.jgrith.control.SlcsLoginWrapper;
import grith.jgrith.credential.Credential;
import grith.jgrith.credential.Credential.PROPERTY;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.globus.common.CoGProperties;
import org.python.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionManagement implements ISessionManagement {

	public static Logger myLogger = LoggerFactory
			.getLogger(SessionManagement.class);

	private static Credential cred = null;

	public static void kickOffIdpPreloading() {

		final Thread t = new Thread() {
			@Override
			public void run() {
				try {
					myLogger.debug("Preloading idps...");
					SlcsLoginWrapper.getAllIdps();
				} catch (final Throwable e) {
					myLogger.error(e.getLocalizedMessage(), e);
				}
			}
		};
		t.setDaemon(true);
		t.setName("preloadIdpsThread");

		t.start();

	}

	private static synchronized void setCredential(Credential c) {
		cred = c;
	}



	private final String location;

	public static ISessionServer server;

	public SessionManagement() {
		this(null);
	}

	public SessionManagement(String location) {
		if (StringUtils.isBlank(location)) {
			this.location = CoGProperties.getDefault().getProxyFile();
		} else {
			this.location = location;
		}
		getCredential();
	}

	private synchronized Credential getCredential() {

		if ( (cred == null) || !cred.isValid() ) {
			try {
				cred = Credential.load(location);
			} catch (Exception e) {
				myLogger.debug("Error loading credential: "
						+ e.getLocalizedMessage());
				return null;
			}
		}
		return cred;
	}

	public List<String> getIdPs() {
		try {
			return SlcsLoginWrapper.getAllIdps();
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public int getRemainingLifetime() {

		Credential currentCredential = getCredential();

		if ( currentCredential == null ) {
			return 0;
		}

		return currentCredential.getRemainingLifetime();
	}

	/* (non-Javadoc)
	 * @see grith.jgrith.session.ISessionManagement#isLoggedIn()
	 */
	public Boolean isLoggedIn() {

		Credential currentCredential = getCredential();
		if (currentCredential == null) {
			return false;
		}
		return currentCredential.isValid();
	}

	public synchronized Boolean login(Map<String, Object> config) {

		Map<PROPERTY, Object> newMap = Maps.newHashMap();
		for (Object key : config.keySet()) {
			PROPERTY p = PROPERTY.valueOf((String) key);
			newMap.put(p, config.get(key));
		}

		Credential currentCredential = Credential.loadFromConfig(newMap, true);
		currentCredential.saveCredential(location);

		setCredential(currentCredential);

		return true;
	}

	public void logout() {

		Credential currentCredential = getCredential();

		if (currentCredential != null) {
			currentCredential.destroy();
		}

	}

	public boolean refresh() {
		Credential currentCredential = getCredential();
		if ((currentCredential == null) || !currentCredential.isAutoRenewable()) {
			return false;
		}

		return currentCredential.autorefresh();
	}

	public boolean shutdown() {

		System.out.println("Shutting down...");

		if (server != null) {
			new Thread() {
				@Override
				public void run() {
					myLogger.debug("Sleeping 1 sec before shutdown...");
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
					server.shutdown();
					myLogger.debug("Server shutdown.");

					System.exit(0);
				}
			}.start();
		}

		return true;
	}

	public String status() {

		if (!isLoggedIn()) {
			return "Not logged in";
		}
		int remaining = getRemainingLifetime();
		Credential c = getCredential();

		Map<String, String> temp = Maps.newLinkedHashMap();
		String[] remainingString = WalltimeUtils
				.convertSecondsInHumanReadableString(remaining);
		temp.put("Remaining session lifetime", remainingString[0] + " "
				+ remainingString[1] + " (" + remaining + " seconds)");

		if (c.isAutoRenewable()) {
			// env.printMessage("Session auto-renew: yes");
			temp.put("Session auto-renew", "yes");
		} else {
			temp.put(
					"Session auto-renew",
					"no (to enable, you need to issue the 'renew session' command or delete your proxy and log in again.)");
			// env.printMessage("Session auto-renew: no (to enable, you need to issue the 'renew session' command or delete your proxy and log in again.)");
		}
		temp.put("User ID", c.getDn());

		String output = OutputHelpers.getTable(temp);
		return output;
	}

}
