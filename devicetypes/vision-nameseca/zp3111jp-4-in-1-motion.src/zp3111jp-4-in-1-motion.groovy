metadata {
	definition (
			name: "ZP3111JP 4-in-1 Motion",
			namespace: "vision_nameseca",
			author: "Dennis Chen",
	) {
		capability "Sensor"
		capability "Configuration"
		capability "Motion Sensor"
		capability "Illuminance Measurement"
		capability "Relative Humidity Measurement"
		capability "Temperature Measurement"
		capability "Battery"
		capability "Tamper Alert"
		capability "Refresh"
		capability "Health Check"

		attribute "lastCheckin", "string"
		attribute "lastUpdate", "string"

		attribute "pendingChanges", "number"

		attribute "primaryStatus", "string"
		attribute "secondaryStatus", "string"
		attribute "pLight", "number"
		attribute "lxLight", "number"
		attribute "firmwareVersion", "string"

		command "clearTamper"

		fingerprint mfr:"0001", prod:"0001", model:"0001", deviceJoinName: "ZP3111JP 4-in-1 Motion"

	}

	simulator { }

	preferences {
		getParamInput(tempScaleParam)
		getParamInput(tempTriggerParam)
		getParamInput(humidityTriggerParam)
		getParamInput(lightTriggerParam)
		getParamInput(motionTimeParam)
		getParamInput(motionSensitivityParam)
		getParamInput(ledIndicatorModeParam)
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"mainTile", type: "generic", width: 6, height: 4){
			tileAttribute ("device.primaryStatus", key: "PRIMARY_CONTROL") {
				attributeState "primaryStatus",
						label:'${currentValue}',
						icon:"${resourcesUrl}motion-inactive.png",
						backgroundColor:"#ffffff"
				attributeState "No Motion",
						label:'NO MOTION',
						icon:"${resourcesUrl}motion-inactive.png",
						backgroundColor:"#ffffff"
				attributeState "Motion",
						label:'MOTION',
						icon:"st.motion.motion.active",
						backgroundColor:"#00a0dc"
			}
			tileAttribute ("device.secondaryStatus", key: "SECONDARY_CONTROL") {
				attributeState "default", label:'${currentValue}'
				attributeState "inactive", label:'NO MOTION'
				attributeState "active", label:'MOTION'
			}
		}

		valueTile("temperature", "device.temperature", width: 2, height: 2) {
			state "temperature", label:'${currentValue}°',
			icon: "${resourcesUrl}65566524-b7deff00-df85-11e9-9996-8e8988519987.png"

		}

		valueTile("humidity", "device.humidity", width: 2, height: 2){
			state "humidity", label:'${currentValue}%', icon: "${resourcesUrl}65566908-cd085d80-df86-11e9-8725-7106d492e599.png"
		}

		valueTile("pLight", "device.pLight", width: 2, height: 2){
			state "pLight", label:'${currentValue}%', icon: "${resourcesUrl}65567062-659edd80-df87-11e9-9c92-6d1fe722726a.png"
		}

		standardTile("tampering", "device.tamper", decoration: "flat", width: 2, height: 2) {
			state "clear", label:'No Tamper', icon: "${resourcesUrl}65567962-b49a4200-df8a-11e9-9a3c-c36d32b340be.png"
			state "detected", label:'Tamper', icon: "${resourcesUrl}65567848-540b0500-df8a-11e9-8198-4d3b757c61f5.png"
		}

		valueTile("battery", "device.battery", width: 2, height: 2){
			state "default", label:'${currentValue}%', icon: "${resourcesUrl}65567408-bf53d780-df88-11e9-860c-5a4531b4894d.png"
			state "1", label:'${currentValue}%', icon: "${resourcesUrl}65567848-540b0500-df8a-11e9-8198-4d3b757c61f5.png"
		}

		valueTile("pending", "device.pendingChanges", decoration: "flat", width: 2, height: 2){
			state "pendingChanges", label:'${currentValue} Change(s) Pending'
			state "0", label: 'No Pending Changes'
			state "-1", label:'Updating Settings'
			state "-2", label:'Refresh Pending'
			state "-3", label:'Refreshing'
		}

		valueTile("lastUpdate", "device.lastUpdate", decoration: "flat", width: 2, height: 2){
			state "lastUpdate", label:'Settings\nUpdated\n\n${currentValue}'
		}

		valueTile("lastActivity", "device.lastCheckin", decoration: "flat", width: 2, height: 2){
			state "lastCheckin", label:'Last\nActivity\n\n${currentValue}'
		}

		valueTile("firmwareVersion", "device.firmwareVersion", decoration: "flat", width: 2, height: 2){
			state "firmwareVersion", label:'Firmware \n${currentValue}'
		}

		standardTile("refresh", "device.refresh", width: 2, height: 2, decoration: "flat") {
			state "default", label: "Power Refresh", action: "refresh", icon:"${resourcesUrl}65655148-cdb7f700-e04d-11e9-9456-78a919245ac9.png"
		}

		main("mainTile")
		details(["mainTile", "temperature", "humidity", "pLight", "tampering", "battery", "refresh"])
	}
}

