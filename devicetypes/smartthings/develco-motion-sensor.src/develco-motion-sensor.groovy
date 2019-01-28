/*
 *  Copyright 2016 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy
 *  of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 */
import physicalgraph.zigbee.clusters.iaszone.ZoneStatus


metadata {
	definition(name: "Develco Motion Sensor", namespace: "smartthings", author: "Riivo.Pilvik", ocfDeviceType: "x.com.st.d.sensor.motion", mnmn:"SmartThings", vid:"generic-motion") {
		capability "Motion Sensor"
		capability "Configuration"
		capability "Battery"
		capability "Temperature Measurement"
		capability "Refresh"
		capability "Health Check"
		capability "Sensor"
        capability "Illuminance Measurement" //0x0400

		command "enrollResponse"
        command "getMyTemperature"
       	command "getMyBattery"
       	command "getMyMotion"
        command "getMyLux"

	fingerprint inClusters: "0000,0003,0500,0020,0400,0402,0406,0407,0001, 0B05",  outClusters: "0019", manufacturer: "Develco Poducts A/S", model: "Motion Sensor ZHOT201"

    }

	simulator {
		status "active": "zone report :: type: 19 value: 0031"
		status "inactive": "zone report :: type: 19 value: 0030"
	}

	preferences {
		section {
			image(name: 'educationalcontent', multiple: true, images: [
					"http://cdn.device-gse.smartthings.com/Motion/Motion1.jpg",
					"http://cdn.device-gse.smartthings.com/Motion/Motion2.jpg",
					"http://cdn.device-gse.smartthings.com/Motion/Motion3.jpg"
			])
		}
		section {
			input title: "Temperature Offset", description: "This feature allows you to correct any temperature variations by selecting an offset. Ex: If your sensor consistently reports a temp that's 5 degrees too warm, you'd enter '-5'. If 3 degrees too cold, enter '+3'.", displayDuringSetup: false, type: "paragraph", element: "paragraph"
			input "tempOffset", "number", title: "Degrees", description: "Adjust temperature by this many degrees", range: "*..*", displayDuringSetup: false
		}
        section {
			input title: "Luminance Offset", description: "This feature allows you to correct the luminance reading by selecting an offset. Enter a value such as 20 or -20 to adjust the luminance reading.", displayDuringSetup: false, type: "paragraph", element: "paragraph"
			input "luxOffset", "number", title: "Lux", description: "Adjust luminance by this amount", range: "*..*", displayDuringSetup: false
		}
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"motion", type: "generic", width: 6, height: 4){
			tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
				attributeState "active", label:'motion', icon:"https://s3-eu-west-1.amazonaws.com/fibaro-smartthings/motionSensor/motion1.png", backgroundColor:"#00a0dc"
				attributeState "inactive", label:'no motion', icon:"https://s3-eu-west-1.amazonaws.com/fibaro-smartthings/motionSensor/motion0.png", backgroundColor:"#ffffff"
			}
		}
		valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:""
		}
		valueTile("temperature", "device.temperature", width: 2, height: 2) {
			state("temperature", label:'${currentValue}°', unit:"C",
				backgroundColors:[
					[value: 00, color: "#767676"],
					[value: 21, color: "#153591"],
					[value: 31, color: "#1e9cbb"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]
				]
			)
		}
		valueTile("illuminance", "device.illuminance", decoration: "flat", width: 2, height: 2) {
			state("illuminance", label:'${currentValue} lux', unit:"lux"
            	,
				backgroundColors:[
					[value: 5, color: "#767676"],
					[value: 50, color: "#ffa81e"],
					[value: 500, color: "#fbd41b"]
				]
                /*
                st.illuminance.illuminance.bright
				st.illuminance.illuminance.dark
				st.illuminance.illuminance.light
                */

			)
		}
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}

		main(["motion", "temperature"])
		details(["motion", "battery","temperature", "illuminance","refresh"])
	}
}

def parse(String description) {
	log.debug "description: $description"

	Map map = [:]
	if (description?.startsWith('catchall:')) {
		map = parseCatchAllMessage(description)
	}
	else if (description?.startsWith('read attr -')) {
		map = parseReportAttributeMessage(description)
	}
	else if (description?.startsWith('temperature: ')) {
		map = parseCustomMessage(description)
	}
 	else if (description?.startsWith('illuminance: ')) {
		map = parseCustomMessage(description)
	}	
    else if (description?.startsWith('zone status')) {
		map = parseIasMessage(description)
	}

	log.debug "Parse returned $map"
	def result = map ? createEvent(map) : null

	if (description?.startsWith('enroll request')) {
		List cmds = enrollResponse()
		log.debug "enroll response: ${cmds}"
		result = cmds?.collect { new physicalgraph.device.HubAction(it) }
	}
	return result
}

