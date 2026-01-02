/* P ( U S H )   B ( U T T O N )   S ( W I T C H )   G ( R O U P )
 *
 * Copyright (C) 2023-Present Wesley M. Conner
 *
 * LICENSE
 * Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
 * "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
 * not use this file except in compliance with the License. Unless
 * required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 */

// Wmc.WmcPbsgUtilsLib_1.2.0
//   - The imports below support library methods.
//   - Expect a Groovy Linter 'NglParseError' per Hubitat #include.
#include Wmc.WmcPbsgUtilsLib_1.2.0
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

// Imports specific to this file.
import java.time.Duration
import java.time.Instant
import java.util.concurrent.SynchronousQueue

@Field static ConcurrentHashMap<Long, Map> STATE = [:]
@Field static ConcurrentHashMap<Long, SynchronousQueue> QUEUE = [:]

Long DID() { return device.idAsLong }

metadata {
  definition(
    name: 'PBSG - Push Button Switch Group',
    namespace: 'Wmc',
    author: 'Wesley M. Conner',
    description: "Virtual PushButtonSwitchGroup (PBSG) Device",
    category: '',   // As of Q2'24 Not used
    iconUrl: '',    // As of Q2'24 Not used
    iconX2Url: '',  // As of Q2'24 Not used
    documentationLink: 'A Hubitat Community post is pending',
    importUrl: 'https://github.com/WesleyMConner/Hubitat-PBSGLibrary',
    singleThreaded: 'false',
  ) {
    capability 'Initialize'      // Commands: initialize()
    capability 'PushableButton'  // Attributes:
                                 //   - numberOfButtons: number
                                 //   - pushed: number
                                 // Commands: push(number)

    // Commands not implied by a Capability
    command 'config', [
      [ name: 'jsonPrefs', type: 'STRING', description: 'Map of prefs serialized as JSON']
    ]
    command 'activate', [
      [ name: 'button', type: 'STRING', description: 'button to activate' ],
      [ name: 'ref', type: 'STRING', description: 'optional text for tracing' ]
    ]
    command 'deactivate', [
      [ name: 'button', type: 'STRING', description: 'button to deactivate' ],
      [ name: 'ref', type: 'STRING', description: 'optional text for tracing' ]
    ]
    command 'pushByName', [
      [ name: 'buttonName', type: 'STRING', description: 'Toggle button by name (like push but using name instead of number)' ]
    ]
    // Attributes not implied by a Capability
    attribute 'jsonPbsg', 'string'
    attribute 'active', 'string'
  }
  preferences {
    input( name: 'buttons',
      title: "${b('Button Names')} (pipe delimited, e.g., Morning|Evening|Night)",
      type: 'text',
      required: true
    )
    input( name: 'dflt',
      title: [
        b('Default Button'),
        i('(Select a Button Name or "not_applicable")')
      ].join('<br/>'),
      type: 'text',  // Cannot be an Enum since buttons (are dynamic).
      multiple: false,
      defaultValue: 'not_applicable',
      required: false
    )
    input( name: 'stateDisplayMode',
      title: b('State Display Mode'),
      type: 'enum',
      options: ['Button Name', 'On/Off'],
      defaultValue: 'Button Name',
      required: true
    )
    input( name: 'logLevel',
      title: b('PBSG Log Threshold ≥'),
      type: 'enum',
      multiple: false,
      options: [ 'TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'],  // Static Options
      defaultValue: 'TRACE',
      required: true
    )
  }
}

// SYSTEM DEVICE MANAGEMENT METHODS

void installed() {
  // Called when a bare device is first constructed.
  logInfo('installed', 'Initializing STATE and QUEUE PBSG entries.')
  // Both STATE and QUEUE must be populated for a new device.
  STATE[DID()] = getEmptyPbsg()
  QUEUE[DID()] = new SynchronousQueue<Map>(true) // TRUE → FIFO
  logWarn('installed', [ '',
    'Launching Command Queue Handler.',
    "Watch for ${b('QUEUE HANDLER LOOP STARTED')}! in the logs.",
    "If the QUEUE HANDLER does not start:",
    "  - Check Log for excess ${b('Scheduled jobs')}",
    "  - Circa Jul 2024 some ${b('Rule Machine')} jobs seem to hang."
  ])
  runInMillis(100, 'commandProcessor', [data: []])
}