private getResourcesUrl() {
	return "https://user-images.githubusercontent.com/8133436/"
}

private getParamInput(param) {
	input "${param.prefName}", "number",
			title: "${param.name}:",
			defaultValue: "${param.val}",
			required: false,
			displayDuringSetup: true,
			range: "${param.range}"
}

private getDisplayedDescriptionText(eventMap) {
	def deviceName = "${device.displayName}"
	if (eventMap?.displayed && eventMap?.descriptionText && !eventMap?.descriptionText?.contains(deviceName)) {
		return "${deviceName}: ${eventMap.descriptionText}"
	}
	else {
		return eventMap?.descriptionText
	}
}

def updated() {
	if (!isDuplicateCommand(state.lastUpdated, 3000)) {
		state.lastUpdated = new Date().time
		logTrace "updated()"

		initializeOffsets()
		initializePrimaryTile()

		if (!getAttrValue("tamper")) {
			sendEvent(createTamperEventMap("clear"))
		}

		if (checkForPendingChanges()) {
			logForceWakeupMessage("The configuration will be updated the next time the device wakes up.")
		}
	}
}

def configure() {
	logTrace "configure()"

	def cmds = []
	if (!getAttrValue("firmwareVersion")) {
		sendMotionEvents(0xFF)
		cmds << "delay 2000"
		cmds << versionGetCmd()
	}

	if (state.pendingRefresh != false || state.refreshAll || !allAttributesHaveValues()) {
		runIn(5, finalizeConfiguration)
		sendEvent(createEventMap("pendingChanges", -3, "", false))

		cmds += [
				batteryGetCmd(),
				sensorMultilevelGetCmd(tempSensorType),
				sensorMultilevelGetCmd(humiditySensorType),
				sensorMultilevelGetCmd(lightSensorType)
		]
	}
	else if (canReportBattery()) {
		cmds << batteryGetCmd()
	}

	if (state.configured != true || state.refreshAll) {
		createCheckIntervalEvent(checkinIntervalSettingSeconds)
		configParams.each { param ->
			cmds << configGetCmd(param)
		}
	}
	else {
		configParams.each { param ->
			cmds += updateConfigVal(param)
		}

		if (checkinIntervalChanged) {
			logTrace "Updating wakeup interval"
			cmds << wakeUpIntervalSetCmd(checkinIntervalSettingSeconds)
			cmds << wakeUpIntervalGetCmd()
		}
	}

	return cmds ? delayBetween(cmds, 1000) : []
}

private allAttributesHaveValues() {
	return (getAttrValue("temperature") != null &&
			getAttrValue("humidity") != null &&
			getAttrValue("illuminance") != null &&
			getAttrValue("battery") != null)
}

private updateConfigVal(param) {
	def result = []
	if (hasPendingChange(param)) {
		def newVal = param.val
		logDebug "${param.prefName}(#${param.num}): changing ${getParamStoredVal(param)} to ${newVal}"
		result << configSetCmd(param, newVal)
		result << configGetCmd(param)
	}
	return result
}

private getCheckinIntervalChanged() {
	return (state.checkinInterval != checkinIntervalSettingSeconds)
}

private hasPendingChange(param) {

	if ((param.num != ledIndicatorModeParam.num || ledIndicatorModeMatchesFirmware(param.val)) && (param.num != motionTimeParam.num || motionTimeMatchesFirmware(param.val))) {
		return (param.val != getParamStoredVal(param))
	}
	else {
		return false
	}
}

