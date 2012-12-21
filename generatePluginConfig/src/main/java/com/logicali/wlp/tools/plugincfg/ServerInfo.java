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
  private final File outputDir;
  private final String address;

  public ServerInfo(String[] args) {
    String sn = "defaultServer";
    String id = null;
    String a = null;
    File outDir = null;
    String od = null;
    String usrDir = null;
    
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith("--installDir=")) {
        id = args[i].substring("--installDir=".length());
      } else if (args[i].startsWith("--outputDir=")) {
        od = args[i].substring("--outputDir=".length());
      } else if (args[i].startsWith("--userDir=")) {
        usrDir = args[i].substring("--userDir=".length());
      } else if (args[i].startsWith("--")) {
        System.err.println("Unknown argument: " + args[i]);
      } else if (i == args.length - 1) {
        sn = args[i];
      } else {
        System.err
            .println("Invalid position. The server name must be entered as the last argument.");
      }
    }
    
    if (od == null) {
      od = getOutputDir(id, usrDir, sn);
    }

    if (od != null) {
      outDir = new File(od, sn);
      
      File serverWorkarea = new File(outDir, "/workarea/com.ibm.ws.jmx.local.address");
      if (serverWorkarea.exists()) {
        BufferedReader reader = null;
        try {
          reader = new BufferedReader(new FileReader(serverWorkarea));
          a = reader.readLine();
        } catch (IOException e) {
        } finally {
          if (reader != null) {
            try {
              reader.close();
            } catch (IOException e) {
            }
          }
        }
      }
    }
    
    if (a == null) {
      VirtualMachine vm = findLibertyServer(sn);
      Properties props;
      try {
        props = vm.getSystemProperties();
        outDir = new File(props.getProperty("server.output.dir"));
        a = getAddress(vm, props);
        vm.detach();
      } catch (IOException e) {
        reportError("An I/O error happened while generating the plugin config",
            e);
      }
    }

    serverName = sn;
    address = a;
    outputDir = outDir;
  }

  private String getAddress(VirtualMachine vm, Properties props) {
    String address = null;
    try {
      address = (String) vm.getAgentProperties().get(
          "com.sun.management.jmxremote.localConnectorAddress");
      if (address == null) {
        String javaHome = props.getProperty("java.home");
        String agent = javaHome + File.separator + "lib" + File.separator
            + "management-agent.jar";
        vm.loadAgent(agent);
        address = (String) vm.getAgentProperties().get(
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

  private VirtualMachine findLibertyServer(String sn) {
    List<VirtualMachineDescriptor> vmds = VirtualMachine.list();

    for (VirtualMachineDescriptor vmd : vmds) {
      String displayName = vmd.displayName();
      if (displayName.contains("ws-launch.jar")) {
        if (displayName.contains("ws-launch.jar " + sn)) {
          try {
            return VirtualMachine.attach(vmd);
          } catch (AttachNotSupportedException e) {
            reportError(
                "Unable to attach to the server VM. This is possibly because the server and this program are not using the same JVM.",
                e);
          } catch (IOException e) {
            reportError("An I/O error happened while generating the plugin config",
                e);
          }
        }
      }
    }
    
    return null;
  }

  private String getOutputDir(String id, String usrDir, String sn) {
    if (usrDir != null) {
      return usrDir + "/" + sn;
    }
    
    String result = findOutputDir(System.getenv());
    if (result != null) return result;
    
    if (id != null) {
      File etc = new File(id, "etc");
      if (etc.isDirectory() && etc.exists()) {
        File serverEnv = new File(etc, "server.env");
        Map<String, String> env = readIntoMap(serverEnv);
        return findOutputDir(env);
      }
      
      return id + "/usr/servers/" + sn;
    }

    return null;
  }

  private Map<String, String> readIntoMap(File serverEnv) {
    Map<String, String> env = new HashMap<String, String>();
    BufferedReader reader = null;
    try {
      reader = new BufferedReader(new FileReader(serverEnv));
      String line;
      while ((line = reader.readLine()) != null) {
        int index = line.indexOf('=');
        if (index != -1) {
          String before = line.substring(0, index);
          String after = line.substring(index + 1);
          env.put(before.trim(), after.trim());
        }
      }
    } catch (IOException e) {
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
        }
      }
    }
    return env;
  }

  private String findOutputDir(Map<String, String> env) {
    String od = env.get("WLP_OUTPUT_DIR");
    if (od == null) {
      String usrDir = env.get("WLP_USER_DIR");
      if (usrDir != null) {
        od = usrDir + "/servers";
      }
    }
    return od;
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
  
  public File getOutputDir() {
    return outputDir;
  }
}