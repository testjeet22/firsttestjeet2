
/*****************************************************************************************************************************
 *
 * � RBC CONFIDENTIAL
 *___________________
 *
 * This file provides functions for all deployment related steps using UCD.
 * 		-	Uses UCD REST API's to interface with UCD and trigger deployments
 * 		-	Uses curl to send/receive UCD REST API calls
 * 		- 	All functions are OS agnostic.
 * 
 * Pre-Requisite: Requires curl to be installed on the build agent where this runs.
 * 				  Requires pipeline environment variables to be set by calling commonUtils.initializePipelineEnv()
 * 				  before using any of the functions in this class as these functions refer to env variables
 *
 * Authors: Harshavardhana Badakere
 *
 * Contact: Digital-DevOps <digitaldevops@rbc.com>
 *
 **/
package deploy

import groovy.util.*
import groovy.json.*
import java.util.regex.*

/*
 * 	Method			: triggerVersionImport
 *
 * 	Purpose			: Triggers the version import of the artifact from nexus to UCD.
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: UCD Component Name, component version, username, password
 *
 *  Returns			: true or false
 */
def triggerVersionImport(ucdComponentName,pomVersion,ucdUser,ucdPass) {

	def jsonData = "\"{ \"component\": \\\"$ucdComponentName\\\",\"properties\": { \"version\": \"$pomVersion\" } }\""
	def curlCmd = "https://$ucdUser:$ucdPass@" + readYaml(text: libraryResource('config.yml')).ucd_server + "/cli/component/integrate -d $jsonData"
	def result

	println "Fetching the artifact from Nexus to UCD..."
	result = "${env.TERMINAL}" (script: "${env.SUPPRESS_TERMINAL_OUTPUT}curl -sk -X PUT $curlCmd", returnStdout: true).trim()
	
	return result
}

/*
 * 	Method			: checkVersionImportStatus
 *
 * 	Purpose			: Checks the status of version import of the artifact from nexus to UCD by polling based on a timer.
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: UCD Component Name, component version, username, password, time out value
 *
 *  Returns			: true or false
 */
def checkVersionImportStatus (ucdComponentName, nexusArtifactVersion, ucdUser, ucdPass, timeOut = 240) {

	def ucdUrl = readYaml(text: libraryResource('config.yml')).ucd_server
	def curlCmd = "https://$ucdUser:$ucdPass@$ucdUrl/cli/version/getVersionId --data-urlencode \"component=$ucdComponentName\" --data \"version=$nexusArtifactVersion\""
	def result
	def versionArtifactsJson

	println "Checking the status of Nexus import..."
	while (timeOut > 0) {
		result = "${env.TERMINAL}" (script: "${env.SUPPRESS_TERMINAL_OUTPUT}curl -sk -G $curlCmd", returnStdout: true).trim()
		
		if (result.endsWith('could not be resolved.') && result.contains('"version"')) {
			sleep 15
			timeOut -= 15
		}
		else if (result.matches (/^[a-zA-Z0-9].*[-][a-zA-Z0-9].*[-][a-zA-Z0-9].*[-][a-zA-Z0-9].*/)) {
			
			while (timeOut > 0) {
				versionArtifactsJson = "${env.TERMINAL}" (script: "${env.SUPPRESS_TERMINAL_OUTPUT}" +
													"curl -sk https://$ucdUser:$ucdPass@$ucdUrl/cli/version/listVersionArtifacts?{version=$result}",
													returnStdout: true).trim()
				if (readJSON(text: versionArtifactsJson).size() == 0) {
					sleep 15
					timeOut -= 15
				}
				else
					return result
			}
			return "Nexus Import Request timeout:: No artifacts found in version!!"
		}
		else {
			return "Nexus Import Failed!:$result"
		}
	}
	return "Nexus Import Request timed out!!"
}

/*
 * 	Method			: triggerDeployment
 *
 * 	Purpose			: Triggers deployment of the component into a specific deployment Env.
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: UCD Component Name, UCD Application Name, UCD Application Process name, 
 *  				  Deployment Environment, component version, additional component list, username, password
 *
 *  Returns			: Request ID on sucess or null
 */
