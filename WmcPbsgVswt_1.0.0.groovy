// ---------------------------------------------------------------------------------
// V S W   W I T H   T O G G L E
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
// not use this file except in compliance with the License. Unless
// required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// ---------------------------------------------------------------------------------
// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include Wmc.WmcPbsgUtilsLib_1.0.0  // Requires the following imports.
import com.hubitat.app.ChildDeviceWrapper as ChildDevW
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import groovy.json.JsonOutput as JsonOutput
import groovy.json.JsonSlurper as JsonSlurper
import groovy.transform.Field
import java.lang.Math as Math
import java.lang.Object as Object
import java.util.concurrent.ConcurrentHashMap

metadata {
  definition(
    name: 'VswWithToggle',
    namespace: 'Wmc',
    author: 'Wesley M. Conner',
    description: 'This device is a sub-component of device Wmc.PBSG',
    category: '',   // As of Q2'24 Not used
    iconUrl: '',    // As of Q2'24 Not used
    iconX2Url: '',  // As of Q2'24 Not used
    documentationLink: 'A Hubitat Community post is pending',
    importUrl: 'https://github.com/WesleyMConner/Hubitat-PBSGLibrary',
    singleThreaded: 'false'
  ) {
    capability "Switch"          // Attributes:
                                 //   - switch: ['on'|'off']
                                 // Commands: on(), off()
    capability "Momentary"       // Commands: push()
  }
  preferences { /* THIS DEVICE IS FULLY MANAGED BY THE PARENT DEVICE */ }
}

void setButtonNameAndPosition(String buttonName, Integer buttonPosition) {
  // The PBSG parent device calls this method just after device creation.
  // By capturing the device's button name and position, subsequent
  // interacton with the parent device is simpler.
  state.buttonName = buttonName
  state.buttonPosition = buttonPosition
}

// ADVERTISED CAPABILITIES

void on() {                            // Per capability 'Switch'
  parent.activate(
    state.buttonName,
    "${this.device.getDeviceNetworkId()} on()"
  )
}

void off() {                           // Per capability 'Switch'
  parent.deactivate(
    state.buttonName,
    "${this.device.getDeviceNetworkId()} off()"
  )
}

void push(Map parms = null) {          // Per capability 'Momentary'
  parent.push(
    state.buttonPosition,
    "${this.device.getDeviceNetworkId()} push()"
  )
}

// METHODS LEVERAGED BY PARENT PBSG

void adjustLogLevel(String level) {
  setLogLevel(level)
}

void logTrace(String text) {
  // Parent speaks as child through this facility.
  logTrace('switch', text)
}

void logInfo(String text) {
  // Parent speaks as child through this facility.
  logInfo('switch', text)
}

void parse(ArrayList actions) {
  // This command expects actions (an ArrayList) of commands (Maps).
  // Each Map must be suitable for execution by sendEvent().
  //      +-----------------+-------------------------+
  //      |            name | Target device attribute |
  //      |           value | Attribute's value       |
  //      | descriptionText | Human-friendly string   |
  //      |   isStateChange | true or false           |
  //      |            unit | NOT REQUIRED            |
  //      +-----------------+-------------------------+
  ArrayList allowedActions = ['switch']
  actions.each{ action ->
    if (action?.name in allowedActions) { sendEvent(action) }
  }
}

// NOT REQUIRED
//   void installed() { }
//   void uninstalled() { }
//   void initialize() {}
//   void updated() { }
//   void parse(String) { }
