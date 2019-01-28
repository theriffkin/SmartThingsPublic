/*
 *  Copyright 2017 SmartThings
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
 *author: "Riivo.Pilvik", ocfDeviceType: "oic.d.smartplug", mnmn: "smartthings", vid: "generic-switch")
 *author: "SmartThings", mnmn: "SmartThings", vid: "generic-switch-power", ocfDeviceType: "oic.d.smartplug", runLocally: true, minHubCoreVersion: '000.017.0012', executeCommandsLocally: false)
  namespace: "smartthings", author: "SmartThings", ocfDeviceType: "oic.d.switch",
 */ 
metadata {
	// Automatically generated. Make future change here.
	definition(name: "Develco DIN Relay", namespace: "smartthings", author: "Riivo.Pilvik", ocfDeviceType: "oic.d.smartplug", mnmn: "SmartThings", vid: "generic-switch") {
        capability "Actuator"
        capability "Switch"
        capability "Power Meter"
        capability "Configuration"
        capability "Refresh"
        capability "Sensor"
        capability "Health Check"
        capability "Light"
		capability "Outlet"
	   //endpointId: "0x21,
		
        command "enrollResponse"
       	command "resetCurrentSummation"

	fingerprint  profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006,0702", manufacturer: "Develco Products A/S", model: "Smart Relay ZHWR-202", deviceJoinName: "Outlet"
	/*	fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0B04,0B05", outClusters: "0019", manufacturer: "CentraLite", model: "3200", deviceJoinName: "Outlet"*/
	}

	// simulator metadata
	simulator {
		// status messages
		status "on": "on/off: 1"
		status "off": "on/off: 0"

		// reply messages
		reply "zcl on-off on": "on/off: 1"
		reply "zcl on-off off": "on/off: 0"
	}

	/*preferences {
		section {
			image(name: 'educationalcontent', multiple: true, images: [
					"http://cdn.device-gse.smartthings.com/Outlet/US/OutletUS1.jpg",
					"http://cdn.device-gse.smartthings.com/Outlet/US/OutletUS2.jpg"
			])
		}
	}
    */
 preferences {        
    	//S2: Button (Level), Switch (toggle), Switch (on/off), Button (on/off), Pair of Buttons (toggle)....select input 1/2
        //input name: "deviceSetup", type: "enum", title: "Device Setup", options: ["Push", "Bi-Stable"], description: "Enter Device Setup, Push button is default", required: false
        
        //input name: "readConfiguration", type: "bool", title: "Read Advanced Configuration", description: "Enter Read Advanced Configuration", required: false
    }
	// UI tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name: "switch", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
			tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label: 'On', action: "switch.off", icon: "https://s3-eu-west-1.amazonaws.com/fibaro-smartthings/wallPlugUS/plug_us_blue.png", backgroundColor: "#00A0DC", nextState: "turningOff"
				attributeState "off", label: 'Off', action: "switch.on", icon: "https://s3-eu-west-1.amazonaws.com/fibaro-smartthings/wallPlugUS/plug_us_off.png", backgroundColor: "#ffffff", nextState: "turningOn"
				attributeState "turningOn", label: 'Turning On', action: "switch.off", icon: "st.switches.switch.on", backgroundColor: "#00A0DC", nextState: "turningOff"
				attributeState "turningOff", label: 'Turning Off', action: "switch.on", icon: "st.switches.switch.off", backgroundColor: "#ffffff", nextState: "turningOn"
			}
            tileAttribute ("power", key: "SECONDARY_CONTROL") {
				attributeState "power", label:'${currentValue} W', icon: "st.Appliances.appliances17" //icon: "st.unknown.unknown"
			}
		}
        
       /* valueTile("power", "device.power", width: 2, height: 2) {
			state("default", label:'${currentValue} W',
				backgroundColors:[
					[value: 0, color: "#ffffff"],
					[value: 1, color: "#00A0DC"]					
				]
			)
		}*/

        valueTile("powersum", "device.powersum", decoration: "flat", width: 2, height: 2) {
			//state("default", label:'${currentValue} \nkw/h')
			state("default", label:'${currentValue} \nkw/h')
		}
		standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: 'reset kw/h', action: "resetCurrentSummation", icon: "st.secondary.tools" // https://web.archive.org/web/20150825232647/http://scripts.3dgo.net/smartthings/icons
		}
        /*
        	standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat",width: 2, height: 2) {
			state "default", label:'reset kWh', action:"reset"
		}
        */
		standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: '', action: "refresh.refresh", icon: "st.secondary.refresh"
		}
        
        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}

		main "switch"
		details(["switch", "power", "powersum", "refresh", "configure"])
	}   

}