private Map parseCatchAllMessage(String description) {
	Map resultMap = [:]
	def cluster = zigbee.parse(description)
	if (shouldProcessMessage(cluster)) {
		switch(cluster.clusterId) {
			case 0x0001:
				resultMap = getBatteryResult(cluster.data.last())
				break

			case 0x0402:
				// temp is last 2 data values. reverse to swap endian
				String temp = cluster.data[-2..-1].reverse().collect { cluster.hex1(it) }.join()
				def value = getTemperature(temp)
				resultMap = getTemperatureResult(value)
				break
                
			case 0x0400:
				// Illumination  is value and can be converted lux=10^(y/10000) -1
				log.debug "catchall : luminance" + cluster
                resultMap = getLuminanceResult(cluster.data.last())
				break
                
			case 0x0406:
				log.debug 'motion'
				resultMap.name = 'motion'
				break
		}
	}

	return resultMap
}

private boolean shouldProcessMessage(cluster) {
	// 0x0B is default response indicating message got through
	// 0x07 is bind message
	boolean ignoredMessage = cluster.profileId != 0x0104 ||
		cluster.command == 0x0B ||
		cluster.command == 0x07 ||
		(cluster.data.size() > 0 && cluster.data.first() == 0x3e)
	return !ignoredMessage
}

private Map parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
	log.debug "Desc Map: $descMap"

	Map resultMap = [:]
	if (descMap.cluster == "0402" && descMap.attrId == "0000") {
		def value = getTemperature(descMap.value)
		resultMap = getTemperatureResult(value)
	}
	else if (descMap.cluster == "0001" && descMap.attrId == "0020") {
		resultMap = getBatteryResult(Integer.parseInt(descMap.value, 16))
	}
	else if (descMap.cluster == "0400" && descMap.attrId == "0000") { 
		resultMap = getLuminanceResult(Integer.parseInt(descMap.value, 16)) 
        
	}
	else if (descMap.cluster == "0406" && descMap.attrId == "0000") {
		def value = descMap.value.endsWith("01") ? "active" : "inactive"
		resultMap = getMotionResult(value)
	}

	return resultMap
}

private Map parseCustomMessage(String description) {
	Map resultMap = [:]
	/*if (description?.startsWith('temperature: ')) {
		//def value = zigbee.parseHATemperatureValue(description, "temperature: ", getTemperatureScale())  //returns round number
		resultMap = getTemperatureResult(value)
        //https://github.com/SANdood/SmartThings-Fixed/blob/master/SmartSense-Temp-Humidity%20Sensor%20Custom 
        //https://community.smartthings.com/t/temperature-value-decimal-place-change-of-behaviour-2-weeks-ago/59226/6
	}*/
    if (description?.startsWith('temperature: ')) {
		def value = (description - "temperature: ").trim()
        if (value.isNumber()) {
        	if (getTemperatureScale() == "F") {
            	value = celsiusToFahrenheit(value.toFloat()) as Float
			}
			resultMap = getTemperatureResult(value)
            return resultMap
        } else {
        	log.error "invalid temperature: ${temp}"
        }
	}
    
    if (description?.startsWith('illuminance: ')) {
        // log.warn "proc: " + value
		// zigbee.lux coverts value to lux
		def value = zigbee.lux( description.split(": ")[1] as Integer ) 
		resultMap = getLuminanceResult(value)
	}
	return resultMap
}

private Map parseIasMessage(String description) {
    List parsedMsg = description.split(' ')
    String msgCode = parsedMsg[2]
    
    Map resultMap = [:]
    switch(msgCode) {
        case '0x0020': // Closed/No Motion/Dry
        	resultMap = getMotionResult('inactive')
            break

        case '0x0021': // Open/Motion/Wet
        	resultMap = getMotionResult('active')
            break

        case '0x0022': // Tamper Alarm
        	//log.debug 'motion with tamper alarm'
        	resultMap = getMotionResult('active')
            break

        case '0x0023': // Battery Alarm
            break

        case '0x0024': // Supervision Report
        	//log.debug 'no motion with tamper alarm'
        	resultMap = getMotionResult('inactive')
            break

        case '0x0025': // Restore Report
            break

        case '0x0026': // Trouble/Failure
        	//log.debug 'motion with failure alarm'
        	resultMap = getMotionResult('active')
            break

        case '0x0028': // Test Mode
            break
    }
    return resultMap
}

