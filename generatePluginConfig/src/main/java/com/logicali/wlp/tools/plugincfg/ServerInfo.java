package com.logicali.wlp.tools.plugincfg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

class ServerInfo {
	private final String serverName;
	private final String address;
	private File serverOutputDir = null;

	public ServerInfo(String[] args, VirtualMachine vm, VirtualMachineDescriptor vmd) {
		String a = null;
		String od = null;
		String servername = vmd.displayName();

                String startName = servername.substring(servername.indexOf("ws-server.jar ") + "ws-server.jar ".length());

		if (startName.indexOf(" ") != -1){
	                serverName = startName.substring(0,  startName.indexOf(" "));
		} else{
			serverName = startName.substring(0,  startName.length());
		}

		

		Properties props;
		try {
			props = vm.getSystemProperties();
			a = getAddress(vm, props);
			serverOutputDir = new File(props.getProperty("server.output.dir"));
		} catch (IOException e) {
			reportError("An I/O error happened while generating the plugin config",
					e);
		}


		address = a;
	}

	private String getAddress(VirtualMachine vm, Properties props) {
		String address = null;
		try {
			address = (String) vm.getSystemProperties().get(
					"com.sun.management.jmxremote.localConnectorAddress");
			if (address == null) {
				String javaHome = props.getProperty("java.home");
				String agent = javaHome + File.separator + "lib" + File.separator
						+ "management-agent.jar";
				vm.loadAgent(agent);
				address = (String) vm.getSystemProperties().get(
						"com.sun.management.jmxremote.localConnectorAddress");
			}
		} catch (IOException e) {
			reportError("An I/O error happened while generating the plugin config",
					e);
		} catch (AgentLoadException e) {
			reportError(
					"An error occurred loading the JMX Management agent in the target JVM",
					e);
		} catch (AgentInitializationException e) {
			reportError(
					"An error occurred initializing the JMX Management agent in the target JVM",
					e);
		}

		return address;
	}


	/**
	 * @param e
	 */
	private static void reportError(String msg, Exception e) {
		System.err.println(msg);
		System.err.println(e.getMessage());
	}

	public String getServerName() {
		return serverName;
	}

	public String getJMXAddress() {
		return address;
	}
	
	public File getServerOutputDir() {
		return serverOutputDir;
	}
}