def triggerDeployment(ucdComponentName,ucdApplicationNameJson,ucdApplicationProcess,environment,nexusArtifactId, ucdComponentList, ucdUser,ucdPass) {

	def componentCurlCmd = []
	def requestId, result

	componentCurlCmd [0] = "{ \"version\": \"$nexusArtifactId\",\"component\": \\\"$ucdComponentName\\\" }"
	for (i=0; i < ucdComponentList.size(); i++) {
		componentCurlCmd [i+1] = "{ \"version\": \"latest\",\"component\": \\\"${ucdComponentList[i]}\\\" }"
	}

	def jsonData = "\"{ \"application\": \\\"$ucdApplicationNameJson\\\",\"applicationProcess\": \\\"$ucdApplicationProcess\\\",\"environment\": \"$environment\",\"versions\": [ ${componentCurlCmd.join(",")} ] }\""
	def curlCmd = "https://$ucdUser:$ucdPass@" + readYaml(text: libraryResource('config.yml')).ucd_server + "/cli/applicationProcessRequest/request -d $jsonData"

	println "Deploying the UCD artifact..."
	result = "${env.TERMINAL}" (script: "${env.SUPPRESS_TERMINAL_OUTPUT}curl -sk -X PUT $curlCmd", returnStdout: true).trim()
	
	if ( result.contains ("requestId")) {
		requestId = readJSON text: result
		return requestId.requestId
	}
	else {
		return result
	}
}

/*
 * 	Method			: checkDeploymentStatus
 *
 * 	Purpose			: Checks the status of the deployment by polling UCD based on a timer.
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: Request ID, username, password, timeout value
 *
 *  Returns			: true or false
 */
def checkDeploymentStatus(requestId,version,ucdUser,ucdPass, timeOut = 600) {

	def curlCmd = "https://$ucdUser:$ucdPass@" + readYaml(text: libraryResource('config.yml')).ucd_server + "/cli/applicationProcessRequest/requestStatus --data-urlencode \"request=$requestId\""
	def jsonObj

	println "Checking the status of deployment by polling UCD..."
	while (timeOut > 0) {
		result = "${env.TERMINAL}" (script: "${env.SUPPRESS_TERMINAL_OUTPUT}curl -sk -G $curlCmd", returnStdout: true).trim()
		
		jsonObj = readJSON text: result
		if (jsonObj.status.trim().equals("CLOSED")) {
			return jsonObj.result.trim()
		}
		sleep 15
		timeOut -= 15
	}
	
	return "Deployment failed : REQUEST TIMED OUT!!!"
}

/*
 * 	Method			: getComponentsInApplication
 *
 * 	Purpose			: Gets all components that are included in any given application. 
 * 					  This is needed as there other supporting artifacts like base server, JKS etc. that needs to be deployed with the component.
 * 					  These could vary from one project to the other.
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: Application Name, username, password, Exclusion List
 *
 *  Returns			: List of components if successfull or null
 */
def getComponentsInApplication(ucdApplicationName,ucdUser,ucdPass, exclusionList) {

	def result
	def componentsList = []
	def curlCmd = "https://$ucdUser:$ucdPass@" + readYaml(text: libraryResource('config.yml')).ucd_server + "/cli/application/componentsInApplication --data-urlencode \"application=$ucdApplicationName\""

	println "Getting the list of components for ${env.REPO_NAME} from UCD..."
	result = "${env.TERMINAL}" (script: "${env.SUPPRESS_TERMINAL_OUTPUT}curl -sk -G $curlCmd", returnStdout: true).trim()
	
	//Check for errors
	if (result.startsWith('No application for') || result.toLowerCase().contains('error'))
	{
		println("\n==========================================================================\n" +
				"Failed to retrieve Component List!: $result								" +
				"=============================================================================\n")
		return null
	}
	else { //Trim all the elements in the List
		componentsList = extractComponentsList(result)
		//Remove unwanted components from the result list
		componentsList.removeAll(exclusionList)
		return componentsList
	}
}

/*
 * 	Method			: getApplicationInfo
 *
 * 	Purpose			: Gets information about any given application.
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: Application Name, username, password
 *
 *  Returns			: application information json or error string
 */
def getApplicationInfo(ucdApplicationName,ucdUser,ucdPass) {

	def result
	def curlCmd = "https://$ucdUser:$ucdPass@" + readYaml(text: libraryResource('config.yml')).ucd_server + "/cli/application/info --data-urlencode \"application=$ucdApplicationName\""

	println "Getting the Application information for ${ucdApplicationName} from UCD..."
	result = "${env.TERMINAL}" (script: "${env.SUPPRESS_TERMINAL_OUTPUT}curl -sk -H \"Connection: close\" -G $curlCmd", returnStdout: true).trim()

	//Check for errors
	if (result.startsWith('No application for') || result.toLowerCase().contains('error'))
		return null

	return result
}