private ledIndicatorModeMatchesFirmware(val) {
	if (firmwareVersion >= firmwareV2 || val != 4) {
		return true
	}
	else {
		log.warn "LED Indicator Mode #4 is only available in firmware ${firmwareV2} and above."
		return false
	}
}

private motionTimeMatchesFirmware(val) {
	if (firmwareVersion < firmwareV3 || (val >= 15 && val <= 60)) {
		return true
	}
	else {
		log.warn "${val} Seconds is not a valid Motion Time for Firmware ${firmwareV3}."
		return false
	}
}

private getRoundPrimaryStatusSetting() {
	return settings?.roundPrimaryStatus ?: false
}
private getDecimalPlacesSetting() {
	return safeToInt(roundVal((settings?.decimalPlaces != null ? settings?.decimalPlaces : 2), 0))
}
private getPrimaryTileStatusSetting() {
	return settings?.primaryTileStatus ?: "motion"
}
private getSecondaryTileStatusSetting() {
	return settings?.secondaryTileStatus ?: "none"
}
private getTempOffsetSetting() {
	return safeToDec(settings?.tempOffset, 0)
}
private getHumidityOffsetSetting() {
	return safeToDec(settings?.humidityOffset, 0)
}
private getLightOffsetSetting() {
	return safeToDec(settings?.lightOffset, 0)
}
private getLxLightOffsetSetting() {
	return safeToDec(settings?.lxLightOffset, 0)
}
private getReportLxSetting() {
	return (settings?.reportLx ?: false)
}
private getMaxLxSetting() {
	return safeToInt(settings?.maxLx, 50)
}
private getCheckinIntervalSetting() {
	return safeToInt(settings?.checkinInterval, 4)
}
private getCheckinIntervalSettingSeconds() {
	if (checkinIntervalSetting == 0) {
		return (4 * 60 * 60)
	}
	else {
		return (checkinIntervalSetting * 60 * 60)
	}
}
private getBatteryReportingIntervalSetting() {
	return safeToInt(settings?.reportBatteryEvery, 12)
}
private getBatteryReportingIntervalSettingSeconds() {
	return (batteryReportingIntervalSetting * 60 * 60)
}
private getAutoClearTamperSetting() {
	return (settings?.autoClearTamper ?: false)
}
private getDebugOutputSetting() {
	return (settings?.debugOutput || settings?.debugOutput == null)
}
private getNameValueSettingDesc(nameValueMap) {
	def desc = ""
	nameValueMap?.sort { it.value }.each {
		desc = "${desc}\n(${it.value} = ${it.name})"
	}
	return desc
}
private getTempUnits() {
	return [
			[name: "Celsius", unit: "C", value: 0],
			[name: "Fahrenheit", unit: "F", value: 1]
	]
}
private getLedIndicatorModes() {
	return [
			[name: "Temperature Off / Motion Off", value: 1],
			[name: "Temperature Pulse / Motion Flash", value: 2],
			[name: "Temperature Flash / Motion Flash", value: 3],
	]
}

private getFirmwareVersion() {
	return safeToDec(getAttrValue("firmwareVersion"), 0.0)
}
private getFirmwareV1() { return 5.1 }
private getFirmwareV2() { return 16.9 }
private getFirmwareV3() { return 17.9 }

private getTempSensorType() { return 1 }
private getHumiditySensorType() { return 5 }
private getLightSensorType() { return 3 }

