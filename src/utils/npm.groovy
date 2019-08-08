#!usr/bin/env groovy

/*****************************************************************************************************************************
 *
 * © RBC CONFIDENTIAL
 *___________________
 *
 * This file provides all utility functions related to npm builds to be used by piepline scripts.
 *
 * Authors: Harshavardhana Badakere
 *
 * Contact: Digital-DevOps <digitaldevops@rbc.com>
 *
 **/

package utils

import groovy.util.*

/*
 * 	Method			: getNPMParams
 *
 * 	Purpose			: Gets the NPM parameters like artifact Id and group Id for projects by
 * 					  reading the the package.json @ root directory.
 *
 * 	Pre-condition	: Assumes the function is run from the directory where the package.json file exists
 *
 * 	Post-condition	: None
 *
 *  Parameters		: None
 *
 *  Returns			: List of extracted npm params as a key/value pair or null
 */
def getNPMParams() {

	def jsonParams = [:]
	def jsonObj = readJSON file: 'package.json'

	jsonParams['groupId'] = (jsonObj.has("groupId")) ? jsonObj.get("groupId"): jsonObj.get("group-id")
	jsonParams['version'] = jsonObj.get("version")
	jsonParams['artifactId'] = (jsonObj.has("artifact-id")) ? jsonObj.get("artifact-id") : jsonObj.get("name")
	jsonParams['deployFoldername'] = ((jsonObj.has("app-code")) ? jsonObj.get("app-code") : jsonObj.get("appCode")).toLowerCase() + "-snapshots"
	
	assert jsonParams['groupId'] != null:"groupId cannot be null"
	assert jsonParams['version']!= null:"version cannot be null"
	assert jsonParams['artifactId']!= null:"artifactId cannot be null"
	assert jsonParams['deployFoldername']!= null:"app code cannot be null"
	
	return jsonParams
}

/*
 * 	Method			: extractAppName
 *
 * 	Purpose			: Extracts the application name by reading the package.json.
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: json file path
 *
 *  Returns			: Application name as a String
 */
def extractAppName(jsonPath) {
	
	appName = readJSON(file: jsonPath).name
	assert appName != null: "App Name cannot be null"
	return appName
	
}

/*
 * 	Method			: extractAndValidateAppVersion
 *
 * 	Purpose			: Extracts the application version by reading the package.json
 * 					  and optionally validates it against passed pattern 
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: JSON file path, Pattern RegEx (optional)
 *
 *  Returns			: Application version as a String
 */
def extractAndValidateAppVersion(jsonPath, pattern=null) {
	
	def appVersion = readJSON(file: jsonPath).version
	
	assert appVersion != null : "App version cannot be null"
	
	if (pattern && !(appVersion.matches(/${pattern}/))) {
		println "App version does not match the regex pattern: $pattern"
		return null
	}
	
	return appVersion
}

/*
 * 	Method			: calculatePrereleaseIncrement
 *
 * 	Purpose			: Parses all the package semvers to determine the latest prerelese increment version 
 * 					  and returns an increment number by adding 1 it
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: None
 *
 *  Parameters		: List of package versions
 *
 *  Returns			: Increment as a number
 */
def calculatePrereleaseIncrement (pckgListArr) {
	
	def incArr = []
	
	for (pckg in pckgListArr) {
	    pckg = pckg.replaceAll("\"","")
	    incArr.add(pckg.substring(pckg.lastIndexOf('.')+1).toInteger())
	}
	
	return incArr.max() + 1
}

return this