def getTemperature(value) {
	log.debug 'TEMPERATURE'
	def celsius = Integer.parseInt(value, 16).shortValue() / 100	
	if(getTemperatureScale() == "C"){
		return celsius
	} else {
		return celsiusToFahrenheit(celsius) as Integer
	}
}
private Map getTemperatureResult(value) {
	log.debug "TEMP $value"
	def linkText = getLinkText(device)
	if (tempOffset) {
		def offset = tempOffset //as int
		Float v = value as Float
		value = (v + offset) as Float
	}
    Float nv = Math.round( (value as Float) * 10.0 ) / 10	// Need at least one decimal point
    value = nv as Float
	def descriptionText = "${linkText} was ${value}°${temperatureScale}"
	return [
		name: 'temperature',
		value: value,
		descriptionText: descriptionText,
		translatable: true,
		unit: temperatureScale
	]
}
/*
private Map getTemperatureResult(value) {
	log.debug 'TEMP'
	if (tempOffset) {
		def offset = tempOffset as int
		def v = value as int
		value = v + offset
	}
    def descriptionText
    if ( temperatureScale == 'C' )
    	descriptionText = '{{ device.displayName }} was {{ value }}°C'
    else
    	descriptionText = '{{ device.displayName }} was {{ value }}°F'

	return [
		name: 'temperature',
		value: value,
		descriptionText: descriptionText,
		translatable: true,
		unit: temperatureScale
	]
}*/
private Map getLuminanceResult(rawValue) {
	log.debug 'ILLUMINANCE'
    log.debug "Luminance rawValue = ${rawValue}"
    def linkText = getLinkText(device)

	if (luxOffset) {
		def offset = luxOffset as int
		def v = rawValue as int
		rawValue = v + offset
	}
    def nvlux = Math.round( (rawValue) * 10.0 ) / 10	// Need at least one decimal point
    rawValue = nvlux //as Float    
    def descriptionText = "${linkText} was ${rawValue}"
	def result = [
		name: 'illuminance',
		value: '--',
		descriptionText: descriptionText,
        translatable: true,
 		unit: 'lux'
        
	]
    
    result.value = rawValue as Integer
    return result
}

private Map getBatteryResult(rawValue) {
	log.debug 'BATTERY'
	log.debug "Battery rawValue = ${rawValue}"
	def linkText = getLinkText(device)

	def result = [
		name: 'battery',
		value: '--',
		translatable: true
	]

	def volts = rawValue / 10

	if (rawValue == 0 || rawValue == 255) {}
	else {
		if (volts > 3.5) {
			result.descriptionText = "${linkText} battery has too much power: (> 3.5) volts."
		}
		else {
			if (device.getDataValue("manufacturer") == "SmartThings") {
				volts = rawValue // For the batteryMap to work the key needs to be an int
				def batteryMap = [28:100, 27:100, 26:100, 25:90, 24:90, 23:70,
								  22:70, 21:50, 20:50, 19:30, 18:30, 17:15, 16:1, 15:0]
				def minVolts = 15
				def maxVolts = 28

				if (volts < minVolts)
					volts = minVolts
				else if (volts > maxVolts)
					volts = maxVolts
				def pct = batteryMap[volts]
				if (pct != null) {
					result.value = pct
                    def value = pct
					result.descriptionText = "${linkText} battery was ${value}%"
				}
			}
			else {
				def minVolts = 2.1
				def maxVolts = 3.0
				def pct = (volts - minVolts) / (maxVolts - minVolts)
				def roundedPct = Math.round(pct * 100)
				if (roundedPct <= 0)
					roundedPct = 1
				result.value = Math.min(100, roundedPct)
				result.descriptionText = "${linkText} battery was ${value}%"
			}
		}
	}

	return result
}



private Map getMotionResult(value) {
	log.debug 'MOTION'
    def linkText = getLinkText(device)
	String descriptionText = value == 'active' ? "${linkText} detected motion" : "${linkText} motion has stopped"
	return [
		name: 'motion',
		value: value,
		descriptionText: descriptionText,
        translatable: true
	]
}


////MY REFRESH///
def getMyTemperature() {
    log.debug "getTemperature()"
    zigbee.readAttribute(0x0402, 0x0000, [destEndpoint: 0x26])
}

def getMyBattery() {
    log.debug "getBattery()"
    zigbee.readAttribute(0x0001, 0x0020, [destEndpoint: 0x23])
}
def getMyMotion() {
    log.debug "getMyMotion()"
    zigbee.readAttribute(0x0406, 0x0000, [destEndpoint: 0x22])
}
def getMyLux() {
    log.debug "getMyLux()"
    zigbee.readAttribute(0x0400, 0x0000, [destEndpoint: 0x27]) 
    //Uint16 0x21 log: catchall: 0104 0400 27 01 0100 00 DD16 00 00 0000 01 01 000000212023
}
////
/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	return zigbee.readAttribute(0x0001, 0x0020) // Read the Battery Level
}