// Parse incoming device messages to generate events
def parse(String description) {
    log.trace "parse: description is $description"	
    
     if (description?.startsWith("on/off")) {
    	//Trigger read of cluster 6 ep 1/2, need to poll due to missing endpoint in attribute report
        def cmds = zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: 0x01]) + zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: 0x02])		
		return cmds.collect { new physicalgraph.device.HubAction(it) }        
  
    }
    
    def map = zigbee.parseDescriptionAsMap(description)
    if(map) {
    	if (map.clusterInt == 0x0006 && map.attrInt == 0x00 && map.commandInt == 0x01) {
			log.debug "parse: switch read from endpoint $map.sourceEndpoint"
            if(map.sourceEndpoint == "21") {            	
            	return createEvent(name: "switch", value: map.value == "01" ? "on" : "off")
            } else if(map.sourceEndpoint == "21") {            	
            	return childDevices[0].sendEvent(name: "switch", value: map.value == "01" ? "on" : "off")
            }            
        } else if (map.clusterInt == 0x0006 && map.commandInt == 0x0B) {
			log.debug "parse: switch set for endpoint $map.sourceEndpoint"
            if(map.sourceEndpoint == "21") {            	
            	return createEvent(name: "switch", value: map.data[0] == "01" ? "on" : "off")
            } else if(map.sourceEndpoint == "21") {            	
            	return childDevices[0].sendEvent(name: "switch", value: map.data[0] == "01" ? "on" : "off")
            }            
        } else if (map.clusterInt == 0x0006 && map.commandInt == 0x07) {
			if (Integer.parseInt(map.data[0], 16) == 0x00) {
				log.debug "On/Off reporting successful"
				return createEvent(name: "checkInterval", value: 60 * 22, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
			} else {
				log.warn "On/Off reporting failed with code: ${map.data[0]}"				
			}
        } else if (map.clusterInt == 0x0702 && map.commandInt == 0x07) {
			if (Integer.parseInt(map.data[0], 16) == 0x00) {
				log.debug "Metering reporting successful"				
			} else {
				log.warn "Metering reporting failed with code: ${map.data[0]}"				
			}
		} else if (map.clusterInt == 0x0702 && map.attrInt == 0x0400) {
			def powervalue = createEvent(name: "power", value: Integer.parseInt(map.value, 16)) 
            log.debug "parse:  power report for InstantaneousDemand:  $powervalue.value watts"
            return powervalue
         
        } else if (map.clusterInt == 0x0702 && map.attrInt == 0x0000) {
        	def powersummation_kwh =  (int) Long.parseLong(map.value, 32)/1000
			def powersummation = createEvent(name: "powersum", value: Math.round( (powersummation_kwh) * 10.0 ) / 10 ) 
			//def powersummation = createEvent(name: "powersum", value: (int) Long.parseLong(map.value, 32)) 
            log.debug "parse:  power report for CurrentSummationDelivered:  $powersummation.value watts"
           
            return powersummation
            
        }else if (map.clusterInt == 0xFC00 && map.commandInt == 0x04) {
			if (Integer.parseInt(map.data[0], 16) == 0x00) {
				log.debug "Device Setup successful"				
			} else {
				log.warn "Device Setup failed with code: ${map.data[0]}"                				
			}            		         
        } else if(!shouldProcessMessage(map)) {
        	log.trace "parse: map message ignored"            
        } else {
        	log.warn "parse: failed to process map $map"
        }
        return null
    }
    
    log.warn "parse: failed to process message"	
    return null
}

private boolean shouldProcessMessage(map) {
    // 0x0B is default response
    // 0x07 is configure reporting response
    boolean ignoredMessage = map.profileId != 0x0104 ||
        map.commandInt == 0x0B ||
        map.commandInt == 0x07 ||
        (map.data.size() > 0 && Integer.parseInt(map.data[0], 16) == 0x3e)
    return !ignoredMessage
}

def off() {
    	log.debug "off()"
     // zigbee.command(0x0006, 0x00)
	 //"st cmd 0x${device.deviceNetworkId} 0x21 6 0 {}"

	zigbee.off() +
	zigbee.readAttribute(0x0702, 0x0400, [destEndpoint: 0x21])

}

def on() {
	log.debug "on()"
      //zigbee.command(0x0006, 0x01)
       //"st cmd 0x${device.deviceNetworkId} 0x21 6 1 {}"
		zigbee.on()+
		zigbee.readAttribute(0x0702, 0x0400, [destEndpoint: 0x21])
}

void off2() {	
	log.trace "off2"        
    
    def actions = [new physicalgraph.device.HubAction("st cmd 0x${device.deviceNetworkId} 0x21 0x0006 0x00 {}")]    
    sendHubCommand(actions)
}

void on2() {	
	log.trace "on2"
    
    def actions = [new physicalgraph.device.HubAction("st cmd 0x${device.deviceNetworkId} 0x21 0x0006 0x01 {}")]    
    sendHubCommand(actions)    
}

def resetCurrentSummation (){	
	log.trace "Reset CurrentSummation"
		//Metering Cluster 0x0702, 0x0000, [destEndpoint: 0x21]  CurrentSummationDelivered   Uint48 0x25
        
    	zigbee.writeAttribute(0x0702, 0x0000, 0x25, 0x0000, [destEndpoint: 0x21])   
}
        
        
/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	return zigbee.onOffRefresh()
}



def refresh() {
	log.trace "refresh"    
    
    def refreshCmds = zigbee.onOffRefresh()+
    				  zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: 0x21]) +
    				  zigbee.readAttribute(0x0006, 0x0000, [destEndpoint: 0x21]) + 
                      zigbee.readAttribute(0x0702, 0x0400, [destEndpoint: 0x21])+
                   	  zigbee.readAttribute(0x0702, 0x0000, [destEndpoint: 0x21])
   
                
    return refreshCmds
}

def configure() {
	log.trace "configure"

    // Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
    // enrolls with default periodic reporting until newer 10 min interval is confirmed
    sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
    
    def configCmds = zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, 600, null, [destEndpoint: 0x21]) +
    				 zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, 600, null, [destEndpoint: 0x21]) +
                     zigbee.configureReporting(0x0702, 0x0400, 0x2A, 10, 600, 0x01, [destEndpoint: 0x21]) +	// Power InstantaneousDemand in every 1 watt change
                     zigbee.configureReporting(0x0702, 0x0000, 0x25, 60, 600, 0x0a, [destEndpoint: 0x21])	//Power CurrentSummationDelivered in 60to 600 sec in for every 10 watts
    
    //Configure device setup
   
    
    return refresh() + configCmds    
}