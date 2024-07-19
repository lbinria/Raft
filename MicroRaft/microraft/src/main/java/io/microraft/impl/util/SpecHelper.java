/*
 * package io.microraft.impl.util;
 *
 * import org.lbee.instrumentation.helper.*; import
 * org.lbee.instrumentation.trace.*; import org.lbee.instrumentation.clock.*;
 *
 * import java.io.BufferedWriter; import java.io.FileWriter; import
 * java.io.IOException; import java.util.HashMap;
 *
 * public class SpecHelper {
 *
 * private static final HashMap<String, BehaviorRecorder> specs = new
 * HashMap<>();
 *
 * public static BehaviorRecorder get(String name) { if
 * (!specs.containsKey(name)) { try { specs.put(name,
 * BehaviorRecorder.create(name + ".ndjson",
 * SharedClock.get("microraft.clock"))); } catch (IOException e) { throw new
 * RuntimeException(e); } }
 *
 * return specs.get(name); }
 *
 * public static void commitChanges(BehaviorRecorder r, String eventName,
 * Object[] args) { try { r.commitChanges(eventName, args); } catch (IOException
 * ex) { System.out.println("Unable to record event."); } }
 *
 * public static void commitChanges(BehaviorRecorder r, String eventName) { try
 * { r.commitChanges(eventName); } catch (IOException ex) {
 * System.out.println("Unable to record event."); } }
 *
 * public static VirtualField getStateVariable(String name) { return
 * get(name).getVariable("state").getField(name); }
 *
 * public static VirtualField getCurrentTermVariable(String name) { return
 * get(name).getVariable("currentTerm").getField(name); }
 *
 * public static VirtualField getLogVariable(String name) { return
 * get(name).getVariable("log").getField(name); }
 *
 * public static VirtualField getCommitIndex(String name) { return
 * get(name).getVariable("commitIndex").getField(name); }
 *
 * public static VirtualField getMessages(String name) { return
 * get(name).getVariable("messages"); }
 *
 * public static VirtualField getMessagesDebug(String name) { return
 * get(name).getVariable("__messages"); }
 *
 * }
 */