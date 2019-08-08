#!usr/bin/env groovy

/*****************************************************************************************************************************
 *
 * © RBC CONFIDENTIAL
 *___________________
 *
 * This file provides all utility functions related to git commands and GitHub REST API's used by piepline scripts.
 *
 * Authors: Harshavardhana Badakere, Thomas Sheridan
 *
 * Pre-Requisite: Requires pipeline environment variables to be set by calling commonUtils.initializePipelineEnv()
 * 				  before using any of the functions in this class as these functions refer to env variables
 *
 * Contact: Digital-DevOps <digitaldevops@rbc.com>
 *
 **/

package utils

import groovy.util.*
import java.util.regex.*
import groovy.json.*

/*
 * 	Method			: extractGitParams
 *
 * 	Purpose			: Extracts git information like repo name org name, commit hash etc.
 *
 * 	Pre-condition	: None
 *
 * 	Post-condition	: All git parameters are set as Jenkins Environment variables. These will be used in subsequent pipeline functions
 *
 *  Parameters		: None
 *
 *  Returns			: None
 */
def extractGitParams() {
	
	//Get the GIT repo URL
	env.GIT_URL = "${env.TERMINAL}"(script: "${env.SUPPRESS_TERMINAL_OUTPUT}git config --get remote.origin.url",
	returnStdout: true).trim()
	
	//Get the GIT commit ID for current branch HEAD
	env.GIT_SHA = "${env.TERMINAL}"(script: "${env.SUPPRESS_TERMINAL_OUTPUT}git rev-parse HEAD",
	returnStdout: true).trim()
	
	//Assertions to ensure the parameters are successfully extracted above
	assert env.GIT_URL != null:'GIT URL cannot be determined'
	assert env.GIT_SHA != null:'GIT Commit Hash cannot be determined'
	
	//Parse and extract the Org name & Repo name from the GIT Url
	def tokenArr = env.GIT_URL.tokenize('/')
	env.ORG_NAME = tokenArr[tokenArr.size() - 2].toUpperCase()
	
	//Extracting the app code from the orgname in case org name is not same as app code. 
	//Uses “-“ or “_“ character as a delimiter to extract the app code which is typically the first part of the org name
	def Matcher myMatcher = Pattern.compile(/[-_]/).matcher(env.ORG_NAME)
	
	if (myMatcher.find())
		env.APP_CODE = env.ORG_NAME.substring(0,myMatcher.start())
	else
		env.APP_CODE = env.ORG_NAME
	
	env.REPO_NAME = tokenArr[tokenArr.size() - 1]
	env.REPO_NAME = env.REPO_NAME.substring(0, env.REPO_NAME.indexOf('.git'))
	
	env.CREDENTIAL_ID_GH = scm.getUserRemoteConfigs().get(0).getCredentialsId()

}

/*
 * 	Method			: postToGitHub
 *
 * 	Purpose			: Posts build statuses to GitHub for the current build's commit ID.
 *
 * 	Pre-condition	: Requires env variables instantiated by extractGitParams method
 *
 * 	Post-condition	: None
 *
 *  Parameters		: Commit ID, GitHub Status Name, GitHub Status Context, Description, Target URL (Optional)
 *
 *  Returns			: true or false
 */
def postToGitHub(state, context, description, targetUrl) {

	def payload = [ 'state': state, 'context': context, 'description' : description]

	if (targetUrl)
		payload['target_url'] = targetUrl

	writeFile file: 'githubPayload.json', text: JsonOutput.toJson(payload)
	def credentialsID = scm.getUserRemoteConfigs().get(0).getCredentialsId()
	
	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: credentialsID, passwordVariable: 'ghAuthToken', usernameVariable: 'ghAuthUser']]){
		//Use GitHub APIs to post the status for given Commit ID
		"${env.TERMINAL}"(script: "${env.SUPPRESS_TERMINAL_OUTPUT}curl -k -H \"Authorization: token $ghAuthToken \" --request POST --data @githubPayload.json  https://" + readYaml(text: libraryResource('config.yml')).github_server + "/api/v3/repos/${env.ORG_NAME}/${env.REPO_NAME}/statuses/${env.GIT_SHA}",
					   returnStdout: true)
		}
}

/*
 * 	Method			: getLastCommitMessage
 *
 * 	Purpose			: Returns the commit message for the most recent commit ID in the branch
 *
 * 	Pre-condition	: Requires env variables instantiated by extractGitParams method.
 *
 * 	Post-condition	: None
 *
 *  Parameters		: None
 *
 *  Returns			: Commit message as String
 */
def getLastCommitMessage() {

	def commitMsg = "${env.TERMINAL}"(script: "${env.SUPPRESS_TERMINAL_OUTPUT}git log -1 --pretty=${env.HTTP_ESC_CHARACTER}%B",
	returnStdout: true).trim()
	
	return commitMsg
}

/*
 * 	Method			: checkPathForChanges
 *
 * 	Purpose			: Given a subfolder, check the history for changes
 *
 * 	Pre-condition	: Requires env variables instantiated by extractGitParams method.
 *
 * 	Post-condition	: None
 *
 *  Parameters		: path
 *
 *  Returns			: true or false
 */
def checkPathForChanges(path) {
    echo "path ${path}"
    def gitScript = "${env.TERMINAL}"(script: "${env.SUPPRESS_TERMINAL_OUTPUT}git diff HEAD^ -- ${path}", returnStdout: true);
    echo "gitScript ${gitScript}"
    return gitScript.length() > 0;
}

return this