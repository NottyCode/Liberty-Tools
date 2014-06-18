/**
 * This file is licensed to you under the Apache License, 
 * Version 2.0 (the "License"); you may not use this file 
 * except in compliance with the License.  You may obtain 
 * a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.logicali.wlp.tools.plugincfg;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

public class Main {
	public static void main(String[] argArray) {

		String serverName = null;
		File outDir = null;

		boolean moveFile = false;
                //boolean hintFile = false;

		for (int i = 0; i < argArray.length; i++) {
			if (argArray[i].startsWith("--serverName=")) {
				serverName = argArray[i].substring("--serverName=".length());
			}
			else if (argArray[i].startsWith("--outputDir=")) {
				outDir = new File ( argArray[i].substring("--outputDir=".length()));				
				moveFile = true;
		//		hintFile = true;
			}
			else if (argArray[i].startsWith("--help") || argArray[i].startsWith("-h")) {
				System.out.println("Generate plugin-cfg.xml from liberty servers");
				System.out.println("");
				System.out.println("Arguements");
				System.out.println("");
				System.out.println("--serverName=<SERVERNAME>  the name of a single server to probe. Without this argument all servers will be probed.");
				System.out.println("--outputDir=<DIRECTORY>  output files to DIRECTORY with the server name as a prefix to prevent duplicates. Without this argument they will be placed in the server's default output directories. ");
			} else {
				System.out.println("Unkown argument:" + argArray[i]);
				System.exit(1);
 			}
                }

		List<VirtualMachineDescriptor> vmds = findLibertyServer(serverName);

		for (VirtualMachineDescriptor vmd : vmds)
		{ 

			VirtualMachine vm = null;
			try {
				vm = VirtualMachine.attach(vmd);
				File outputDir;

				ServerInfo args = new ServerInfo(argArray, vm, vmd);
				String address = args.getJMXAddress();

				if (outDir == null){
					outputDir = args.getServerOutputDir();
				} else {
				        outputDir = outDir;
				}

				if (address != null) {
					JMXServiceURL url = new JMXServiceURL(address);
					JMXConnector conn = JMXConnectorFactory.connect(url);
					MBeanServerConnection mbeanServer = conn.getMBeanServerConnection();
					mbeanServer.invoke(new ObjectName(
							"WebSphere:name=com.ibm.ws.jmx.mbeans.generatePluginConfig"),
							"generateDefaultPluginConfig", new Object[0], new String[0]);

					if(moveFile){

						File plginFile;
						File[] findPlginFile = args.getServerOutputDir().listFiles();
						for (File f : findPlginFile){
							if (f.getName().equals("plugin-cfg.xml")){
								plginFile = f;
								plginFile.renameTo(new File(outputDir, args.getServerName()+"-plugin-cfg.xml"));
								break;
							}
						}
							System.out.println("Plugin configuration file written to "
							+ outputDir + "/"+args.getServerName()+"-plugin-cfg.xml");
					} else  {
						System.out.println("Plugin configuration file written to "
							+ outputDir + "/plugin-cfg.xml");
                                        }

                                        /*if (hintFile){
					   File hintFile = new File(outDir + "/hintfile.txt");

                                           String[] mbeansListTmp = (String[]) mbeanServer.queryNames(null, null);
                                           ArrayList<String> mbeansList = new ArrayList<String>(mbeansListTmp);
                                           for (int i = mbeansList.length() -1; i >=0; i--){
					  	if (! mbeansList.get(i).contains("WebSphere:service=com.ibm.websphere.application.ApplicationMBean")){
						   mbeansList.remove(i);
						}
					   }
					   mbeansList = mbeansList.sort();
					   for (String s : mbeansList){
					     s.substring(s.lastIndexOf("name=") + 1);
					     //TODO write the file as a CSL
 					   }
                                        }*/
				}
				else {
					System.err.println("Unable to connect to " + args.getServerName()
							+ "'s MBean server");
				}
			} catch (InstanceNotFoundException e) {
				reportError(
						"The MBean used to generate the plugin configuration could not be located",
						e);
			} catch (MalformedObjectNameException e) {
				reportError("An internal error occurred. The MBean name was not valid",
						e);
			} catch (MBeanException e) {
				reportError(
						"The MBean threw an exception generating the plugin configuration",
						e.getTargetException());
			} catch (ReflectionException e) {
				reportError("An error occurred calling the MBean",
						e.getTargetException());
			} catch (IOException e) {
				reportError("An I/O error happened while generating the plugin config",
						e);
			} catch (AttachNotSupportedException e) {
	            reportError(
	                    "Unable to attach to the server VM. This is possibly because the server and this program are not using the same JVM.",
	                    e);
                        } catch (Exception e) {
	            reportError(
	                    "Some other error.",
	                    e);
			} finally {
				try {
					if (vm != null){
						vm.detach();
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} 
	}



private static List<VirtualMachineDescriptor> findLibertyServer(String nullableSN) {
	List<VirtualMachineDescriptor> vmdsUnfiltered = VirtualMachine.list();

	List<VirtualMachineDescriptor> vmds = new LinkedList<VirtualMachineDescriptor>();

	for (VirtualMachineDescriptor vmd : vmdsUnfiltered) {
		String displayName = vmd.displayName();

		if (nullableSN == null && displayName.contains("ws-server.jar ")) {
			vmds.add(vmd);
		}
		else if (nullableSN != null && displayName.endsWith("ws-server.jar " + nullableSN)) {
			vmds.add(vmd);
		}

	}

	return vmds;
}



/**
 * @param e
 */
private static void reportError(String msg, Exception e) {
	System.err.println(msg);
	System.err.println(e.getMessage());
}
}
