package org.joget.marketplace.app;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.PluginThread;
import org.joget.plugin.base.ApplicationPlugin;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.Plugin;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowUserManager;

public class ConditionalMultiProcessTool extends DefaultApplicationPlugin{

    @Override
    public String getName() {
        return "Conditional Multi Process Tool";
    }

    @Override
    public String getVersion() {
        return "6.0.1";
    }

    @Override
    public String getDescription() {
        return "Enable the use of multiple process tools with conditions";
    }
    
    @Override
    public String getLabel() {
        return "Conditional Multi Process Tool";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }
    
    @Override
    public String getPropertyOptions() {
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        String appId = appDef.getId();
        String appVersion = appDef.getVersion().toString();
        Object[] arguments = new Object[]{appId, appVersion};
        String json = AppUtil.readPluginResource(getClass().getName(), "/properties/ConditionalMultiProcessTool.json", arguments, true, "messages/ConditionalMultiProcessTool");
        return json;
    }

    @Override
    public Object execute(final Map properties) {
        final AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        final String[] conditionList = new String[]{"firstCondition","secondCondition","thirdCondition","fourthCondition","fifthCondition"};
        final boolean debugMode = Boolean.parseBoolean((String)properties.get("debug"));
        
        final PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        final WorkflowUserManager workflowUserManager = (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
        final String currentUser = workflowUserManager.getCurrentUsername();
        
        String multithread = (String)properties.get("multithread");
        Thread newThread;
        Collection<Thread> threads = new ArrayList<Thread>();
        
        if(debugMode){
            LogUtil.info(getClass().getName(), "Executing Conditional Multi Process Tool");
        }
        
        try{
            if(multithread.equalsIgnoreCase("true")){
                //multithread
                if(debugMode){
                    LogUtil.info(getClass().getName(), "Parallel Execution Mode");
                }
                //int delay = Integer.parseInt(properties.get("delay").toString());
                
                for (String cond : conditionList) {
                    String condition = (String)properties.get(cond);

                    ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
                    ScriptContext context = engine.getContext();
                    StringWriter writer = new StringWriter();
                    context.setWriter(writer);

                    if(debugMode){
                        LogUtil.info(getClass().getName(), "Evaluating " + cond + " : " + condition);
                    }

                    engine.eval("print(" + condition + ")");

                    String output = writer.toString();
                    output = output.trim();

                    if(debugMode){
                        LogUtil.info(getClass().getName(), "Result " + cond + " : " + output);
                    }

                    if("true".equalsIgnoreCase((String)output)){
                        //executes the process tool plugin
                        final String processToolPropertyName = cond + "ProcessTool";

                        newThread = new PluginThread(new Runnable() {
                            public void run() {
                                workflowUserManager.setCurrentThreadUser(currentUser);

                                Object objProcessTool = properties.get(processToolPropertyName);
                                if (objProcessTool != null && objProcessTool instanceof Map) {
                                    Map fvMap = (Map) objProcessTool;
                                    if (fvMap != null && fvMap.containsKey("className") && !fvMap.get("className").toString().isEmpty()) {
                                        String className = fvMap.get("className").toString();
                                        ApplicationPlugin p = (ApplicationPlugin)pluginManager.getPlugin(className);

                                        Map propertiesMap = new HashMap(properties);//(Map)fvMap.get("properties");
                                        propertiesMap.putAll(AppPluginUtil.getDefaultProperties((Plugin) p, (Map) fvMap.get("properties"), (AppDefinition) properties.get("appDef"), (WorkflowAssignment) properties.get("workflowAssignment")));
                                        ApplicationPlugin appPlugin = (ApplicationPlugin) p;

                                        if (appPlugin instanceof PropertyEditable) {
                                            ((PropertyEditable) appPlugin).setProperties(propertiesMap);
                                        }

                                        if(debugMode){
                                            LogUtil.info(getClass().getName(), "Executing tool: " + processToolPropertyName + " - " + className);
                                        }
                                        
                                        AppUtil.setCurrentAppDefinition(appDef);
                                        Object result = appPlugin.execute(propertiesMap);

                                        if(debugMode){
                                            if(result != null){
                                                LogUtil.info(getClass().getName(), "Executed tool: " + processToolPropertyName + " - " + className + " - " + result.toString());
                                            }else{
                                                LogUtil.info(getClass().getName(), "Executed tool: " + processToolPropertyName + " - " + className);
                                            }
                                        }
                                    }
                                }
                            }
                        });
                        newThread.start();
                        threads.add(newThread);
                        
                        /*
                                    ,
                        {
                            "name" : "delay",
                            "label" : "@@app.conditionalMultiProcessTool.delay@@",
                            "type" : "textfield",
                            "value" : "1",
                            "required" : "True",
                            "control_field": "multithread",
                            "control_value": "true",
                            "control_use_regex": "false"
                        },*/
                        
//                        //delay in starting new thread
//                        if(delay > 0){
//                            try {
//                                Thread.sleep(delay * 1000);
//                            } catch (InterruptedException ex) {
//                                Logger.getLogger(ConditionalMultiProcessTool.class.getName()).log(Level.SEVERE, null, ex);
//                            }
//                        }
                    }
                }
            }else{
                //single thread
                if(debugMode){
                    LogUtil.info(getClass().getName(), "Single Thread Execution Mode");
                }
                
                newThread = new PluginThread(new Runnable() {
                    public void run() {
                        workflowUserManager.setCurrentThreadUser(currentUser);
                        
                        for (String cond : conditionList) {
                            try{
                                String condition = (String)properties.get(cond);

                                ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
                                ScriptContext context = engine.getContext();
                                StringWriter writer = new StringWriter();
                                context.setWriter(writer);

                                if(debugMode){
                                    LogUtil.info(getClass().getName(), "Evaluating " + cond + " : " + condition);
                                }

                                engine.eval("print(" + condition + ")");

                                String output = writer.toString();
                                output = output.trim();

                                if(debugMode){
                                    LogUtil.info(getClass().getName(), "Result " + cond + " : " + output);
                                }

                                if("true".equalsIgnoreCase((String)output)){
                                    //executes the process tool plugin
                                    String processToolPropertyName = cond + "ProcessTool";
                                    Object objProcessTool = properties.get(processToolPropertyName);
                                    if (objProcessTool != null && objProcessTool instanceof Map) {
                                        Map fvMap = (Map) objProcessTool;
                                        if (fvMap != null && fvMap.containsKey("className") && !fvMap.get("className").toString().isEmpty()) {
                                            String className = fvMap.get("className").toString();
                                            ApplicationPlugin p = (ApplicationPlugin)pluginManager.getPlugin(className);

                                            Map propertiesMap = new HashMap(properties);//(Map)fvMap.get("properties");
                                            propertiesMap.putAll(AppPluginUtil.getDefaultProperties((Plugin) p, (Map) fvMap.get("properties"), (AppDefinition) properties.get("appDef"), (WorkflowAssignment) properties.get("workflowAssignment")));
                                            ApplicationPlugin appPlugin = (ApplicationPlugin) p;

                                            if (appPlugin instanceof PropertyEditable) {
                                                ((PropertyEditable) appPlugin).setProperties(propertiesMap);
                                            }

                                            if(debugMode){
                                                LogUtil.info(getClass().getName(), "Executing tool: " + processToolPropertyName + " - " + className);
                                            }
                                            
                                            AppUtil.setCurrentAppDefinition(appDef);
                                            Object result = appPlugin.execute(propertiesMap);
                                            
                                            if(debugMode){
                                                if(result != null){
                                                    LogUtil.info(getClass().getName(), "Executed tool: " + processToolPropertyName + " - " + className + " - " + result.toString());
                                                }else{
                                                    LogUtil.info(getClass().getName(), "Executed tool: " + processToolPropertyName + " - " + className);
                                                }
                                            }
                                        }
                                    }
                                }
                            }catch(Exception ex){
                                Logger.getLogger(ConditionalMultiProcessTool.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                });
                newThread.start();
                threads.add(newThread);
            }
            
            //wait for all threads to finish
            for(Thread thread : threads){
                try {
                    thread.join();
                } catch (InterruptedException ex) {
                    LogUtil.error(getClassName(), ex, "");
                }
            }
        }catch(Exception ex){
            Logger.getLogger(ConditionalMultiProcessTool.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
}
