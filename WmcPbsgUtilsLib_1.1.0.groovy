/* W M C   P B S G   U T I L S   L I B R A R Y
 *
 * Utility functions for PBSG (Push Button Switch Group) drivers.
 * Extracted from WmcUtils (https://github.com/WesleyMConner/WmcUtils)
 * to provide a self-contained driver package.
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

library(
  name: 'WmcPbsgUtilsLib_1.1.0',
  namespace: 'Wmc',
  author: 'Wesley M. Conner',
  description: 'Utility functions for PBSG drivers',
  category: 'General Purpose',
  documentationLink: 'https://github.com/WesleyMConner/Hubitat-PBSGLibrary',
  importUrl: ''
)

import groovy.transform.Field
import java.util.concurrent.ConcurrentHashMap

@Field static ConcurrentHashMap<String, String> HUED_CACHE = [:]

// ============================================================================
// TEXT FORMATTING FUNCTIONS
// ============================================================================

String b(def val) {
  // Bold HTML formatting
  return "<b>${val}</b>"
}

String i(def val) {
  // Italic HTML formatting
  return "<i>${val}</i>"
}

String bList(ArrayList list) {
  // Format ArrayList with bold items
  return list ? "[${list.collect { b(it) }.join(', ')}]" : '[]'
}

String bMap(Map map) {
  // Format Map with bold keys and italic values
  if (!map) return '[:]'
  ArrayList entries = map.collect { k, v -> "${b(k)}: ${i(v)}" }
  return "[${entries.join(', ')}]"
}

String tdBordered(def content) {
  // Table cell with border styling
  return "<td style='border: 1px solid black; padding: 2px 5px;'>${content}</td>"
}

// ============================================================================
// JSON UTILITY
// ============================================================================

String toJson(def thing) {
  // Serialize object to JSON string
  return groovy.json.JsonOutput.toJson(thing)
}

// ============================================================================
// COLOR PALETTE AND HUED FUNCTIONS
// ============================================================================

Map getFgBg() {
  // Returns a map of 79 foreground/background color combinations
  // Keys range from '-39' to '39' (as strings)
  return [
    '-39': ['White', 'Maroon'],
    '-38': ['White', 'Brown'],
    '-37': ['White', 'Olive'],
    '-36': ['White', 'Teal'],
    '-35': ['White', 'Navy'],
    '-34': ['White', 'Black'],
    '-33': ['White', 'Red'],
    '-32': ['White', 'Orange'],
    '-31': ['Yellow', 'Olive'],
    '-30': ['White', 'Green'],
    '-29': ['White', 'Blue'],
    '-28': ['White', 'Purple'],
    '-27': ['White', 'Gray'],
    '-26': ['Black', 'Fuchsia'],
    '-25': ['Black', 'Yellow'],
    '-24': ['Black', 'Lime'],
    '-23': ['Black', 'Aqua'],
    '-22': ['Black', 'White'],
    '-21': ['Black', 'Silver'],
    '-20': ['Maroon', 'White'],
    '-19': ['Brown', 'White'],
    '-18': ['Olive', 'White'],
    '-17': ['Teal', 'White'],
    '-16': ['Navy', 'White'],
    '-15': ['Black', 'White'],
    '-14': ['Red', 'White'],
    '-13': ['Orange', 'White'],
    '-12': ['Olive', 'Yellow'],
    '-11': ['Green', 'White'],
    '-10': ['Blue', 'White'],
    '-9': ['Purple', 'White'],
    '-8': ['Gray', 'White'],
    '-7': ['Fuchsia', 'Black'],
    '-6': ['Yellow', 'Black'],
    '-5': ['Lime', 'Black'],
    '-4': ['Aqua', 'Black'],
    '-3': ['White', 'Black'],
    '-2': ['Silver', 'Black'],
    '-1': ['Black', 'Silver'],
    '0': ['Black', 'White'],
    '1': ['Silver', 'Black'],
    '2': ['Black', 'Silver'],
    '3': ['White', 'Black'],
    '4': ['Aqua', 'Black'],
    '5': ['Lime', 'Black'],
    '6': ['Yellow', 'Black'],
    '7': ['Fuchsia', 'Black'],
    '8': ['Gray', 'White'],
    '9': ['Purple', 'White'],
    '10': ['Blue', 'White'],
    '11': ['Green', 'White'],
    '12': ['Olive', 'Yellow'],
    '13': ['Orange', 'White'],
    '14': ['Red', 'White'],
    '15': ['Black', 'White'],
    '16': ['Navy', 'White'],
    '17': ['Teal', 'White'],
    '18': ['Olive', 'White'],
    '19': ['Brown', 'White'],
    '20': ['Maroon', 'White'],
    '21': ['Black', 'Silver'],
    '22': ['Black', 'White'],
    '23': ['Black', 'Aqua'],
    '24': ['Black', 'Lime'],
    '25': ['Black', 'Yellow'],
    '26': ['Black', 'Fuchsia'],
    '27': ['White', 'Gray'],
    '28': ['White', 'Purple'],
    '29': ['White', 'Blue'],
    '30': ['White', 'Green'],
    '31': ['Yellow', 'Olive'],
    '32': ['White', 'Orange'],
    '33': ['White', 'Red'],
    '34': ['White', 'Black'],
    '35': ['White', 'Navy'],
    '36': ['White', 'Teal'],
    '37': ['White', 'Olive'],
    '38': ['White', 'Brown'],
    '39': ['White', 'Maroon']
  ]
}

String hued(String s, Long i) {
  // Core hued implementation with caching
  // Applies foreground/background colors based on device/app ID
  String cacheKey = "${s}_${i}"
  String cached = HUED_CACHE[cacheKey]
  if (cached) return cached

  Map fgBg = getFgBg()
  Integer colorIndex = (i % 79) - 39  // Range: -39 to 39
  ArrayList colors = fgBg["${colorIndex}"]
  String fg = colors[0]
  String bg = colors[1]
  String result = "<span style='color:${fg}; background-color:${bg}; padding:0 3px;'>${s}</span>"
  HUED_CACHE[cacheKey] = result
  return result
}

String hued(com.hubitat.app.DeviceWrapper d) {
  // Device wrapper overload
  if (!d) return hued('null', 0L)
  return hued(d.getDeviceNetworkId(), d.id as Long)
}

String hued(com.hubitat.app.ChildDeviceWrapper d) {
  // Child device wrapper overload
  if (!d) return hued('null', 0L)
  return hued(d.getDeviceNetworkId(), d.id as Long)
}

String hued() {
  // No-arg version - uses implicit device object in driver context
  return hued(device.getDeviceNetworkId(), device.id as Long)
}

// ============================================================================
// LOGGING FRAMEWORK
// ============================================================================

void setLogLevel(String logThreshold) {
  // Set the logging threshold level
  // Valid values: TRACE, DEBUG, INFO, WARN, ERROR
  atomicState['logLevel'] = logThreshold
}

Boolean ifLogTrace() {
  return (state.logLevel == 'TRACE')
}

Boolean ifLogDebug() {
  return (state.logLevel in ['TRACE', 'DEBUG'])
}

Boolean ifLogInfo() {
  return (state.logLevel in ['TRACE', 'DEBUG', 'INFO'])
}

Boolean ifLogWarn() {
  return (state.logLevel in ['TRACE', 'DEBUG', 'INFO', 'WARN'])
}

// TRACE level logging
void logTrace(String fnName, String s) {
  if (ifLogTrace()) {
    log.trace("${hued()}.<b>${fnName}( )</b> ${s}")
  }
}

void logTrace(String fnName, ArrayList list) {
  if (ifLogTrace()) {
    log.trace("${hued()}.<b>${fnName}( )</b> ${list.join('<br/>')}")
  }
}

// DEBUG level logging
void logDebug(String fnName, String s) {
  if (ifLogDebug()) {
    log.debug("${hued()}.<b>${fnName}( )</b> ${s}")
  }
}

void logDebug(String fnName, ArrayList list) {
  if (ifLogDebug()) {
    log.debug("${hued()}.<b>${fnName}( )</b> ${list.join('<br/>')}")
  }
}

// INFO level logging
void logInfo(String fnName, String s) {
  if (ifLogInfo()) {
    log.info("${hued()}.<b>${fnName}( )</b> ${s}")
  }
}

void logInfo(String fnName, ArrayList list) {
  if (ifLogInfo()) {
    log.info("${hued()}.<b>${fnName}( )</b> ${list.join('<br/>')}")
  }
}

// WARN level logging
void logWarn(String fnName, String s) {
  if (ifLogWarn()) {
    log.warn("${hued()}.<b>${fnName}( )</b> ${s}")
  }
}

void logWarn(String fnName, ArrayList list) {
  if (ifLogWarn()) {
    log.warn("${hued()}.<b>${fnName}( )</b> ${list.join('<br/>')}")
  }
}

// ERROR level logging (always logged)
void logError(String fnName, String s) {
  log.error("${hued()}.<b>${fnName}( )</b> ${s}")
}

void logError(String fnName, ArrayList list) {
  log.error("${hued()}.<b>${fnName}( )</b> ${list.join('<br/>')}")
}