void uninstalled() {
  // Called on device tear down.
  logInfo('uninstalled', 'Unscheduling commandProcessor().')
  unschedule('commandProcessor')
  logInfo('uninstalled', 'Removing STATE and QUEUE entries.')
  STATE.remove(DID())
  QUEUE.remove(DID())
}

void initialize() {
  // Called on hub startup (per capability "Initialize").
  logTrace('initialize', 'Initializing STATE and QUEUE PBSG entries.')
  // Both STATE and QUEUE must re-populated (the PBSG re-built) on hub restart.
  STATE[DID()] = getEmptyPbsg()
  QUEUE[DID()] = new SynchronousQueue<Map>(true) // TRUE → FIFO
  runInMillis(100, 'commandProcessor', [:])
}

void updated() {
  // Called when a human uses the Hubitat GUI's Device drilldown page to edit
  // preferences (aka settings) AND presses 'Save Preferences'.
  logTrace('updated', 'Attempting PBSG Rebuild from configuration data.')
  updatePbsgStructure([ref: 'Invoked by updated()'])
}

String tr(String label, String pks, String vs) {
  return "<tr><td>${label}</td><td>${pks}</td><td>${vs}</td></tr>"
}

Boolean isPbsgChanged(String label, Map p, String ref = '') {
  // Returns true if PBSG differs with the current STATE.
  // Logs any differences.
  Boolean differ = false
  ArrayList table = [ '<table border="1">']
  // Build a table of key with differing values.
  table << tr(b('CHANGED KEYS'), b('PBSG'), b('STATE'))
  Map curr = airGapPbsg(STATE[DID()])
  curr.each { k, v ->
    String vs = "${v}"
    String pks = p?."${k}"?.toString()
    if (vs != pks) {
      differ = true
      table << tr(k, pks, vs)
    s}
  }
  table << '</table>'
  differ ? logInfo(label, [ b(ref), table.join() ])
         : logInfo(label, [ b(ref), b('PBSG is unchanged') ])
  return differ
}

// Utility Methods

Map airGapPbsg(Map m) {
  // Clone PBSG sufficiently deep to 'air gap' the results from the source.
  return m.collectEntries { k, v ->
    switch (k) {
      case 'buttonsList':
      case 'lifo':
        ArrayList shallowCopyList = v.findAll { e -> (e) }
        [k, shallowCopyList]
        break
      default:
        [k, v]
    }
  }
}

String timestampAsString() { return java.time.Instant.now().toString() }

Integer buttonNameToPushed(String button, ArrayList buttons) {
  // Button name to button 'keypad' position is always computed 'on-the-fly'.
  return buttons?.withIndex().collectEntries { b, i ->
    [(b), i+1]
  }?."${button}"
}

// Externally-Exposed PBSG Commands

void ensureStateInitialized() {
  // Ensure STATE and QUEUE are initialized with proper data from preferences
  // This handles the case where commands are called before initialize() runs
  if (STATE[DID()] == null || STATE[DID()].buttonsList?.size() == 0) {
    logInfo('ensureStateInitialized', 'STATE not initialized or empty, rebuilding from preferences')
    STATE[DID()] = getEmptyPbsg()
    updatePbsgStructure([ref: 'ensureStateInitialized'])
  }
  if (QUEUE[DID()] == null) {
    QUEUE[DID()] = new SynchronousQueue<Map>(true)
    runInMillis(100, 'commandProcessor', [:])
  }
}

void config(String jsonPrefs, String ref = '') {
  // If the configuration change alters the PBSG structure:
  //   - Rebuild the PBSG to the new structure
  //   - Update the PBSG version.
  updatePbsgStructure(config: jsonPrefs, ref: ref)
}

void push(Number buttonNumber, String ref = '') {
  // Per Capability 'PushableButton'.
  // Note: Hubitat passes BigDecimal from UI, so accept Number type
  if (buttonNumber) {
    ensureStateInitialized()
    Map command = [
      name: 'Push',
      arg: buttonNumber.intValue(),
      ref: ref,
      version: STATE[DID()].version
    ]
    enqueueCommand(command)
  } else {
    logError('push', 'Called with buttonNumber=NULL')
  }
}