private getConfigParams() {
	return [
			tempScaleParam,
			tempTriggerParam,
			humidityTriggerParam,
			lightTriggerParam,
			motionTimeParam,
			motionSensitivityParam,
			ledIndicatorModeParam
	]
}
private getTempScaleParam() {
	return createConfigParamMap(1, "Temperature Scale [0-1]${getNameValueSettingDesc(tempUnits)}", 1, "tempScale", "0..1", 0)
}
private getTempTriggerParam() {
	return createConfigParamMap(2, "Temperature Change Trigger [1-50]\n(1 = 0.1°)\n(50 = 5.0°)", 1, "tempTrigger", "1..50", 10)
}
private getHumidityTriggerParam() {
	return createConfigParamMap(3, "Humidity Change Trigger [1-50]\n(1% - 50%)", 1, "humidityTrigger", "1..50", 10)
}
private getLightTriggerParam() {
	return createConfigParamMap(4, "Light Change Trigger [0,5-50]\n(OFF,5% - 50%)", 1, "lightTrigger", "0..50", 10)
}
private getMotionTimeParam() {
	return createConfigParamMap(5, "Motion Retrigger Time [1-255]\n(1 Minute - 255 Minutes)", 1, "motionTime", "1..255", 3)
}
private getMotionSensitivityParam() {
	return createConfigParamMap(6, "Motion Sensitivity [1-7]\n(1 = Most Sensitive)(7 = Least Sensitive)", 1, "motionSensitivity", "1..7", 4)
}
private getLedIndicatorModeParam() {
	return createConfigParamMap(7, "LED Indicator Mode [1-3]${getNameValueSettingDesc(ledIndicatorModes)}", 1, "ledIndicatorMode", "1..3", 3)
}
private getParamStoredVal(param) {
	return state["configVal${param.num}"]
}
private createConfigParamMap(num, name, size, prefName, range, val) {
	if (settings?."${prefName}" != null) {
		val = settings?."${prefName}"
	}
	return [
			num: num,
			name: name,
			size: size,
			prefName: prefName,
			range: range,
			val: val
	]
}
def parse(String description) {
	def result = []

	sendLastCheckinEvent()

	def cmd = zwave.parse(description, commandClassVersions)
	if (cmd) {
		result += zwaveEvent(cmd)
	}
	else {
		logDebug "Unable to parse description: $description"
	}
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapCmd = cmd.encapsulatedCommand(getCommandClassVersions())

	def result = []
	if (encapCmd) {
		result += zwaveEvent(encapCmd)
	}
	else {
		log.warn "Unable to extract encapsulated cmd from $cmd"
	}
	return result
}
private getCommandClassVersions() {
	[
			0x20: 1,  // Basic
			0x31: 5,	// Sensor Multilevel (v7)
			0x59: 1,  // AssociationGrpInfo
			0x5A: 1,  // DeviceResetLocally
			0x5E: 2,  // ZwaveplusInfo
			0x70: 2,  // Configuration
			0x71: 3,  // Alarm v1 or Notification v4
			0x72: 2,  // ManufacturerSpecific
			0x73: 1,  // Powerlevel
			0x7A: 2,  // FirmwareUpdateMd
			0x80: 1,  // Battery
			0x84: 2,  // WakeUp
			0x85: 2,  // Association
			0x86: 1,	// Version (2)
			0x98: 1		// Security
	]
}
def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpIntervalReport cmd) {
	state.checkinInterval = cmd.seconds

	sendUpdatingEvent()

	def msg = "Minimum Check-in Interval is ${cmd.seconds / 60} Minutes"
	if (cmd.seconds == 600) {
		log.warn "$msg"
	}
	else {
		logDebug "$msg"
	}

	runIn(5, finalizeConfiguration)

	return [ createCheckIntervalEvent(cmd.seconds) ]
}
def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) {
	logTrace "WakeUpNotification: $cmd"
	def cmds = []

	logDebug "Device Woke Up"

	if (getAttrValue("firmwareVersion")) {
		cmds += configure()

		if (cmds) {
			cmds << "delay 2000"
		}

		cmds << wakeUpNoMoreInfoCmd()
	}
	else {
		cmds << versionGetCmd()
	}

	return cmds ? response(cmds) : []
}
def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def val = (cmd.batteryLevel == 0xFF ? 1 : cmd.batteryLevel)

	if (val > 100)
	{
		val = 100
	}
	else if (val < 1)
	{
		val = 1
	}

	state.lastBatteryReport = new Date().time
	[
			createEvent(createEventMap("battery", val, "%", true))
	]
}
def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	logTrace "ManufacturerSpecificReport: ${cmd}"
	return []
}
def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
	logTrace "VersionReport: ${cmd}"

	def version = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
	logDebug "Firmware Version: ${version}"

	def result = []
	if (getAttrValue("firmwareVersion") != "${version}") {
		result << createEvent(name: "firmwareVersion", value: "${version}", displayed: false)
	}
	return result
}
def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
	logTrace "ConfigurationReport: ${cmd}"
	sendUpdatingEvent()

	def val = cmd.scaledConfigurationValue

	def configParam = configParams.find { param ->
		param.num == cmd.parameterNumber
	}

	if (configParam) {
		logDebug "${configParam.name}(#${configParam.num}) = ${val}"
		state["configVal${cmd.parameterNumber}"] = val
	}
	else {
		logDebug "Parameter ${cmd.parameterNumber} = ${val}"
	}

	runIn(5, finalizeConfiguration)
	return []
}
def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	logTrace "BasicReport: $cmd"
	if (state.refreshAll || !device.currentValue("motion")) {
		sendMotionEvents(cmd.value)
	}
	return []
}
def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	logTrace "BasicSet: $cmd"
	sendMotionEvents(cmd.value)
	return []
}
def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
	logTrace "NotificationReport: $cmd"
	def result = []
	if (cmd.notificationType == 7) {
		if (cmd.eventParameter[0] == 3 || cmd.event == 3) {
			result += handleTamperEvent(cmd.v1AlarmLevel)
		}
	}
	return result
}
def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
	logTrace "SensorMultilevelReport: ${cmd}"

	def eventMaps = []
	switch (cmd.sensorType) {
		case tempSensorType:
			def unit = tempUnits.find { it.value == cmd.scale }?.unit
			def temp = cmd.scaledSensorValue
			eventMaps += createTempEventMaps(temp, false)
			break
		case humiditySensorType:
			eventMaps += createHumidityEventMaps(cmd.scaledSensorValue, false)
			break
		case lightSensorType:
			eventMaps += createLightEventMaps(cmd.scaledSensorValue, false)
			break
	}

	eventMaps += createStatusEventMaps(eventMaps, false)

	def result = []
	eventMaps?.each {
		logTrace "Creating Event: ${it}"
		it.descriptionText = getDisplayedDescriptionText(it)
		result << createEvent(it)
	}
	return result
}
def zwaveEvent(physicalgraph.zwave.Command cmd) {
	logDebug "Unhandled Command: $cmd"
	return []
}
private createCheckIntervalEvent(seconds) {
	// Set the Health Check interval so that it reports offline 5 minutes after it's missed 2 checkins.
	def val = ((seconds * 2) + (5 * 60))

	def eventMap = createEventMap("checkInterval", val, "", false)

	eventMap.data = [protocol: "zwave", hubHardwareId: device.hub.hardwareID]

	return createEvent(eventMap)
}
private sendLastCheckinEvent() {
	if (!isDuplicateCommand(state.lastCheckinTime, 60000)) {
		state.lastCheckinTime = new Date().time

		sendEvent(createEventMap("lastCheckin", convertToLocalTimeString(new Date()), "", false))
	}
}
private canReportBattery() {
	def reportEveryMS = (batteryReportingIntervalSettingSeconds * 1000)

	return (!state.lastBatteryReport || ((new Date().time) - state.lastBatteryReport > reportEveryMS))
}
private sendUpdatingEvent() {
	if (getAttrValue("pendingChanges") != -1) {
		sendEvent(createEventMap("pendingChanges", -1, "", false))
	}
}
private sendMotionEvents(val) {
	def motionVal = (val == 0xFF ? "active" : "inactive")

	def eventMaps = []
	eventMaps += createEventMaps("motion", motionVal, "", true, false)
	eventMaps += createStatusEventMaps(eventMaps, false)

	eventMaps?.each {
		it.descriptionText = getDisplayedDescriptionText(it)
		sendEvent(it)
	}
}
private handleTamperEvent(val)
{
	def result = []
	def tamperVal

	if (val == 0xFF)
	{
		tamperVal = "detected"
	}
	else if (val == 0)
	{
//		if (autoClearTamperSetting)
//		{
			tamperVal = "clear"
//		} else
//		{
			logDebug "Tamper is Clear"
//		}
	}

	if(tamperVal)
	{
		result << createEvent(createTamperEventMap(tamperVal))
	}
	return result
}
private createTempEventMaps(val, onlyIfNew) {
	state.actualTemp = val
	def scale = getTemperatureScale()
	def offsetVal = applyOffset(val, tempOffsetSetting, "Temperature", "°${scale}")
	return createEventMaps("temperature", offsetVal, scale, true, onlyIfNew)
}
private createHumidityEventMaps(val, onlyIfNew) {
	state.actualHumidity = val
	def offsetVal = applyOffset(val, humidityOffsetSetting, "Humidity", "%")
	return createEventMaps("humidity", Math.round(offsetVal), "%", true, onlyIfNew)
}
private createLightEventMaps(val, onlyIfNew) {
	state.actualLight = val
	def pOffsetVal = applyOffset(val, lightOffsetSetting, "Light", "%")
	def lxOffsetVal = (val == 100) ? maxLxSetting : applyOffset(calculateLxVal(val), lxLightOffsetSetting, "Light", "lx")
	def lightOffsetVal = reportLxSetting ? Math.round(lxOffsetVal) : Math.round(pOffsetVal)
	def lightUnit = reportLxSetting ? "lx" : "%"

	def result = []
	result += createEventMaps("pLight", pOffsetVal, "%", false, onlyIfNew)
	result += createEventMaps("lxLight", lxOffsetVal, "lx", false, onlyIfNew)
	result += createEventMaps("illuminance", lightOffsetVal, lightUnit, true, onlyIfNew)
	return result
}
private calculateLxVal(pVal) {
	def multiplier = lxConversionData.find {
		pVal >= it.min && pVal <= it.max
	}?.multiplier ?: 0.5312
	def lxVal = pVal * multiplier
	return Math.round(safeToDec(lxVal) * 100) / 100
}
private getLxConversionData() {
	return [
			[min: 0, max: 9.99, multiplier: 0.4451],
			[min: 10, max: 19.99, multiplier: 0.563],
			[min: 20, max: 29.99, multiplier: 0.538],
			[min: 30, max: 39.99, multiplier: 0.536],
			[min: 40, max: 49.99, multiplier: 0.559],
			[min: 50, max: 59.99, multiplier: 0.6474],
			[min: 60, max: 69.99, multiplier: 0.5222],
			[min: 70, max: 79.99, multiplier: 0.5204],
			[min: 80, max: 89.99, multiplier: 0.4965],
			[min: 90, max: 100, multiplier: 0.4843]
	]
}
private applyOffset(val, offsetVal, name, unit) {
	if (offsetVal) {
		logDebug "Before Applying ${offsetVal}${unit} ${name} Offset to ${val}${unit}"
		val = (safeToDec(val, 0) + safeToDec(offsetVal, 0))
		logDebug "After Applying ${offsetVal}${unit} ${name} Offset to ${val}${unit}"
	}
	return roundVal(val, decimalPlacesSetting)
}
private createStatusEventMaps(eventMaps, onlyIfNew) {
	def result = []

	def primaryStatus = eventMaps?.find { it.name == primaryTileStatusSetting }?.descriptionText
	if (primaryStatus) {
		if (roundPrimaryStatusSetting) {
			primaryStatus = formatPrimaryStatusNumber(primaryStatus)
		}
		result += createEventMaps("primaryStatus", primaryStatus, "", false, onlyIfNew)
	}

	def secondaryStatus = getSecondaryStatus(eventMaps)
	if (secondaryStatus || secondaryTileStatusSetting == "none") {
		result += createEventMaps("secondaryStatus", secondaryStatus, "", false, onlyIfNew)
	}
	return result
}
private formatPrimaryStatusNumber(val) {
	def unit
	["% LIGHT", "% RH", " LUX", "°F", "°C"].each {
		if ("${val}".contains(it)) {
			unit = "${it}"
		}
	}

	if (unit) {
		def numericVal = safeToDec("${val}".replace("${unit}", ""))
		return "${Math.round(numericVal)}${unit}"
	}
	else {
		return val
	}
}

