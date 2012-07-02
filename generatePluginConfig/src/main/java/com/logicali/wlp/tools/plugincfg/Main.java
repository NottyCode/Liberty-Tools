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
import java.util.List;
import java.util.Properties;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

public class Main
{
  public static void main(String[] args) 
  {
    String serverName = "defaultServer";
    if (args.length == 1) {
      serverName = args[0];
    }
    List<VirtualMachineDescriptor> vmds = VirtualMachine.list();
    VirtualMachineDescriptor libertProfileServer = null;
    
    for (VirtualMachineDescriptor vmd : vmds) {
      String displayName = vmd.displayName();
      if (displayName.contains("ws-launch.jar")) {
        if (displayName.contains("ws-launch.jar " + serverName)) {
          libertProfileServer = vmd;
          break;
        } 
      }
    }
    
    if (libertProfileServer != null) {
      VirtualMachine vm;
      try {
        vm = VirtualMachine.attach(libertProfileServer);
        Properties props = vm.getSystemProperties();
        String serverOut = props.getProperty("server.output.dir");
        if (serverOut != null) {
          String address = (String) vm.getAgentProperties().get("com.sun.management.jmxremote.localConnectorAddress");
          if (address == null) {
            String javaHome = props.getProperty("java.home");
            String agent = javaHome + File.separator + "lib" + File.separator + "management-agent.jar";
            vm.loadAgent(agent);
            address = (String) vm.getAgentProperties().get("com.sun.management.jmxremote.localConnectorAddress");
          }
          if (address != null) {
            JMXServiceURL url = new JMXServiceURL(address);
            JMXConnector conn = JMXConnectorFactory.connect(url);
            MBeanServerConnection mbeanServer = conn.getMBeanServerConnection();
            mbeanServer.invoke(new ObjectName("WebSphere:name=com.ibm.ws.jmx.mbeans.generatePluginConfig"), "generateDefaultPluginConfig", new Object[0], new String[0]);
            System.out.println("Plugin configuration file written to " + serverOut + "plugin-cfg.xml");
          } else {
            System.err.println("Unable to connect to " + serverName + "'s MBean server");
          }
        }
        
        vm.detach();
      } catch (AttachNotSupportedException e) {
        reportError("Unable to attach to the server VM. This is possibly because the server and this program are not using the same JVM.", e);
      } catch (IOException e) {
        reportError("An I/O error happened while generating the plugin config", e);
      } catch (AgentLoadException e) {
        reportError("An error occurred loading the JMX Management agent in the target JVM", e);
      } catch (AgentInitializationException e) {
        reportError("An error occurred initializing the JMX Management agent in the target JVM", e);
      } catch (InstanceNotFoundException e) {
        reportError("The MBean used to generate the plugin configuration could not be located", e);
      } catch (MalformedObjectNameException e) {
        reportError("An internal error occurred. The MBean name was not valid", e);
      } catch (MBeanException e) {
        reportError("The MBean threw an exception generating the plugin configuration", e.getTargetException());
      } catch (ReflectionException e) {
        reportError("An error occurred calling the MBean", e.getTargetException());
      }
    } else {
      System.err.println("Unable to locate a running server called " + serverName);
    }
  }

  /**
   * @param e
   */
  private static void reportError(String msg, Exception e)
  {
    System.err.println(msg);
    System.err.println(e.getMessage());
  }
}