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

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class Main {
  public static void main(String[] argArray) {
    ServerInfo args = new ServerInfo(argArray);
    String address = args.getJMXAddress();
    File serverOut = args.getOutputDir();
    if (address != null) {
      try {
        JMXServiceURL url = new JMXServiceURL(address);
        JMXConnector conn = JMXConnectorFactory.connect(url);
        MBeanServerConnection mbeanServer = conn.getMBeanServerConnection();
        mbeanServer.invoke(new ObjectName(
            "WebSphere:name=com.ibm.ws.jmx.mbeans.generatePluginConfig"),
            "generateDefaultPluginConfig", new Object[0], new String[0]);
        System.out.println("Plugin configuration file written to "
            + serverOut + "/plugin-cfg.xml");
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
      }
    } else {
      System.err.println("Unable to connect to " + args.getServerName()
          + "'s MBean server");
    }
  }

  /**
   * @param e
   */
  private static void reportError(String msg, Exception e) {
    System.err.println(msg);
    System.err.println(e.getMessage());
  }
}