def clearTamper() {
	logDebug "Clearing Tamper"
	sendEvent(createTamperEventMap("clear"))
	return []
}

private getSecondaryStatus(eventMaps) {
	def status = ""
	if (secondaryTileStatusSetting == "combined"){
		def motionStatus = getAttrStatusText("motion", eventMaps)
		def lightStatus = getAttrStatusText("lxLight", eventMaps)
		def tempStatus = getAttrStatusText("temperature", eventMaps)
		def humidityStatus = getAttrStatusText("humidity", eventMaps)
		status = "${motionStatus} / ${tempStatus} / ${humidityStatus} / ${lightStatus}"
	}
	else if (status != "none") {
		status = getAttrStatusText(secondaryTileStatusSetting, eventMaps)
	}
	return status
}
private getAttrStatusText(attrName, eventMaps=null) {
	def status = (eventMaps?.find { it.name == attrName }?.descriptionText)
	if (status) {
		return status
	}
	else {
		return getDescriptionText(device.currentState(attrName))
	}
}
private getDescriptionText(data) {
	switch (data?.name ?: "") {
		case "motion":
			return "${data.value}" == "active" ? "Motion" : "No Motion"
			break
		case "temperature":
			return "${data.value}°${data.unit}"
			break
		case "humidity":
			return  "${data.value}% RH"
			break
		case "lxLight":
			return "${data.value} LUX"
		case "pLight":
			return "${data.value}% LIGHT"
			break
		default:
			return ""
	}
}