def refresh() {
	log.debug "refresh called"
	def refreshCmds = [
		//zigbee.readAttribute(0x0402, 0x0000, [destEndpoint: 0x26]),
        //"st rattr 0x${device.deviceNetworkId} 1 0x402 0", "delay 200",
        
        //zigbee.readAttribute(0x0001, 0x0020, [destEndpoint: 0x23])
		//"st rattr 0x${device.deviceNetworkId} 1 1 0x20", "delay 200"
	]

	//return refreshCmds + enrollResponse() + "Endpoint: " + getEndpointId() + 
    getMyBattery() + getMyTemperature() + getMyMotion() + getMyLux()
}

def configure() {
	// Device-Watch allows 2 check-in misses from device
	sendEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee"])

	String zigbeeEui = swapEndianHex(device.hub.zigbeeEui)
	log.debug "Configuring Reporting, IAS CIE, and Bindings., Eui: " + zigbeeEui + "Endpoint: " + getEndpointId()

	def enrollCmds = [
    	// 500 IAS CIE ZONES CONF
       	//zigbee.writeAttribute(0x0500, 0x0010, 0xf0, {${zigbeeEui}}, [destEndpoint: 0x23]),
 		//"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 500",
		"zcl global write 0x500 0x10 0xf0 {${zigbeeEui}}", "delay 200",
		"send 0x${device.deviceNetworkId} 1 0x23", "delay 500",
        
        // 406 OCCUPANCY sensor
        //"zdo bind 0x${device.deviceNetworkId} 1 0x22 0x406 {${zigbeeEui}} {}", 
        //"zcl global send-me-a-report 0x22 0x0406 0x18 0x3C 0x3C {0x3f800000}", "delay 200",  //MOtion 8bit btmap for sec
        zigbee.configureReporting(0x0406, 0x0000, 0x18, 0, 60, null, [destEndpoint: 0x22]) , //This is WORKING!!
       
       	// 402 TEMP SENSOR
        //"zcl global send-me-a-report 0x26 0x0402 0 0x29 60 60 {0A00}", "delay 200",
        //"zcl global send-me-a-report 0x26 0x0402 0x29 0x3C 0x3C {0x3f800000}", "delay 200"
  		//"zdo bind 0x${device.deviceNetworkId} ${endpointId} 1 0x402 {${device.zigbeeId}} {}", "delay 200",
        //"send 0x${device.deviceNetworkId} 1 1", "delay 1500",
         zigbee.configureReporting(0x0402, 0x0000, 0x29, 30, 300, 0x32, [destEndpoint: 0x26]),  //This is WORKING!! 0x0a = 10 , 

       	// 400 ILLUMINANCE SENSOR
         zigbee.configureReporting(0x0400, 0x0000, 0x21, 120, 600, 0x0A, [destEndpoint: 0x27])  //This is WORKING!!   0x32 = 50 0x01F4 = 500 as lux values are ca 500 per 1

		]
	// temperature minReportTime 30 seconds, maxReportTime 5 min. Reporting interval if no activity
	// battery minReport 30 seconds, maxReportTime 6 hrs by default
	return enrollCmds + zigbee.batteryConfig() + zigbee.temperatureConfig(30, 300) + refresh() // send refresh cmds as part of config
    //return enrollResponse()
}

def enrollResponse() {
	log.debug "Sending enroll response"
	String zigbeeEui = swapEndianHex(device.hub.zigbeeEui)
	[
		//Resending the CIE in case the enroll request is sent before CIE is written
		"zcl global write 0x500 0x10 0xf0 {${zigbeeEui}}", "delay 200",
		//"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 500",
		"send 0x${device.deviceNetworkId} 1 0x23", "delay 500",
        //zigbee.writeAttribute(0x0500, 0x0010, 0xf0, {${zigbeeEui}}, [destEndpoint: 0x23]),
         
		//Enroll Response
		"raw 0x500 {01 23 00 00 00}","delay 200",
		"send 0x${device.deviceNetworkId} 0x23 1 ", "delay 200"
        //zigbee.command(0x0500, 0x00, "0000")   
	]
}

private getEndpointId() {
	new BigInteger(device.endpointId, 16).toString()
}

private hex(value) {
	new BigInteger(Math.round(value).toString()).toString(16)
}

private String swapEndianHex(String hex) {
	reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
	int i = 0;
	int j = array.length - 1;
	byte tmp;
	while (j > i) {
		tmp = array[j];
		array[j] = array[i];
		array[i] = tmp;
		j--;
		i++;
	}
	return array
}