void activate(String button, String ref = '') {
  if (button) {
    ensureStateInitialized()
    Map command = [
      name: 'Activate',
      arg: button,
      ref: ref,
      version: STATE[DID()].version
    ]
    enqueueCommand(command)
  } else {
    logError('activate', 'Called with button=NULL')
  }
}

void deactivate(String button, String ref = '') {
  if (button) {
    ensureStateInitialized()
    Map command = [
      name: 'Deactivate',
      arg: button,
      ref: ref,
      version: STATE[DID()].version
    ]
    enqueueCommand(command)
  } else {
    logError('deactivate', 'Called with button=NULL')
  }
}

void pushByName(String buttonName) {
  // Toggle a button by name (like push() but using name instead of number)
  if (buttonName) {
    ensureStateInitialized()
    Integer buttonNumber = buttonNameToPushed(buttonName, STATE[DID()].buttonsList)
    if (buttonNumber) {
      push(buttonNumber, "pushByName(${buttonName})")
    } else {
      logError('pushByName', "Button '${buttonName}' not found in PBSG")
    }
  } else {
    logError('pushByName', 'Called with buttonName=NULL')
  }
}

// Internal Methods

void updatePbsgStructure(Map parms) {
  // Evaluates the health of available configuration data (i.e., settings
  // overlayed with parms.conf). If the configiuration is healthy and the
  // PBSG STRUCTURE has changed, the PBSG instance is rebuilt.
  //   Input
  //     parms.config - Map of <k, v> pairs that overwrite settings <k, v> pairs.
  //        parms.ref - Context string provided by caller.
  //   Output
  //     null - Config is unhealthy or unchanged relative to CSM, see logs.
  //      Map - The new PBSG (as saved to STATE).
  // Ensure STATE is initialized (may be null after hub restart before initialize() runs)
  if (STATE[DID()] == null) {
    STATE[DID()] = getEmptyPbsg()
  }
  Map config = settings              // Insert Preference <k, v> pairs.
  if (parms.config) {
    config << parseJson(parms.config)  // Overlay provided <k, v> pairs.
  }
  ArrayList issues = []
  ArrayList buttonsList = []
  if (config) {
    // Process the (two) log fields which ARE NOT part of STATE.
    if (config.logLevel == null) {
      issues << "Missing config ${b('logLevel')}, defaulting to TRACE."
      device.updateSetting('logLevel', 'TRACE')
      setLogLevel('TRACE')
    } else if (![ 'TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR'].contains(config.logLevel)) {
      issues << [
        "Unrecognized config ${b('logLevel')} '${config.logLevel}', ",
        'defaulting to TRACE.'
      ].join()
      device.updateSetting('logLevel', 'TRACE')
      setLogLevel('TRACE')
    } else {
      setLogLevel(config.logLevel)
    }
    // Reviewing PBSG Structural fields.
    Boolean healthyButtons = true
    // Normalize curly quotes/apostrophes to straight versions
    String normalizedButtons = config?.buttons
      ?.replaceAll(/[''‚]/, "'")  // Curly single quotes → straight
      ?.replaceAll(/[""„]/, '"')  // Curly double quotes → straight
    // Allow Unicode letters, numbers, spaces, common punctuation; pipe is delimiter
    String markDirty = normalizedButtons?.replaceAll(/[^\p{L}\p{N}\s_|'\-]/, '▮')
    buttonsList = normalizedButtons?.tokenize('|')?.collect { it.trim() }?.findAll { it }
    Integer buttonsCount = buttonsList?.size()
    if (config.buttons == null) {
      issues << "The setting ${b('buttons')} is null."
      healthyButtons = false
    } else if (normalizedButtons != markDirty) {
      issues << [
        "The setting ${b('buttons')} has invalid characters:",
        normalizedButtons,
        "${markDirty} ('▮' denotes problematic characters)",
      ].join('<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;')
      healthyButtons = false
    }
    if (buttonsCount < 2) {
      issues << [
        'Two buttons are required to proceed:',
        "Found ${buttonsCount} buttons: ${bList(buttonsList)}"
      ].join('<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;')
      healthyButtons = false
    }
    // Normalize settings.buttons
    if (healthyButtons) {
      device.updateSetting('buttons', buttonsList.join('|'))
    }
    if (config.dflt == null) {
      issues << "The setting ${b('dflt')} is null (expected 'not_applicable')"
    }
    if (! [buttonsList.contains(config.dflt)]) {
      issues << [
        "The setting ${b('dflt')} (${config.dflt}) is not found among ",
        "buttons: ${bList(buttonsList)}"
      ].join('<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;')
    }
  } else {
    issues << 'No Preferences/settings or parms.config map was found.'
  }
  if (issues) {
    // REPORT THE DISCOVERED ISSUES AND STOP.
    logError('updatePbsgStructure', [ i(parms.ref),
      'The following issues prevent a PBSG (re-)build at this time',
      *issues
    ])
  } else {
    // Does the (healthy) configuration merits a PBSG rebuild?
    String dflt = (config.dflt == 'not_applicable') ? null : config.dflt
    Boolean structureAltered = (
      buttonsList != STATE[DID()].buttonsList
      || dflt != STATE[DID()].dflt
    )
    if (structureAltered) {
      // Having detected a healthy and altered structure begin a PBSG rebuild.
      Map newPbsg = [
        version: timestampAsString(),
        buttonsList: buttonsList,
        dflt: dflt,
        active: null,
        lifo: []
      ]
      logInfo('updatePbsgStructure', [ i(parms.ref),
        "The PBSG structure has changed, new PBSG: ${b(newPbsg)}."
      ])
      rebuildPbsg(newPbsg: newPbsg, ref: parms.ref)
    } else {
      logInfo('updatePbsgStructure', [ i(parms.ref),
        'The PBSG is healthy and DOES NOT REQUIRE a rebuild.'
      ])
    }
  }
}

void rebuildPbsg(Map parms) {
  // Rebuild the PBSG per the structure in parms.newPbsg.
  //   * Updates STATE
  //   * Update appropriate PBSG Attributes
  //
  //   Input
  //     parms.newPbsg - Constains the structural fields of a new PBSG.
  //         parms.ref - Context string provided by caller.
  if (parms.newPbsg) {
    Map pbsg = airGapPbsg(parms.newPbsg)
    pbsg.buttonsList.each { button ->
      ChildDevW vsw = getOrCreateVswWithToggle(
        device.getLabel(),
        button,
        buttonNameToPushed(button, pbsg.buttonsList)
      )
      pbsg.lifo.push(button)
      if (vsw.switch == 'on') {
        pbsg_Activate(pbsg, button, parms.ref)
      }
    }
    if (!pbsg.active && pbsg.dflt) {
      pbsg_Activate(pbsg, pbsg.dflt, parms.ref)
    }
    pbsg_SaveState(pbsg: pbsg, ref: parms.ref)
  } else {
    logError('rebuildPbsg', 'Called with null parms.newPbsg')
  }
}

void pbsg_SaveState(Map parms) {
  // Update STATE for the PBSG and publish appropriate Attributes.
  //   * Updates STATE
  //   * Update appropriate PBSG Attributes
  // Per community.hubitat.com/t/avoid-sending-events-for-unchanged-attributes:
  //   - NOT sending events unless a change has been made/
  //   - If there is no change in the 'jsonPbsg', then (by its definition) there
  //     are no changes for 'numberOfButtons', 'active' or 'pushed'.
  //
  //   Input
  //     parms.pbsg - In-memory PBSG for updating STATE.
  //      parms.ref - Context string provided by caller
  if (parms.pbsg) {
    if (isPbsgChanged('pbsg_SaveState', parms.pbsg, parms.ref)) {
      String ref = parms.ref ? i(" Ref: ${parms.ref}") : ''
      pbsg = airGapPbsg(parms.pbsg)
      // Capture before and after values BEFORE saving PBSG to STATE.
      Integer oldCnt = STATE[DID()].buttonsList.size()
      Integer newCnt = pbsg.buttonsList.size()
      Integer oldPushed = buttonNameToPushed(STATE[DID()].active, STATE[DID()].buttonsList)
      Integer newPushed = buttonNameToPushed(pbsg.active, pbsg.buttonsList)
      String oldSummary = "${i(STATE[DID()].active)} (${i(oldPushed)})"
      String newSummary = "${b(pbsg.active)} (${b(newPushed)})"
      String summary = "[${i(oldSummary)} → ${b(newSummary)}]"
      Boolean cntChanged = (oldCnt != newCnt)
      Boolean activeChanged = (STATE[DID()].active != pbsg.active)
      STATE[DID()] = pbsg
      // Reconcile VSWs and pause to allow devices to reflect state changes.
      pbsg.buttonsList.each{ button ->
        updateVswState(button, (pbsg.active == button) ? 'on' : 'off', parms.ref)
      }
      pauseExecution(100)
      // Begin Attribute Updates
      logTrace('pbsg_SaveState', [ "Updating jsonPbsg, ref: ${ref}",
        pbsg_StateHtml(pbsg),
        bMap(pbsg)
      ])
      device.sendEvent(
        name: 'jsonPbsg',
        isStateChange: true,
        value: toJson(pbsg),
        descriptionText: ref
      )
      if (activeChanged) {
        // Determine active value based on stateDisplayMode preference
        String activeValue = (settings.stateDisplayMode == 'On/Off')
          ? (pbsg.active ? 'on' : 'off')
          : pbsg.active
        String activeDesc = "active: ${b(activeValue)}, ${summary}, ref: ${ref}"
        logTrace('pbsg_SaveState', "Updating active: ${activeDesc})")
        device.sendEvent(
          name: 'active',
          isStateChange: true,
          value: activeValue,
          unit: '#',
          descriptionText: activeDesc
        )
        String pushedDesc = "pushed: ${b(newPushed)}, ${summary}, ref: ${ref}"
        logTrace('pbsg_SaveState', "Updating pushed: ${pushedDesc})")
        device.sendEvent(
          name: 'pushed',
          isStateChange: true,
          value: newPushed,
          unit: '#',
          descriptionText: pushedDesc
        )
      }
      if (cntChanged) {
        String cntDesc = "count: ${b(newCnt)}, [${i(oldCnt)} -> ${b(newCnt)}], ref: ${ref}"
        logTrace('pbsg_SaveState', "Updating numberOfButtons: ${cntDesc}")
        device.sendEvent(
          name: 'numberOfButtons',
          isStateChange: true,
          value: newCnt,
          unit: '#',
          descriptionText: cntDesc
        )
      }
      pruneOrphanedDevices()
    } else {
      logInfo('pbsg_SaveState', 'No changes to save or to publish.')
    }
  } else {
    logError('pbsg_SaveState', 'Missing parms.pbsg')
  }
}

Map getEmptyPbsg() {
  return [
    version: timestampAsString(),
    buttonsList: [],
    dflt: null,
    active: null,
    lifo: []
  ]
}

void enqueueCommand(Map command) {
  // Ensure QUEUE is initialized
  if (QUEUE[DID()] == null) {
    QUEUE[DID()] = new SynchronousQueue<Map>(true)
    runInMillis(100, 'commandProcessor', [:])
  }
  logTrace('enqueueCommand', bMap(command))
  QUEUE[DID()].put(command)
}

void commandProcessor() {  // Map parms
  // Enter a loop to consume commands issued via QUEUE[DID()].
  //   - If the versions agree, the command is executed.
  //   - If the command is older, it is considered 'stale' and dropped.
  //   - If the command is newer, an error is thrown.
  Long l1 = device?.id as Long
  long l2 = app?.id ?: (device?.id as Long)
  logInfo('commandProcessor', 'QUEUE HANDLER LOOP STARTED')
  while (1) {
    logTrace('commandProcessor', 'Awaiting next take().')
    Map command = QUEUE[DID()].take()
    Map pbsg = airGapPbsg(STATE[DID()])
    logTrace('commandProcessor', [ 'Processing command:', bMap(command)])
    if (pbsg.version == command.version) {
      switch(command.name) {
        case 'Activate':
          String button = command.arg
          logTrace('commandProcessor', "case Activate for ${b(button)}.")
          pbsg_Activate(pbsg, button, command.ref)
          pbsg_SaveState(pbsg: pbsg, ref: command.ref)
          break
        case 'Deactivate':
          String button = command.arg
          logTrace('commandProcessor', "case Deactivate for ${b(button)}.")
          pbsg_Deactivate(pbsg, button, command.ref)
          pbsg_SaveState(pbsg: pbsg, ref: command.ref)
          break
        case 'Push':
          Integer buttonNumber = command.arg  // 1..N, not zero based!
          String button = pbsg.buttonsList[buttonNumber - 1]
          logTrace('commandProcessor', "case Toggle for ${b(button)}.")
          if (pbsg.active == button) {
            if (button == pbsg.dflt) {
              logInfo('commandProcessor', [ "Ignoring 'Toggle ${b(button)}'",
                "The button is 'on' AND is also the default button"
              ])
            } else {
              logInfo('commandProcessor', "Toggling ${b(button)} 'off'")
              pbsg.lifo.push(pbsg.active)
              pbsg.active = null
              if (pbsg.dflt) {
                pbsg.lifo.removeAll([pbsg.dflt])
                pbsg.active = pbsg.dflt
              }
              pbsg_SaveState(pbsg: pbsg, ref: command.ref)
            }
          } else {
            if (pbsg.lifo.contains(button)) {
              logInfo('commandProcessor', "Toggling ${b(button)} 'on'")
              if (pbsg.active) { pbsg.lifo.push(pbsg.active) }
              pbsg.lifo.removeAll([button])
              pbsg.active = button
              pbsg_SaveState(pbsg: pbsg, ref: command.ref)
            } else {
              logInfo('commandProcessor', [ "Ignoring 'Toggle ${b(button)}'",
                "The button is not found. (PBSG: ${bMap(pbsg)})"
              ])
            }
          }
          break
        default:
          logError('commandProcessor', "Unknown Command: ${command}")
      }
    } else if (command.version < pbsg.version) {
      logWarn('commandProcessor', [ 'Dropping stale command.',
        "command.version: ${b(command.version)}",
        "   pbsg.version: ${b(pbsg.version)}"
      ])
    } else {
      logError('commandProcessor', [ 'PBSG is stale?!',
        "command.version: ${b(command.version)}",
        "   pbsg.version: ${b(pbsg.version)}"
      ])
    }
  }
}

String buttonState(String button) {
  result = ''
  if (button != null) {
    String tag = (button == settings.dflt) ? '*' : ''
    result += "${tag}${b(button)} "
    ChildDevW d = getChildDevice("${device.getLabel()}_${button}")
    if (d) {
      switch(d.currentValue('switch')) {
        case 'on':
          result += '(<b>on</b>)'
          break
        case 'off':
          result += '(<em>off</em>)'
          break
        default:
          result += '(--)'
      }
    } else {
      result += "(tbd)"
    }
  } else {
    logError('buttonState', 'button arg is NULL')
  }
  return result
}

String pbsg_StateText(Map pbsg) {
  // IMPORTANT
  //   LIFO push() and pop() are supported, *BUT* pushed items are appended
  //   (not prepended). See "reverse()" below, which compensates.
  String result
  if (pbsg) {
    ArrayList list = [ "${hued()}: "]
    list << (pbsg.active ? buttonState(pbsg.active) : 'null')
    list << ' ← '
    pbsg.lifo?.reverse().each { button ->
      if (button) { list << buttonState(button) }
    }
    list << "<b>LIFO</b>"
    result = list.join()
  } else {
    result = "${hued()}: null"
  }
  return result
}

String pbsg_StateHtml(Map pbsg) {
  // IMPORTANT
  //   LIFO push() and pop() are supported, *BUT* pushed items are appended
  //   (not prepended). See "reverse()" below, which compensates.
  ArrayList table = []
  if (pbsg) {
    table << '<span style="display:inline-table;">'
    table << '<table><tr>'
    table << tdBordered( pbsg.active ? buttonState(pbsg.active) : 'null' )
    table << '<td>&nbsp;←&nbsp;</td>'
    pbsg.lifo?.reverse().each { button ->
      table << tdBordered( buttonState(button) )
    }
    table << "<td><b>LIFO</b></td></tr></table></span>"
  }
  return table ? table.join() : null
}

// METHODS THAT ADJUST THE IN-MEMORY PBSG (WITHOUT ALTERING STATE)

void pbsg_Activate(Map pbsg, String button, String ref = null) {
  if (pbsg?.active == button) {
    logInfo(
      'pbsg_Activate',
      "Ignoring 'Activate ${b(button)}' (already active), ref: ${ref}"
    )
  } else if (pbsg.lifo.contains(button)) {
    logTrace('pbsg_Activate', "Activating ${b(button)}, ref: ${ref}")
    if (pbsg.active) { pbsg.lifo.push(pbsg.active) }
    pbsg.lifo.removeAll([button])
    pbsg.active = button
  } else {
    logInfo(
      'pbsg_Activate',
      "Ignoring 'Activate ${b(button)}' (not found), ref: ${ref}"
    )
  }
}

void pbsg_Deactivate(Map pbsg, String button, String ref) {
  if (pbsg.active != button) {
    logInfo(
      'pbsg_Deactivate',
      "Ignoring 'Deactivate ${b(button)} (already inactive), ref: ${ref}"
    )
  } else if (pbsg.active == pbsg.dflt) {
    logInfo(
      'pbsg_Deactivate',
      "Ignoring 'Deactivate ${b(button)}' (dflt button), ref: ${ref}"
    )
  } else if (pbsg.active == button) {
    logInfo('pbsg_Deactivate', "Deactivating ${b(button)}, ref: ${ref}")
    pbsg.lifo.push(pbsg.active)
    pbsg.active = null
    if (pbsg.dflt) {
      logTrace('pbsg_Deactivate', "Activating ${b(pbsg.dflt)}, ref: ${ref}")
      pbsg.lifo.removeAll([pbsg.dflt])
      pbsg.active = pbsg.dflt
    }
  }
}

// MANAGE VSWS AND IMPLEMENT VSW METHODS

ChildDevW getOrCreateVswWithToggle(
  String pbsgName,
  String buttonName,
  Integer buttonPosition
) {
  // IMPORTANT
  //   - Device Network Identifier (DNI) does not include white space.
  //   - Device Names / Labels limit special characters to '_'.
  String dni = "${pbsgName}_${buttonName}"
  String deviveName = dni.replaceAll('_', ' ')
  ChildDevW d = getChildDevice(dni)
  if (!d) {
    d = addChildDevice(
      'Wmc',               // Device namespace
      'PBSG - Push Button',  // Device type
      dni,
      [
        isComponent: true,   // Lifecycle is tied to parent
        name: deviveName,
        label: deviveName
      ]
    )
    logWarn(
      'getOrCreateVswWithToggle',
      "Created new VswWithToggle instance: ${hued(d)}"
    )
  }
  d.adjustLogLevel(settings.logLevel)  // Child logLevel follows parent.
  d.setButtonNameAndPosition(buttonName, buttonPosition)
  return d
}

void pruneOrphanedDevices() {
  ArrayList buttonsList = STATE[DID()].buttonsList
  ArrayList expectedChildDnis = buttonsList.collect { button ->
    "${device.getLabel()}_${button}"
  }
  ArrayList currentChildDnis = getChildDevices().collect { d ->
    d.getDeviceNetworkId()
  }
  ArrayList orphanedDevices = currentChildDnis?.minus(expectedChildDnis)
  orphanedDevices.each { dni ->
    logWarn('pruneOrphanedDevices', "Removing orphaned device ${b(dni)}.")
    deleteChildDevice(dni)
  }
}

void updateVswState(String button, String value, String ref = null) {
  if (button) {
    String iref = ref ? i(", ${ref}") : ''
    if (value == 'on' || value == 'off') {
      ChildDevW d = getVswForButton(button) ?: '-'
      if (d) {
        String currVal = d.currentValue('switch')
        Boolean chg = (currVal != value)
        String state = "[${chg ? b('changed') : i('unchanged') }]"
        String txt = "Turned ${value} ${state}"
        ArrayList commands = [] << [
          name: 'switch',
          value: value,
          descriptionText: txt,
          isStateChange: chg
        ]
        d.parse(commands)
        chg ? d.logInfo('switch', b("Turned ${value}") + iref)
            : d.logTrace('switch', i("Remains ${currVal}" + iref))
      } else {
        logError('updateVswState', "Failed to get VSW for ${button}")
      }
    } else {
      logError('updateVswState', "Expected value 'on' or 'off', got '${value}'.")
    }
  } else {
    logError('updateVswState', 'Received null parameter "button"')
  }
}

String getButtonForVsw(DevW d) {
  return d.getDeviceNetworkId().tokenize('_')[1]
}

ChildDevW getVswForButton(String button) {
  String dni = "${device.getLabel()}_${button}"
  ChildDevW d = getChildDevice(dni)
  if (!d) {
    logError('getVswForButton', "No Device (${hued(d)}) for button (${b(button)}).")
  }
  return d
}

// UNUSED / UNSUPPORTED

void parse(String) {
  // This method is reserved for interaction with FUTURE parent devices.
  logError('parse(String)', 'Called unexpectedly')
}