/*
 * 	Method			: triggerInitialization
 *
 * 	Purpose			: Triggers Initialization of Ucd setup for new projects.
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: Init Application Name, Process Name, Environment, Application Name, Application type, username, password
 *
 *  Returns			: RequestID or error string
 */
def triggerInitialization(initAppName, processName, createTemplate, application, ucdUser, ucdPass) {

	def componentCurlCmd = []
	def requestId, result

	def jsonData = "\"{ \"application\": \\\"$initAppName\\\"," +
						"\"applicationProcess\": \\\"$processName\\\"," +
						"\"environment\": \"DEV\"," +
						"\"properties\": {" +
									"\"appName\": \\\"${application.name}\\\"," +
									"\"appCode\": \"${application.appCode}\"," +
									"\"artifactType\": \"${application.artifactType}\"," +
									"\"repo\": \\\"${application.registryUrl}\\\"," +
									"\"extension\": \"${application.packageType}\"," +
									"\"type\": \"$createTemplate\"" +
									"}" +
	            	"}\""

	def curlCmd = "https://$ucdUser:$ucdPass@" + readYaml(text: libraryResource('config.yml')).ucd_server + "/cli/applicationProcessRequest/request -d $jsonData"

	println "Initializating the UCD setup..."
	result = "${env.TERMINAL}" (script: "${env.SUPPRESS_TERMINAL_OUTPUT}curl -sk -H \"Connection: close\" -X PUT $curlCmd", returnStdout: true).trim()

	if ( result.contains ("requestId")) {
		requestId = readJSON text: result
		return requestId.requestId
	}
	else {
		return result
	}
}

/*
*	Method			: createComponentVersion
*
* 	Purpose			: Creates a new version for a given UCD component.
*
* 	Pre-condition	: None
*
* 	Post-condition	: None
*
*  	Parameters		: Component Name, Component version, username, password
*
*  	Returns			: Component information json or error string
*/
def createComponentVersion(componentName,componentVersion, ucdUser,ucdPass) {

   def result
   def curlCmd = "https://$ucdUser:$ucdPass@" + readYaml(text: libraryResource('config.yml')).ucd_server + "/cli/version/createVersion --data-urlencode \"component=$componentName\" --data \"name=$componentVersion\""

   println "Creating component version ${componentVersion} for ${componentName}..."
   result = "${env.TERMINAL}" (script: "${env.SUPPRESS_TERMINAL_OUTPUT}curl -sk -H \"Connection: close\" -G -XPOST $curlCmd", returnStdout: true).trim()

   //Check for errors and return accordingly
   if (isValidJson(result))
	   return readJSON(text: result).name

   return result
}

/*
*	Method			: setVersionProperty
*
* 	Purpose			: Sets a version property for a given UCD component.
*
* 	Pre-condition	: None
*
* 	Post-condition	: None
*
*  	Parameters		: Component Name, Component version, Property Name, Property Value, UCD user name, UCD password
*
*  	Returns			: Component information json or error string
*/
def setVersionProperty(componentName,componentVersion,propName,propValue='',ucdUser,ucdPass) {

    def result

	def curlCmd = "https://$ucdUser:$ucdPass@" + readYaml(text: libraryResource('config.yml')).ucd_server +
	            "/cli/version/versionProperties --data-urlencode \"component=$componentName\"" +
	            " --data \"version=$componentVersion&name=$propName\"" + " --data-urlencode \"value=$propValue\""


	println "Setting property $propName=$propValue for ${componentName}-${componentVersion} ..."
	result = "${env.TERMINAL}" (script: "${env.SUPPRESS_TERMINAL_OUTPUT}curl -sk -X PUT -G $curlCmd", returnStdout: true).trim()

	return result
}