def refresh() {
	log.debug "sending battery refresh command"
	batteryGetCmd()
}

private createTamperEventMap(val) {
	return createEventMap("tamper", val)
}
private createEventMaps(eventName, newVal, unit, displayed, onlyIfNew) {
	def result = []

	if (!onlyIfNew || getAttrValue(eventName) != newVal)
	{
		def eventMap = createEventMap(eventName, newVal, unit, displayed)
		def desc = getDescriptionText(eventMap)

		if (desc)
		{
			eventMap.descriptionText = desc
		}
		result << eventMap
	}
	return result
}
private createEventMap(eventName, newVal, unit="", displayed=null) {
	def oldVal = getAttrValue(eventName)
	def isNew = "${oldVal}" != "${newVal}"
	def desc = "${eventName.capitalize()} is ${newVal}${unit}"

	if (displayed == null) {
		displayed = isNew
	}

	if (displayed) {
		logDebug "${desc}"
	}
	else {
		logTrace "${desc}"
	}

	return [
			name: eventName,
			value: newVal,
			displayed: displayed,
			isStateChange: true,
			unit: unit,
			descriptionText: "${device.displayName}: ${desc}"
	]
}
private getAttrValue(attrName) {
	try {
		return device?.currentValue("${attrName}")
	}
	catch (ex) {
		logTrace "$ex"
		return null
	}
}
private wakeUpIntervalGetCmd() {
	return secureCmd(zwave.wakeUpV2.wakeUpIntervalGet())
}
private wakeUpIntervalSetCmd(val) {
	return secureCmd(zwave.wakeUpV2.wakeUpIntervalSet(seconds:val, nodeid:zwaveHubNodeId))
}
private wakeUpNoMoreInfoCmd() {
	return secureCmd(zwave.wakeUpV2.wakeUpNoMoreInformation())
}
private batteryGetCmd() {
	return secureCmd(zwave.batteryV1.batteryGet())
}
private versionGetCmd() {
	return secureCmd(zwave.versionV1.versionGet())
}
private sensorMultilevelGetCmd(sensorType) {
	return secureCmd(zwave.sensorMultilevelV5.sensorMultilevelGet(scale: 2, sensorType: sensorType))
}
private configSetCmd(param, val) {
	return secureCmd(zwave.configurationV2.configurationSet(parameterNumber: param.num, size: param.size, scaledConfigurationValue: val))
}
private configGetCmd(param) {
	return secureCmd(zwave.configurationV2.configurationGet(parameterNumber: param.num))
}
private secureCmd(cmd) {
	if (zwaveInfo?.zw?.contains("s") || ("0x98" in device.rawDescription?.split(" ")))
	{
		log.debug "running secureCmd"
		return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	}
	else
	{
		return cmd.format()
	}
}
private safeToInt(val, defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}
private safeToDec(val, defaultVal=0) {
	def decVal = "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal
	return "${val}"?.isBigDecimal() ? "${val}".toBigDecimal() : defaultVal
}
private roundVal(val, places) {
	if ("${val}".isNumber()) {
		def dblVal = "${val}".toDouble()
		if (places) {
			return dblVal.round(places)
		}
		else {
			dblVal.round()
		}
	}
	else {
		return val
	}
}
private convertToLocalTimeString(dt) {
	def timeZoneId = location?.timeZone?.ID
	def localDt = "$dt"
	try {
		if (timeZoneId) {
			localDt = dt.format("MM/dd/yyyy hh:mm:ss a", TimeZone.getTimeZone(timeZoneId))
		}
	}
	catch (e) {
	}
	return localDt
}
private isDuplicateCommand(lastExecuted, allowedMil) {
	!lastExecuted ? false : (lastExecuted + allowedMil > new Date().time)
}
private logDebug(msg) {
	if (debugOutputSetting) {
		log.debug "$msg"
	}
}
private logTrace(msg) {
	// log.trace "$msg"
}
