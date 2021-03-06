package grith.gridsession.view;

import grisu.jcommons.utils.WalltimeUtils;
import grith.jgrith.cred.AbstractCred.PROPERTY;
import grith.jgrith.cred.Cred;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.TitledBorder;

import com.google.common.collect.Maps;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

public class GridSessionCredPanel extends CredPanel implements PropertyChangeListener {
	
	public static final int UPDATE_WAIT_TIME_SECONDS = 60;

	private static String generateHtml(Map<String, String> sessionProps) {

		final StringBuffer html = new StringBuffer(
				"<html><table width=\"100%\">");

		boolean alternate = true;
		for (final String key : sessionProps.keySet()) {
			if (alternate) {
				html.append("<tr bgcolor=\"#FFFFFF\"><td>");
			} else {
				html.append("<tr><td>");
			}
			html.append(key);
			html.append("</td><td>");
			html.append(sessionProps.get(key));
			html.append("</td></tr>");
			alternate = !alternate;
		}
		html.append("</table></html>");

		return html.toString();
	}

	private JPanel panel;
	private JScrollPane scrollPane;
	private JEditorPane propertiesPane;

	private Cred cred = null;
	
	private Thread updateCredPropertiesThread = null;

	/**
	 * Create the panel.
	 */
	public GridSessionCredPanel() {
		setLayout(new FormLayout(new ColumnSpec[] {
				FormSpecs.RELATED_GAP_COLSPEC,
				ColumnSpec.decode("default:grow"),
				FormSpecs.RELATED_GAP_COLSPEC, }, new RowSpec[] {
				FormSpecs.RELATED_GAP_ROWSPEC,
				RowSpec.decode("default:grow"),
				FormSpecs.RELATED_GAP_ROWSPEC, }));
		add(getPanel(), "2, 2, fill, fill");

		setProperties();

	}

	@Override
	public Map<PROPERTY, Object> createCredConfig() {
		return Maps.newHashMap();
	}

	public Cred getCredential() {
		return cred;
	}

	@Override
	public String getCredTitle() {
		return "Use existing session";
	}

	private JPanel getPanel() {
		if (panel == null) {
			panel = new JPanel();
			panel.setLayout(new FormLayout(new ColumnSpec[] {
					FormSpecs.RELATED_GAP_COLSPEC,
					ColumnSpec.decode("default:grow"),
					FormSpecs.RELATED_GAP_COLSPEC, }, new RowSpec[] {
					FormSpecs.RELATED_GAP_ROWSPEC,
					RowSpec.decode("default:grow"),
					FormSpecs.RELATED_GAP_ROWSPEC, }));
			panel.add(getScrollPane(), "2, 2, fill, fill");
			setBorderTitle("Session details");
		}
		return panel;
	}

	private JEditorPane getPropertiesPane() {
		if (propertiesPane == null) {
			propertiesPane = new JEditorPane();
			propertiesPane.setContentType("text/html");
			propertiesPane.setEditable(false);
		}
		return propertiesPane;
	}

	private JScrollPane getScrollPane() {
		if (scrollPane == null) {
			scrollPane = new JScrollPane(getPropertiesPane());
		}
		return scrollPane;
	}

	@Override
	public void lockUI(boolean lock) {
		// nothing to do here...

	}

	private void setBorderTitle(String title) {
		panel.setBorder(new TitledBorder(null, title, TitledBorder.LEADING, TitledBorder.TOP, null, null));
	}

	public synchronized void setCred(Cred cred) {
		this.cred = cred;
		setProperties();
		
		if ( updateCredPropertiesThread == null || ! updateCredPropertiesThread.isAlive() ) {
			updateCredPropertiesThread = new Thread() {
				public void run() {
					while (GridSessionCredPanel.this.cred != null) {
					try {
						Thread.sleep(UPDATE_WAIT_TIME_SECONDS * 1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
						return;
					}
					
					setProperties();
					}
				}
			};
			updateCredPropertiesThread.start();
			
		}
		
	}

	private void setProperties() {

		String propText = null;
		if (!validGridSessionCredentialExists()) {
			propText = "No existing session";
		} else {

			Map<String, String> props = new LinkedHashMap<String, String>();

			props.put("Logged in", "");
			props.put("", "");

			props.put("Identity", cred.getDN());
			int lt = cred.getRemainingLifetime();
			String lifetime = WalltimeUtils.convertSeconds(lt);
			props.put("Remaining lifetime", lifetime + " ( "+lt+" seconds )");

			boolean autorenews = cred.isRenewable();
			if (autorenews) {
				props.put("Auto-refresh", "yes");
			} else {
				props.put("Auto-refresh",
						"no (to enable, re-login via one of the other methods)");
			}

			// props.put("Login type", cred.g)
			propText = generateHtml(props);
		}
		getPropertiesPane().setText(propText);



	}

	public boolean validGridSessionCredentialExists() {

		if ((cred == null) || !cred.isValid()) {
			return false;
		} else {
			return true;
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {

		if ( "credential".equals(evt.getPropertyName()) ) {
			
			Cred cred = (Cred) evt.getNewValue();
			setCred(cred);
			
		}
		
	}
}