/*
*	Method			: createSnapshot
*
* 	Purpose			: Creates a UCD Snapshot
*
* 	Pre-condition	: None
*
* 	Post-condition	: None
*
*  	Parameters		: Application Name, version, components list, UCD user name, UCD password
*
*  	Returns			: Snapshot ID or error
*/
def createSnapshot(appName, version, compVersion, componentList, ucdUser, ucdPass) {

	def componentListCurlCmd = []
	def snapshotId
	def result
	def repoName = appName.split(" ")[-1]

	componentListCurlCmd [0] = "{ \\\"$appName\\\": \"$compVersion\" }"
	for (i=0; i < componentList.size(); i++) {
		componentListCurlCmd [i+1] = "{ \\\"${componentList[i]}\\\": \"latest\" }"
	}

	def jsonData = "\"{ \"application\": \\\"$appName\\\"," +
							"\"description\": \\\"${repoName} ${version} Snapshot\\\"," +
							"\"name\": \\\"${repoName}-${version}\\\"," +
							"\"versions\": [ ${componentListCurlCmd.join(",")} ] }\""

	def curlCmd = "curl -sk -X PUT " +
					"https://$ucdUser:$ucdPass@" +
					readYaml(text: libraryResource('config.yml')).ucd_server +
					"/cli/snapshot/createSnapshot -d $jsonData"

	println "Creating the Snapshot for the UCD artifact..."
	result = "${env.TERMINAL}" (script: "${env.SUPPRESS_TERMINAL_OUTPUT}${curlCmd}", returnStdout: true).trim()

	if (isValidJson(result)) {
		return readJSON (text: result).id
	}

	return result
}

/*
*	Method			: lockSnapshotVersions
*
* 	Purpose			: Creates a UCD Snapshot
*
* 	Pre-condition	: None
*
* 	Post-condition	: None
*
*  	Parameters		: Application Name, version, components list, UCD user name, UCD password
*
*  	Returns			: Snapshot ID or error
*/
def lockSnapshotVersions (snapshotId,ucdUser,ucdPass) {

	def result
	def curlCmd = "curl -sk -X PUT " +
					"https://$ucdUser:$ucdPass@" +
					readYaml(text: libraryResource('config.yml')).ucd_server +
					"/cli/snapshot/lockSnapshotVersions?{\"snapshot=${snapshotId}\"}"

	println "Locking the snapshot versions"
	result = "${env.TERMINAL}" (script: "${env.SUPPRESS_TERMINAL_OUTPUT}${curlCmd}", returnStdout: true).trim()

	return result
}

/*
*	Method			: triggerSnapshotDeploy
*
* 	Purpose			: Triggers a deployment from a UCD snapshot
*
* 	Pre-condition	: None
*
* 	Post-condition	: None
*
*  	Parameters		: Snapshot ID Name, Process name, Environment, UCD user name, UCD password
*
*  	Returns			: Request ID or error
*/
def triggerSnapshotDeploy(snapShotId, appName, processName,envName,ucdUser, ucdPass) {

	def requestId, result

	def jsonData = "\"{ \"application\": \\\"${appName}\\\",\"snapshot\": \\\"$snapShotId\\\",\"applicationProcess\": \\\"$processName\\\",\"environment\": \"$envName\" }\""
	def curlCmd = "https://$ucdUser:$ucdPass@" + readYaml(text: libraryResource('config.yml')).ucd_server + "/cli/applicationProcessRequest/request -d $jsonData"

	println "Deploying the UCD artifact..."
	result = "${env.TERMINAL}" (script: "${env.SUPPRESS_TERMINAL_OUTPUT}curl -sk -H \"Connection: close\" -X PUT $curlCmd", returnStdout: true).trim()

	if ( result.contains ("requestId")) {
		requestId = readJSON text: result
		return requestId.requestId
	}
	else {
		return result
	}
}

/*
 * 	Method			: extractComponentsList
 *
 * 	Purpose			: Extracts the list of component names from the JSONArray returned by UCD in the above function.
 * 					  Uses Groovy apis that are non serializable hence needs to be annotated with @NonCPS
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: JSONArray returned from UCD REST call componentsInApplication made in the above function
 *
 *  Returns			: List of components or null
 */
/**
 * Function to retrieve the list of component names from the JSONArray returned by UCD.
 * Uses Groovy apis that are non serializable hence needs to be annotated with @NonCPS
 */
@NonCPS
def extractComponentsList (result) {
	def componentsList = []

	new JsonSlurper().parseText(result).each {
		node ->
		componentsList.add(node.name)
	}

	return componentsList
}

/*
 * 	Method			: isValidJson
 *
 * 	Purpose			: Checks if the given input string is a valid json format
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: Input string
 *
 *  Returns			: True or False
 */
def isValidJson(str) {

	try {
		new JsonSlurper().parseText(str)
	}

	catch (Exception ex) {
		return false
	}

	return true
}

return this
